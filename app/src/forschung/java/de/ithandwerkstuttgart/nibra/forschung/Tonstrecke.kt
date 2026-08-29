package de.ithandwerkstuttgart.nibra.forschung

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.FileOutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Die eigene Aufnahmestrecke: Mikrofon → Ringpuffer → Rohr → Erkenner.
 *
 * ```
 * AudioRecord ──▶ Warteschlange ──▶ Rohrschreiber ──▶ SpeechRecognizer
 *   (Leser)         (begrenzt)        (eigener Faden)
 * ```
 *
 * **Die beiden Fäden sind bewusst getrennt.** Läse derselbe Faden vom
 * Mikrofon und schriebe ins Rohr, hielte jedes Stocken des
 * Erkennungsdienstes die Aufnahme an -- und dann fehlen Abtastwerte, die
 * niemand je wiederbekommt. Der Mikrofonleser darf nie warten.
 *
 * Läuft die Warteschlange trotzdem voll, wird das **gemeldet**, nicht
 * verschwiegen: [verworfeneBloecke] zählt mit, und der Bericht weist es
 * aus. Still verlorene Abtastwerte wären der schlimmste Fehler, den diese
 * Klasse machen könnte -- man merkt ihn erst an unerklärlich schlechter
 * Erkennung.
 *
 * Der Vorlauf ist das zweite, was hier möglich wird: die Strecke sammelt
 * schon vor dem Start der Erkennung, und beim Start geht der gespeicherte
 * Vorlauf **in richtiger Reihenfolge** als erstes ins Rohr. Damit
 * überlebt der Wortanfang, der sonst verloren geht.
 *
 * Nur Forschung. Kein Ton wird gespeichert -- er lebt im Arbeitsspeicher
 * und ist danach weg.
 */
class Tonstrecke(
    private val abtastrate: Int = 16_000,
    /** Wie viel Ton vor dem Start der Erkennung aufgehoben wird. */
    private val vorlaufMillis: Int = 1_500
) {

    /** Eine Stichprobe während des Laufs. */
    data class Probe(
        /** Millisekunden seit dem Start der Aufnahme. */
        val zeitMillis: Long,
        val rahmen: Long,
        val warteschlangeTiefe: Int,
        val verworfeneBloecke: Int
    )

    /** Was die Strecke über ihren eigenen Lauf sagen kann. */
    data class Befund(
        val geleseneRahmen: Long,
        val gesendeteBytes: Long,
        val vorlaufBytes: Long,
        val verworfeneBloecke: Int,
        val groessteWarteschlange: Int,
        val blockierteSchreibversuche: Int,
        val leseFehler: Int,
        val laufzeitMillis: Long,
        /**
         * Abtastwerte und Zeit **im eingeschwungenen Teil** -- gemessen ab
         * einem Punkt weit nach dem Start und bis zum Ende.
         *
         * Die Ränder taugen nicht zum Messen des Takts. Am Anfang liefert
         * `AudioRecord` einen ersten Block, dessen Ton älter ist als der
         * Aufruf, und braucht davor eine Anlaufzeit, in der gar nichts
         * kommt. Beides sind feste Versätze von einigen Dutzend
         * Millisekunden. Über fünf Sekunden gemessen sehen sie aus wie ein
         * Takt, der um ein bis drei Prozent danebenliegt; über eine
         * Viertelstunde verschwinden sie im Rauschen. Wer den Takt wissen
         * will, muss die Ränder weglassen.
         */
        /**
         * Wann der erste Block wirklich ankam -- absolute Uhrzeit.
         *
         * Für die Frage „wie lange dauert es, bis das Mikrofon liefert?".
         * Die Anlaufzeit ist beim Diktat die erste Wartezeit, die der
         * Nutzer spürt, und sie ist an keiner anderen Zahl ablesbar.
         */
        val ersterBlockMillis: Long?,
        val taktRahmen: Long,
        val taktMillis: Long,
        /**
         * Regelmäßige Stichproben über den ganzen Lauf.
         *
         * Ein einzelner Endwert kann eine gleichmäßige kleine Abweichung
         * nicht von einer wachsenden oder springenden unterscheiden -- am
         * Ende steht in beiden Fällen dieselbe Zahl. Erst die Reihe zeigt,
         * ob die Strecke ruhig läuft oder sich langsam verabschiedet.
         */
        val proben: List<Probe>,
        val verlustMillis: Long,
        val spitze: Int,
        val fehler: String?
    ) {
        /**
         * Wahr, wenn kein Ton verloren ging. Die Uhr ist der unabhängige
         * Zeuge: aus den Abtastwerten allein liesse sich ein Verlust nie
         * erkennen, weil die Zeitachse aus ihnen berechnet wäre.
         *
         * **Nur ein positiver Wert ist ein Verlust.** Ein negativer heißt,
         * es kamen mehr Abtastwerte an, als Zeit vergangen ist -- das ist
         * die Ungenauigkeit an den Rändern der Messung, kein fehlender Ton.
         * Der erste Wurf prüfte den Betrag und meldete deshalb bei -70 ms
         * „ES FEHLT TON", obwohl kein einziger Block verworfen wurde.
         */
        val luekenlos: Boolean get() = istLueckenlos(verworfeneBloecke, verlustMillis)
    }

    private val laeuft = AtomicBoolean(false)
    private val erkennungLaeuft = AtomicBoolean(false)
    private val geleseneRahmen = AtomicLong(0)
    private val gesendeteBytes = AtomicLong(0)
    private val vorlaufBytes = AtomicLong(0)
    private val verworfen = AtomicInteger(0)
    private val groessteTiefe = AtomicInteger(0)
    private val blockiert = AtomicInteger(0)
    private val leseFehler = AtomicInteger(0)
    private val spitze = AtomicInteger(0)

    private val proben = mutableListOf<Probe>()
    @Volatile private var naechsteProbe = PROBENABSTAND_MILLIS

    /** Stand am Ende der Einschwingzeit -- Nullpunkt der Taktmessung. */
    @Volatile private var ersterBlock = 0L
    @Volatile private var einschwungRahmen = -1L
    @Volatile private var einschwungUhr = 0L

    /**
     * Begrenzt -- ein unbegrenzter Puffer verdeckt genau das Problem, das
     * wir sehen wollen. Rund vier Sekunden bei 16 kHz.
     */
    private val warteschlange = ArrayBlockingQueue<ByteArray>(64)

    /**
     * Der Vorlauf. Eigene, geprüfte Klasse -- die Reihenfolge des
     * Vorlaufs ist die eine Sache, die niemand am Gerät nachrechnen kann.
     */
    private val vorlauf = Vorlaufpuffer(
        Vorlaufpuffer.bloeckeFuer(vorlaufMillis, BLOCK_BYTES, abtastrate)
    )

    private var leser: Thread? = null
    private var schreiber: Thread? = null
    /**
     * Was tatsächlich ins Rohr geschrieben wurde, in genau dieser
     * Reihenfolge.
     *
     * Nur zum Prüfen: ohne diesen Mitschnitt liesse sich nicht belegen,
     * dass der Vorlauf **vor** dem laufenden Ton liegt und dass nichts
     * doppelt oder vertauscht ankommt. Er wird nach der Auswertung
     * verworfen und niemals gespeichert.
     */
    private val gesendeteFolge = mutableListOf<Byte>()
    private var mitschnittAn = false

    fun schneideMit() { mitschnittAn = true }

    /** Die ersten Bytes der gesendeten Folge -- zur Reihenfolgeprüfung. */
    fun folgenanfang(anzahl: Int): List<Byte> =
        synchronized(gesendeteFolge) { gesendeteFolge.take(anzahl) }

    private var uhrStart = 0L
    private var uhrEnde = 0L
    private var fehler: String? = null

    /** Marken für die Zeitleiste -- ohne Toninhalt. */
    private val marken = mutableListOf<String>()
    private val beginn = SystemClock.elapsedRealtime()

    fun marke(was: String) {
        synchronized(marken) {
            marken += "%6d ms  %s".format(SystemClock.elapsedRealtime() - beginn, was)
        }
    }

    fun zeitleiste(): List<String> = synchronized(marken) { marken.toList() }

    /**
     * Öffnet das Mikrofon und sammelt. Die Erkennung kann später dazukommen;
     * bis dahin füllt sich der Vorlauf.
     */
    fun starte(): Boolean {
        if (laeuft.getAndSet(true)) return false
        val kleinste = AudioRecord.getMinBufferSize(
            abtastrate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val aufnahme = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, abtastrate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(kleinste * 4, BLOCK_BYTES * 8)
            )
        }.getOrNull()
        if (aufnahme == null || aufnahme.state != AudioRecord.STATE_INITIALIZED) {
            fehler = "AudioRecord nicht bereit"
            laeuft.set(false)
            return false
        }

        leser = Thread {
            runCatching {
                aufnahme.startRecording()
                // **Die Uhr beginnt hier, nicht beim ersten gelesenen
                // Block.** Vorher stand sie erst nach der ersten Lesung --
                // deren Abtastwerte wurden aber schon gezählt. Damit fehlte
                // der Zeitachse rund ein Block, und die Drift sah nach
                // +15 000 ppm aus: nicht ein schneller Quarz, sondern eine
                // zu spät gestartete Uhr. Auf fünf Sekunden fällt so ein
                // fester Versatz von 76 ms als anderthalb Prozent auf, auf
                // eine Viertelstunde als achteinhalb Millionstel -- ein
                // kurzer Kontrollfall reagiert darauf also am
                // empfindlichsten.
                uhrStart = SystemClock.elapsedRealtime()
                marke("Aufnahme läuft")
                val block = ByteArray(BLOCK_BYTES)
                while (laeuft.get()) {
                    val gelesen = aufnahme.read(block, 0, block.size)
                    if (gelesen <= 0) {
                        leseFehler.incrementAndGet()
                        if (gelesen < 0) break else continue
                    }
                    if (ersterBlock == 0L) ersterBlock = SystemClock.elapsedRealtime()
                    geleseneRahmen.addAndGet(gelesen.toLong() / 2)
                    val seitStart = SystemClock.elapsedRealtime() - uhrStart
                    if (einschwungRahmen < 0 && seitStart >= EINSCHWINGEN_MILLIS) {
                        einschwungRahmen = geleseneRahmen.get()
                        einschwungUhr = SystemClock.elapsedRealtime()
                    }
                    if (seitStart >= naechsteProbe) {
                        synchronized(proben) {
                            proben += Probe(
                                zeitMillis = seitStart,
                                rahmen = geleseneRahmen.get(),
                                warteschlangeTiefe = warteschlange.size,
                                verworfeneBloecke = verworfen.get()
                            )
                        }
                        naechsteProbe += PROBENABSTAND_MILLIS
                    }
                    merkeSpitze(block, gelesen)
                    val kopie = block.copyOf(gelesen)

                    if (erkennungLaeuft.get()) {
                        // **Nie warten.** Ist die Warteschlange voll, hat der
                        // Erkenner nicht mitgehalten -- dann wird das gemeldet
                        // und weitergelesen, statt das Mikrofon anzuhalten.
                        if (!warteschlange.offer(kopie)) {
                            verworfen.incrementAndGet()
                        }
                        val tiefe = warteschlange.size
                        groessteTiefe.updateAndGet { maxOf(it, tiefe) }
                    } else {
                        synchronized(vorlauf) { vorlauf.lege(kopie) }
                    }
                }
            }.onFailure { fehler = "${it.javaClass.simpleName} ${it.message}" }
            uhrEnde = SystemClock.elapsedRealtime()
            runCatching { aufnahme.stop() }
            runCatching { aufnahme.release() }
            marke("Aufnahme beendet")
        }.also { it.start() }
        return true
    }

    /**
     * Beginnt, in das Rohr zu schreiben -- zuerst den Vorlauf, dann den
     * laufenden Ton.
     *
     * @param mitVorlauf falsch, um den Anfangsverlust sichtbar zu machen.
     */
    fun speiseIn(schreibseite: ParcelFileDescriptor, mitVorlauf: Boolean) {
        if (erkennungLaeuft.getAndSet(true)) return
        val gespeicherterVorlauf = synchronized(vorlauf) {
            val liste = if (mitVorlauf) vorlauf.nimmHeraus() else emptyList()
            vorlauf.leere()
            liste
        }
        marke(
            "Einspeisung beginnt, Vorlauf " +
                if (mitVorlauf) "${gespeicherterVorlauf.size} Blöcke" else "abgeschaltet"
        )
        schreiber = Thread {
            runCatching {
                FileOutputStream(schreibseite.fileDescriptor).use { strom ->
                    // Der Vorlauf zuerst, in der Reihenfolge, in der er
                    // aufgenommen wurde -- sonst wäre er kein Vorlauf,
                    // sondern Durcheinander.
                    gespeicherterVorlauf.forEach {
                        strom.write(it)
                        merkeFolge(it)
                        vorlaufBytes.addAndGet(it.size.toLong())
                        gesendeteBytes.addAndGet(it.size.toLong())
                    }
                    strom.flush()
                    while (erkennungLaeuft.get() || warteschlange.isNotEmpty()) {
                        val block = warteschlange.poll(200, TimeUnit.MILLISECONDS)
                        if (block == null) {
                            if (!laeuft.get() && warteschlange.isEmpty()) break
                            blockiert.incrementAndGet()
                            continue
                        }
                        strom.write(block)
                        strom.flush()
                        merkeFolge(block)
                        gesendeteBytes.addAndGet(block.size.toLong())
                    }
                }
            }.onFailure { fehler = fehler ?: "Schreiben: ${it.javaClass.simpleName}" }
            runCatching { schreibseite.close() }
            marke("Rohr geschlossen, ${gesendeteBytes.get()} Bytes")
        }.also { it.start() }
    }

    /** Beendet die Einspeisung; die Aufnahme läuft weiter. */
    fun beendeEinspeisung() {
        erkennungLaeuft.set(false)
        schreiber?.join(5_000)
    }

    fun halteAn(): Befund {
        laeuft.set(false)
        erkennungLaeuft.set(false)
        leser?.join(5_000)
        schreiber?.join(5_000)
        val laufzeit = if (uhrStart == 0L) 0 else uhrEnde - uhrStart
        val nachAbtastwerten = geleseneRahmen.get() * 1000 / abtastrate
        return Befund(
            geleseneRahmen = geleseneRahmen.get(),
            gesendeteBytes = gesendeteBytes.get(),
            vorlaufBytes = vorlaufBytes.get(),
            verworfeneBloecke = verworfen.get(),
            groessteWarteschlange = groessteTiefe.get(),
            blockierteSchreibversuche = blockiert.get(),
            leseFehler = leseFehler.get(),
            laufzeitMillis = laufzeit,
            proben = synchronized(proben) { proben.toList() },
            ersterBlockMillis = ersterBlock.takeIf { it > 0 },
            taktRahmen = if (einschwungRahmen < 0) 0
            else geleseneRahmen.get() - einschwungRahmen,
            taktMillis = if (einschwungRahmen < 0) 0 else uhrEnde - einschwungUhr,
            // **Auf dem eingeschwungenen Teil gerechnet, nicht auf dem
            // ganzen Lauf.** Sonst zählt die Anlaufzeit des Mikrofons als
            // fehlender Ton: das A15 meldete so 149 ms Verlust bei null
            // verworfenen Blöcken. Verloren ist Ton nur da, wo die Strecke
            // ihn wegwerfen musste -- und das steht in verworfeneBloecke.
            verlustMillis = if (einschwungRahmen < 0) 0
            else (uhrEnde - einschwungUhr) -
                (geleseneRahmen.get() - einschwungRahmen) * 1000 / abtastrate,
            spitze = spitze.get(),
            fehler = fehler
        )
    }

    private fun merkeFolge(block: ByteArray) {
        if (!mitschnittAn) return
        synchronized(gesendeteFolge) {
            if (gesendeteFolge.size < MITSCHNITT_HOECHSTENS) {
                gesendeteFolge += block.take(MITSCHNITT_HOECHSTENS - gesendeteFolge.size)
            }
        }
    }

    private fun merkeSpitze(block: ByteArray, anzahl: Int) {
        var groesster = 0
        var i = 0
        while (i + 1 < anzahl) {
            val wert = ((block[i + 1].toInt() shl 8) or (block[i].toInt() and 0xFF)).toShort().toInt()
            val betrag = abs(wert)
            if (betrag > groesster) groesster = betrag
            i += 2
        }
        spitze.updateAndGet { maxOf(it, groesster) }
    }

    companion object {
        /** Rund 64 ms bei 16 kHz -- nah an dem, was das Gerät liefert. */
        const val BLOCK_BYTES = 2048

        /**
         * So viel Rückstand gegen die Uhr gilt noch als lückenlos. Ein
         * Block ist rund 64 ms; alles darunter ist Randungenauigkeit.
         */
        const val TOLERANZ_MILLIS = 70L

        /**
         * So lange bleibt der Anfang bei der Taktmessung außen vor. Zwei
         * Sekunden sind reichlich für die Anlaufzeit eines Mikrofons und
         * kosten selbst beim kurzen Kontrollfall nur die Hälfte der
         * Messstrecke.
         */
        const val EINSCHWINGEN_MILLIS = 2_000L

        /**
         * Abstand der Stichproben. Zehn Sekunden ergeben über eine
         * Viertelstunde neunzig Punkte -- genug, um eine wandernde
         * Abweichung von einer ruhigen zu unterscheiden, und wenig genug,
         * dass die Liste nicht selbst zum Speicherproblem wird.
         */
        const val PROBENABSTAND_MILLIS = 10_000L

        /** Genug, um Vorlauf und Übergang zu sehen, ohne Speicher zu fluten. */
        const val MITSCHNITT_HOECHSTENS = 200_000

        /**
         * Ob der Tonstrom lückenlos war.
         *
         * **Das Vorzeichen trägt die Bedeutung.** Positiv heißt: die Uhr
         * ist weiter gelaufen als Abtastwerte ankamen -- es fehlt Ton.
         * Negativ heißt: es kamen mehr Abtastwerte an, als Zeit verging --
         * Randungenauigkeit der Messung, kein Verlust.
         *
         * Die erste Fassung prüfte den **Betrag** und meldete deshalb bei
         * -70 ms „ES FEHLT TON", obwohl kein einziger Block verworfen
         * wurde. Als reine Funktion herausgezogen, damit ein Golden Test
         * das festhalten kann.
         */
        fun istLueckenlos(verworfeneBloecke: Int, verlustMillis: Long): Boolean =
            verworfeneBloecke == 0 && verlustMillis <= TOLERANZ_MILLIS
    }
}
