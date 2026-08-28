package de.ithandwerkstuttgart.nibra.forschung

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Gate 2: **Wie lange trägt eine Sitzung?**
 *
 * Nibra soll Diktat können, nicht Kurzbefehle. Ein Erkenner, der nach 60
 * Sekunden stillschweigend aufhört, wäre für die App unbrauchbar -- und
 * genau das ließe sich mit kurzen Versuchen nie feststellen. Deshalb hier
 * Läufe über eine, fünf und fünfzehn Minuten.
 *
 * **Der Ton kommt aus einer Aufnahme, nicht aus dem Mikrofon.** Das ist
 * Absicht: gefragt ist die Grenze der *Sitzung*, nicht die des Mikrofons.
 * Mit eingespeistem Ton ist der Lauf wiederholbar, läuft ohne Belkis und
 * ist über alle drei Dauern identisch. Der Mikrofonweg selbst ist in
 * [Livestreckenversuch] und [Vorlaufversuch] getrennt belegt.
 *
 * Die Aufnahme wird in Echtzeit im Kreis eingespeist. Echtzeit ist
 * wesentlich: schneller einspeisen würde die Sitzung nicht altern lassen,
 * sondern nur den Puffer füllen -- und die Grenze, die wir suchen, ist eine
 * Grenze in der Zeit.
 *
 * Was der Lauf beantwortet:
 * - Kommen bis zum Schluss Segmente, oder versiegen sie vorher?
 * - Endet die Sitzung von sich aus, und wenn ja, nach wie langer Zeit?
 * - Wird der Fehler gemeldet oder wird es einfach still?
 */
class Dauerversuch(
    private val zusammenhang: Context,
    private val aufStand: (String) -> Unit
) {

    data class Lauf(
        val dauerMillis: Long,
        val eingespeisteBytes: Long,
        /** Zeitpunkte der Segmente, ab Start der Erkennung. */
        val segmentZeiten: List<Long>,
        val segmente: List<String>,
        /**
         * Segmentmeldungen **ohne** Text.
         *
         * Der erste Lauf hat sie nicht gezählt: leere Texte fielen dem
         * Filter zum Opfer, bevor irgendwer sie sah. Damit wäre eine
         * Erkennung, die pflichtschuldig Segmentgrenzen meldet und nichts
         * versteht, als „219 Segmente, alles gut" durchgegangen.
         */
        val leereSegmente: Int,
        val fehlerCode: Int?,
        val fehlerZeit: Long?,
        val sitzungsEnde: Long?,
        val zwischenstaende: Int,
        val letzterZwischenstand: Long?,
        val rechenzeitMillis: Long?,
        val zeigerStart: Int?,
        val zeigerEnde: Int?,
        val speicherStartKb: Long,
        val speicherEndeKb: Long,
        val faedenStart: Int,
        val faedenEnde: Int,
        val schreibFehler: String?
    ) {
        /** Wann zuletzt **irgendein** Lebenszeichen kam. */
        val letztesLebenszeichen: Long?
            get() = listOfNotNull(segmentZeiten.lastOrNull(), letzterZwischenstand).maxOrNull()

        /**
         * Hat die Erkennung bis zum Ende durchgehalten?
         *
         * Nicht „gab es Segmente" -- das wäre auch bei einem Abbruch nach
         * zehn Sekunden wahr. Gefragt ist, ob **am Ende** noch etwas kam.
         * Zwei Sekunden Nachlauf sind zugestanden, weil der Erkenner das
         * letzte Stück erst nach einer Sprechpause abschließt.
         */
        val durchgehalten: Boolean
            get() = letztesLebenszeichen != null &&
                letztesLebenszeichen!! >= dauerMillis - NACHLAUF_MILLIS

        /** Die längste Stille zwischen zwei Lebenszeichen. */
        val groessteLuecke: Long
            get() = (listOf(0L) + segmentZeiten)
                .zipWithNext { a, b -> b - a }
                .maxOrNull() ?: 0L
    }

    private val hauptfaden = Handler(Looper.getMainLooper())

    fun fuehreDurch(pcm: ByteArray, dauern: List<Long>): String = buildString {
        appendLine("DAUERLAUF -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        val quelleMillis = pcm.size / 2 * 1000L / ABTASTRATE
        appendLine("Eingespeist wird eine Aufnahme von $quelleMillis ms, in Echtzeit")
        appendLine("im Kreis wiederholt. Gesucht ist die Grenze der Sitzung, nicht die")
        appendLine("des Mikrofons -- deshalb bekannter Ton statt Vorlesen.")
        appendLine()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appendLine("EXTRA_AUDIO_SOURCE gibt es erst ab Android 13. NICHT PRÜFBAR.")
            return@buildString
        }
        if (pcm.size < 2 * ABTASTRATE) {
            appendLine("Die Aufnahme ist kürzer als eine Sekunde. NICHT AUSSAGEKRÄFTIG.")
            return@buildString
        }

        val laeufe = dauern.map { dauer ->
            aufStand("Dauerlauf ${dauer / 1000} s läuft ...")
            lauf(pcm, dauer).also { schreibe(it) }
        }

        appendLine("URTEIL")
        val gescheitert = laeufe.filter { !it.durchgehalten }
        when {
            gescheitert.isEmpty() -> {
                appendLine("  **Alle Dauern durchgehalten** -- bis ${laeufe.maxOf { it.dauerMillis } / 1000} s")
                appendLine("  kamen Segmente bis zum Schluss. Keine Sitzungsgrenze gefunden.")
                appendLine("  Das heißt **nicht**, dass es keine gibt: bewiesen ist nur, dass")
                appendLine("  sie über der längsten geprüften Dauer liegt.")
            }
            gescheitert.size == laeufe.size ->
                appendLine("  **Keine Dauer durchgehalten.** Erst den Aufbau prüfen, " +
                    "bevor eine Grenze behauptet wird.")
            else -> {
                val kuerzeste = gescheitert.minBy { it.dauerMillis }
                appendLine("  **Sitzungsgrenze gefunden.** Bis " +
                    "${laeufe.filter { it.durchgehalten }.maxOf { it.dauerMillis } / 1000} s hält die")
                appendLine("  Sitzung, bei ${kuerzeste.dauerMillis / 1000} s nicht mehr: das letzte")
                appendLine("  Lebenszeichen kam nach ${kuerzeste.letztesLebenszeichen ?: 0} ms von " +
                    "${kuerzeste.dauerMillis} ms.")
                appendLine("  Folge für die App: die Sitzung muss vor dieser Grenze erneuert")
                appendLine("  werden, ohne dass der Nutzer eine Lücke hört oder Text verliert.")
            }
        }
        appendLine()
        val stiller = laeufe.filter { !it.durchgehalten && it.fehlerCode == null && it.sitzungsEnde == null }
        if (stiller.isNotEmpty()) {
            appendLine("  ACHTUNG: bei ${stiller.joinToString { "${it.dauerMillis / 1000} s" }} " +
                "wurde es **still ohne Fehler**.")
            appendLine("  Ein Ende ohne Meldung ist der teuerste Fall: die App merkt nichts")
            appendLine("  und der Nutzer redet ins Leere. Ein Wächter auf ausbleibende")
            appendLine("  Segmente ist damit Pflicht, nicht Kür.")
        }
    }

    private fun StringBuilder.schreibe(l: Lauf) {
        appendLine("LAUF ${l.dauerMillis / 1000} s")
        appendLine("  eingespeist            ${l.eingespeisteBytes} Bytes = " +
            "${l.eingespeisteBytes / 2 * 1000 / ABTASTRATE} ms")
        appendLine("  Segmente               ${l.segmente.size}")
        appendLine("  davon leer             ${l.leereSegmente}")
        appendLine("  Zwischenstände         ${l.zwischenstaende}")
        appendLine("  erstes Segment         ${ms(l.segmentZeiten.firstOrNull())}")
        appendLine("  letztes Segment        ${ms(l.segmentZeiten.lastOrNull())}")
        appendLine("  letzter Zwischenstand  ${ms(l.letzterZwischenstand)}")
        appendLine("  letztes Lebenszeichen  ${ms(l.letztesLebenszeichen)} von ${l.dauerMillis} ms")
        appendLine("  größte Lücke           ${l.groessteLuecke} ms")
        appendLine("  Fehler                 " +
            (l.fehlerCode?.let { "$it nach ${ms(l.fehlerZeit)}" } ?: "keiner"))
        appendLine("  Sitzungsende gemeldet  ${l.sitzungsEnde?.let { ms(it) } ?: "nein"}")
        appendLine("  Schreibfehler am Rohr  ${l.schreibFehler ?: "keiner"}")
        appendLine("  Rechenzeit             " + (l.rechenzeitMillis?.let { r ->
            val anteil = if (l.dauerMillis == 0L) 0 else r * 1000 / l.dauerMillis
            "$r ms = ${anteil / 10},${anteil % 10} % eines Kerns"
        } ?: "nicht gemessen"))
        appendLine("  Speicher               ${l.speicherStartKb} KB -> ${l.speicherEndeKb} KB")
        appendLine("  Dateizeiger            ${l.zeigerStart} -> ${l.zeigerEnde}")
        appendLine("  Einspeisungsdrift      " + (l.eingespeisteBytes / 2 * 1000 /
            ABTASTRATE - l.dauerMillis) + " ms gegen die Uhr")
        appendLine("  Fäden                  ${l.faedenStart} -> ${l.faedenEnde}")
        appendLine("  durchgehalten          ${if (l.durchgehalten) "ja" else "NEIN"}")
        if (l.segmente.isNotEmpty()) {
            appendLine("  erstes Segment: ${l.segmente.first().take(60)}")
            appendLine("  letztes Segment: ${l.segmente.last().take(60)}")
        }
        appendLine()
    }

    private fun lauf(pcm: ByteArray, dauerMillis: Long): Lauf {
        val segmentZeiten = mutableListOf<Long>()
        val segmente = mutableListOf<String>()
        var fehlerCode: Int? = null
        var fehlerZeit: Long? = null
        var sitzungsEnde: Long? = null
        var zwischenstaende = 0
        var leereSegmente = 0
        var letzterZwischenstand: Long? = null
        var schreibFehler: String? = null
        var eingespeist = 0L

        val vorher = Prozessbefund.nimmAuf()
        val fertig = CountDownLatch(1)
        var erkenner: SpeechRecognizer? = null
        val nullpunkt = SystemClock.elapsedRealtime()
        fun jetzt() = SystemClock.elapsedRealtime() - nullpunkt

        val (lesen, schreiben) = ParcelFileDescriptor.createPipe()

        hauptfaden.post {
            val neuer = runCatching {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(zusammenhang)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(zusammenhang)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(zusammenhang)
                }
            }.getOrNull()
            if (neuer == null) {
                fertig.countDown()
                return@post
            }
            erkenner = neuer
            neuer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rms: Float) = Unit
                override fun onBufferReceived(b: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(code: Int) {
                    // Nur den ersten Fehler festhalten: ein Erkenner, der
                    // nach dem Abbruch weiter meldet, soll den Zeitpunkt des
                    // Abbruchs nicht überschreiben.
                    if (fehlerCode == null) {
                        fehlerCode = code
                        fehlerZeit = jetzt()
                    }
                    fertig.countDown()
                }
                override fun onResults(werte: Bundle?) {
                    lies(werte).firstOrNull()?.let {
                        segmente += it
                        segmentZeiten += jetzt()
                    }
                    fertig.countDown()
                }
                override fun onPartialResults(werte: Bundle?) {
                    if (lies(werte).isNotEmpty()) {
                        zwischenstaende += 1
                        letzterZwischenstand = jetzt()
                    }
                }
                override fun onSegmentResults(werte: Bundle) {
                    val text = lies(werte).firstOrNull()
                    if (text == null) {
                        leereSegmente += 1
                    } else {
                        segmente += text
                        segmentZeiten += jetzt()
                    }
                }
                override fun onEndOfSegmentedSession() {
                    sitzungsEnde = jetzt()
                    fertig.countDown()
                }
                override fun onEvent(art: Int, p: Bundle?) = Unit
            })
            runCatching { neuer.startListening(absicht(lesen)) }
                .onFailure { fertig.countDown() }
        }

        // Der Schreiber speist in Echtzeit ein und läuft im Kreis, bis die
        // Zieldauer erreicht ist. Er blockiert nie länger als ein Block:
        // stünde das Rohr voll, würde der Lauf sonst still langsamer und die
        // gemessene Dauer wäre nicht die gewollte.
        val schreiber = thread(name = "dauerversuch-einspeisung") {
            runCatching {
                FileOutputStream(schreiben.fileDescriptor).use { strom ->
                    var stelle = 0
                    val bis = SystemClock.elapsedRealtime() + dauerMillis
                    while (SystemClock.elapsedRealtime() < bis) {
                        val menge = minOf(BLOCK_BYTES, pcm.size - stelle)
                        strom.write(pcm, stelle, menge)
                        eingespeist += menge
                        stelle = (stelle + menge) % pcm.size
                        Thread.sleep(menge.toLong() * 1000 / (ABTASTRATE * 2))
                    }
                }
            }.onFailure { schreibFehler = "${it.javaClass.simpleName} ${it.message.orEmpty()}" }
            runCatching { schreiben.close() }
        }

        schreiber.join(dauerMillis + 30_000)
        // Nach dem Ende der Einspeisung sieht der Erkenner das Rohrende und
        // schließt ab. Großzügig warten -- bei langen Läufen dauert das.
        fertig.await(30, TimeUnit.SECONDS)
        hauptfaden.post { runCatching { erkenner?.destroy() } }
        runCatching { lesen.close() }
        Thread.sleep(2_000)

        val nachher = Prozessbefund.nimmAuf()
        return Lauf(
            dauerMillis, eingespeist, segmentZeiten.toList(), segmente.toList(),
            leereSegmente, fehlerCode, fehlerZeit, sitzungsEnde, zwischenstaende,
            letzterZwischenstand,
            rechenzeitMillis = if (vorher.rechenzeitMillis == null ||
                nachher.rechenzeitMillis == null) null
            else nachher.rechenzeitMillis - vorher.rechenzeitMillis,
            zeigerStart = vorher.offeneZeiger,
            zeigerEnde = nachher.offeneZeiger,
            speicherStartKb = vorher.speicherKb,
            speicherEndeKb = nachher.speicherKb,
            faedenStart = vorher.faeden,
            faedenEnde = nachher.faeden,
            schreibFehler = schreibFehler
        )
    }

    private fun absicht(lesen: ParcelFileDescriptor) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, lesen)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, ABTASTRATE)
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        }

    private fun lies(werte: Bundle?): List<String> =
        werte?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty().filter { it.isNotBlank() }

    private fun ms(wert: Long?): String = wert?.let { "$it ms" } ?: "-"

    companion object {
        const val ABTASTRATE = 16_000
        const val BLOCK_BYTES = 2048

        /**
         * So viel Nachlauf ist erlaubt, bevor „das letzte Lebenszeichen kam
         * zu früh" als Abbruch gilt. Der Erkenner schließt das letzte Stück
         * erst nach einer Sprechpause ab.
         */
        const val NACHLAUF_MILLIS = 2_000L

        /** Eine, fünf, fünfzehn Minuten. */
        val DAUERN = listOf(60_000L, 300_000L, 900_000L)
    }
}
