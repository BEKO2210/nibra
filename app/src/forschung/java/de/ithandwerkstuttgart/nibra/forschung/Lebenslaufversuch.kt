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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Gate 3: **Was bleibt liegen, wenn ein Diktat nicht sauber endet?**
 *
 * Im Alltag endet ein Diktat selten ordentlich. Der Nutzer wechselt die
 * App, das Gerät schaltet den Bildschirm ab, jemand ruft an, der Nutzer
 * drückt Abbrechen. Jedes Mal bleiben ein Rohr, ein Faden und ein Erkenner
 * zurück -- und jedes Mal kann etwas davon liegen bleiben.
 *
 * Ein einzelner liegen gebliebener Faden fällt nie auf. Nach dreißig
 * Diktaten ist das Gerät warm und der Akku leer, und niemand weiß warum.
 * Genau solche Fehler findet man nicht durch Benutzen, sondern nur durch
 * Zählen.
 *
 * Gezählt wird deshalb **vor und nach jedem Fall**:
 * - offene Dateizeiger über `/proc/self/fd` -- ein nicht geschlossenes Rohr
 *   verbraucht zwei davon, und das Fach ist begrenzt
 * - laufende Fäden
 *
 * Zwischen Ende und Nachmessung liegt eine Ruhepause. Ohne sie zählte man
 * Fäden, die gerade beim Aufräumen sind, fälschlich als Leiche.
 */
class Lebenslaufversuch(
    private val zusammenhang: Context,
    private val aufStand: (String) -> Unit,
    /**
     * Hintergrund, Vordergrund und Neuaufbau kann der Versuch nicht selbst
     * auslösen -- das kann nur die Activity. Sie reicht die drei Griffe
     * herein.
     */
    private val aufHintergrund: () -> Unit = {},
    private val aufVordergrund: () -> Unit = {},
    private val aufNeuaufbau: () -> Unit = {}
) {

    data class Fall(
        val name: String,
        val was: String,
        val zeigerVorher: Int,
        val zeigerNachher: Int,
        val faedenVorher: Int,
        val faedenNachher: Int,
        val rueckmeldungen: List<String>,
        val nachDemEnde: Int,
        val abgestuerzt: String?,
        /**
         * Ob **nach** dem Fall ein neues Diktat wieder Text liefert.
         *
         * Das ist die eigentliche Frage. „Kein Absturz" heißt wenig: eine
         * App, die nach einem Abbruch stumm bleibt, stürzt auch nicht ab.
         * Der Nutzer merkt den Unterschied sofort, unsere Zählerei erst,
         * wenn man sie danach fragt.
         */
        val nachprobeText: String,
        val nachprobeFehler: Int?
    ) {
        val nachprobeGelungen: Boolean get() = nachprobeText.isNotBlank()
        val zeigerRest: Int get() = zeigerNachher - zeigerVorher
        val faedenRest: Int get() = faedenNachher - faedenVorher

        /**
         * Sauber heißt: nichts bleibt liegen, nichts stürzt ab, und nach
         * dem Ende ruft niemand mehr zurück.
         *
         * Die Toleranz ist bewusst klein. Sie deckt die Fäden ab, die
         * Android selbst für die Verbindung zum Erkennerdienst hält --
         * nicht unsere eigenen.
         */
        val sauber: Boolean
            get() = abgestuerzt == null &&
                zeigerRest <= ZEIGER_TOLERANZ &&
                faedenRest <= FADEN_TOLERANZ &&
                nachDemEnde == 0 &&
                nachprobeGelungen
    }

    private val hauptfaden = Handler(Looper.getMainLooper())

    /**
     * @param nur nur diesen einen Fall fahren. Für die Fälle, bei denen
     *        von außen etwas geschehen muss -- Bildschirm ausschalten etwa --
     *        und die deshalb nicht in einem Durchlauf mit den anderen
     *        stehen können.
     */
    fun fuehreDurch(pcm: ByteArray, nur: String? = null): String = buildString {
        appendLine("LEBENSLAUF -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("Sechs Arten, ein Diktat zu beenden -- eine davon ordentlich.")
        appendLine("Gezählt werden offene Dateizeiger und Fäden vor und nach dem Fall,")
        appendLine("nach ${RUHE_MILLIS} ms Ruhe. Was danach noch übrig ist, ist liegen")
        appendLine("geblieben.")
        appendLine()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appendLine("EXTRA_AUDIO_SOURCE gibt es erst ab Android 13. NICHT PRÜFBAR.")
            return@buildString
        }

        val faelle = listOf(
            "A" to "ordentlich: Rohr schließen, Ende abwarten",
            "B" to "cancel() mitten im Strom",
            "C" to "destroy() mitten im Strom",
            "D" to "der Schreiber stirbt, ohne das Rohr zu schließen",
            "E" to "die Leseseite wird zugemacht, während geschrieben wird",
            "F" to "startListening zweimal auf demselben Erkenner",
            "G" to "die Aufnahme hört auf, das Rohr bleibt offen",
            "H" to "die App geht in den Hintergrund",
            "I" to "die Oberfläche wird neu aufgebaut",
            "J" to "der Bildschirm geht aus und wieder an"
        ).filter { (name, _) -> nur == null || name == nur }
            .map { (name, was) ->
            aufStand("Fall $name: $was")
            fall(name, was, pcm).also { schreibe(it) }
        }

        appendLine("URTEIL")
        val schmutzig = faelle.filter { !it.sauber }
        if (schmutzig.isEmpty()) {
            appendLine("  **Alle sechs Fälle räumen auf.** Kein liegen gebliebener")
            appendLine("  Dateizeiger, kein liegen gebliebener Faden, kein Rückruf nach")
            appendLine("  dem Ende, kein Absturz.")
        } else {
            schmutzig.forEach { f ->
                appendLine("  Fall ${f.name} (${f.was}):")
                if (f.abgestuerzt != null) appendLine("    Absturz: ${f.abgestuerzt}")
                if (f.zeigerRest > ZEIGER_TOLERANZ) {
                    appendLine("    ${f.zeigerRest} Dateizeiger blieben offen -- " +
                        "das Fach ist begrenzt, das häuft sich über viele Diktate.")
                }
                if (f.faedenRest > FADEN_TOLERANZ) {
                    appendLine("    ${f.faedenRest} Fäden blieben laufen -- " +
                        "sie kosten Akku, solange die App lebt.")
                }
                if (!f.nachprobeGelungen) {
                    appendLine("    **Nach diesem Fall diktiert die App nicht mehr.** " +
                        "Das wiegt schwerer als jeder Zähler hier: der Nutzer müsste " +
                        "die App neu starten, um weiterzukommen.")
                }
                if (f.nachDemEnde > 0) {
                    appendLine("    ${f.nachDemEnde} Rückruf(e) **nach** dem Ende -- " +
                        "sie treffen auf einen Zustand, den niemand mehr erwartet.")
                }
            }
        }
    }

    private fun StringBuilder.schreibe(f: Fall) {
        appendLine("FALL ${f.name} -- ${f.was}")
        appendLine("  Dateizeiger    ${f.zeigerVorher} -> ${f.zeigerNachher} " +
            "(${vorzeichen(f.zeigerRest)})")
        appendLine("  Fäden          ${f.faedenVorher} -> ${f.faedenNachher} " +
            "(${vorzeichen(f.faedenRest)})")
        appendLine("  Rückrufe nach dem Ende  ${f.nachDemEnde}")
        appendLine("  Absturz        ${f.abgestuerzt ?: "keiner"}")
        appendLine("  Diktat danach  " + if (f.nachprobeGelungen) {
            "geht wieder: ${f.nachprobeText.take(48)}"
        } else {
            "GEHT NICHT (Fehler ${f.nachprobeFehler ?: "keiner, nur kein Text"})"
        })
        appendLine("  sauber         ${if (f.sauber) "ja" else "NEIN"}")
        appendLine("  Ablauf")
        f.rueckmeldungen.forEach { appendLine("    $it") }
        appendLine()
    }

    private fun fall(name: String, was: String, pcm: ByteArray): Fall {
        val zeigerVorher = offeneZeiger()
        val faedenVorher = Thread.activeCount()
        val ablauf = mutableListOf<String>()
        var absturz: String? = null
        // Ab hier zählt jeder Rückruf als „zu spät". Wird erst gesetzt,
        // wenn der Fall abgeschlossen ist.
        // Über die Fadengrenze sichtbar: gesetzt wird im Versuchsfaden,
        // gelesen in den Rückrufen des Erkenners.
        val beendet = java.util.concurrent.atomic.AtomicBoolean(false)
        var nachDemEnde = 0

        val fertig = CountDownLatch(1)
        var erkenner: SpeechRecognizer? = null
        val nullpunkt = SystemClock.elapsedRealtime()
        fun notiere(text: String) {
            val zeit = SystemClock.elapsedRealtime() - nullpunkt
            synchronized(ablauf) {
                if (beendet.get()) {
                    nachDemEnde += 1
                    ablauf += "%6d ms  ZU SPÄT: %s".format(zeit, text)
                } else {
                    ablauf += "%6d ms  %s".format(zeit, text)
                }
            }
        }

        val aufhoeren = java.util.concurrent.atomic.AtomicBoolean(false)
        val (lesen, schreiben) = ParcelFileDescriptor.createPipe()
        val gestartet = CountDownLatch(1)

        hauptfaden.post {
            val neuer = runCatching {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(zusammenhang)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(zusammenhang)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(zusammenhang)
                }
            }.getOrNull()
            if (neuer == null) {
                notiere("Erkenner nicht erzeugbar")
                gestartet.countDown()
                fertig.countDown()
                return@post
            }
            erkenner = neuer
            neuer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) = notiere("onReadyForSpeech")
                override fun onBeginningOfSpeech() = notiere("onBeginningOfSpeech")
                override fun onRmsChanged(rms: Float) = Unit
                override fun onBufferReceived(b: ByteArray?) = Unit
                override fun onEndOfSpeech() = notiere("onEndOfSpeech")
                override fun onError(code: Int) {
                    notiere("onError $code")
                    fertig.countDown()
                }
                override fun onResults(werte: Bundle?) {
                    notiere("onResults")
                    fertig.countDown()
                }
                override fun onPartialResults(werte: Bundle?) = Unit
                override fun onSegmentResults(werte: Bundle) = notiere("onSegmentResults")
                override fun onEndOfSegmentedSession() {
                    notiere("onEndOfSegmentedSession")
                    fertig.countDown()
                }
                override fun onEvent(art: Int, p: Bundle?) = Unit
            })
            runCatching { neuer.startListening(absicht(lesen)) }
                .onFailure { notiere("startListening warf ${it.javaClass.simpleName}") }
            notiere("startListening")
            gestartet.countDown()
        }
        gestartet.await(5, TimeUnit.SECONDS)

        // Ein Stück Ton einspeisen, damit die Sitzung wirklich läuft. Ein
        // Fall, der auf einer nie angelaufenen Sitzung abbricht, prüft
        // nichts.
        val schreiber = thread(name = "lebenslauf-$name") {
            runCatching {
                val strom = FileOutputStream(schreiben.fileDescriptor)
                var stelle = 0
                val bis = SystemClock.elapsedRealtime() +
                    if (name == "J") LANGE_EINSPEISUNG_MILLIS else EINSPEISUNG_MILLIS
                while (SystemClock.elapsedRealtime() < bis && !aufhoeren.get()) {
                    val menge = minOf(2048, pcm.size - stelle)
                    strom.write(pcm, stelle, menge)
                    stelle = (stelle + menge) % pcm.size
                    Thread.sleep(menge.toLong() * 1000 / (16_000 * 2))
                }
                if (name == "G") {
                    // Rohr absichtlich offen lassen -- so sieht es aus,
                    // wenn die Aufnahme endet und niemand aufräumt.
                    notiere("Schreiber endet, Rohr bleibt offen")
                    return@runCatching
                }
                if (name == "D") {
                    // Absichtlich **ohne** close: der Faden endet, das Rohr
                    // bleibt offen. So sieht es aus, wenn ein Schreiber an
                    // einer Ausnahme stirbt.
                    notiere("Schreiber endet ohne close")
                    throw IllegalStateException("gewollter Abbruch des Schreibers")
                }
                strom.close()
            }.onFailure { notiere("Schreiber: ${it.javaClass.simpleName}") }
            if (name != "D" && name != "G") runCatching { schreiben.close() }
        }

        runCatching {
            when (name) {
                "A" -> schreiber.join(EINSPEISUNG_MILLIS + 5_000)
                "B" -> {
                    Thread.sleep(EINSPEISUNG_MILLIS / 2)
                    hauptfaden.post { runCatching { erkenner?.cancel() } }
                    notiere("cancel gerufen")
                    schreiber.join(EINSPEISUNG_MILLIS + 5_000)
                }
                "C" -> {
                    Thread.sleep(EINSPEISUNG_MILLIS / 2)
                    hauptfaden.post { runCatching { erkenner?.destroy() } }
                    notiere("destroy gerufen")
                    erkenner = null
                    schreiber.join(EINSPEISUNG_MILLIS + 5_000)
                }
                "D" -> schreiber.join(EINSPEISUNG_MILLIS + 5_000)
                "E" -> {
                    Thread.sleep(EINSPEISUNG_MILLIS / 2)
                    runCatching { lesen.close() }
                    notiere("Leseseite geschlossen")
                    schreiber.join(EINSPEISUNG_MILLIS + 5_000)
                }
                "G" -> {
                    // Der Fall, den ein Anruf auslöst: die Aufnahme endet,
                    // aber niemand räumt das Rohr auf. Der Erkenner wartet
                    // dann auf Ton, der nie kommt.
                    Thread.sleep(EINSPEISUNG_MILLIS / 2)
                    aufhoeren.set(true)
                    notiere("Aufnahme gestoppt, Rohr bleibt offen")
                    schreiber.join(EINSPEISUNG_MILLIS + 5_000)
                }
                "H" -> {
                    Thread.sleep(EINSPEISUNG_MILLIS / 2)
                    hauptfaden.post { aufHintergrund() }
                    notiere("in den Hintergrund geschickt")
                    schreiber.join(EINSPEISUNG_MILLIS + 5_000)
                    hauptfaden.post { aufVordergrund() }
                    notiere("zurück in den Vordergrund")
                    Thread.sleep(1_500)
                }
                "I" -> {
                    Thread.sleep(EINSPEISUNG_MILLIS / 2)
                    hauptfaden.post { aufNeuaufbau() }
                    notiere("Oberfläche neu aufgebaut")
                    schreiber.join(EINSPEISUNG_MILLIS + 5_000)
                }
                "J" -> {
                    // Hier geschieht nichts von innen. Der Bildschirm wird
                    // von außen ausgeschaltet, während eingespeist wird --
                    // die Einspeisung läuft deshalb länger, damit die
                    // Schaltung von außen sicher hineinfällt.
                    notiere("wartet auf den Bildschirm von außen")
                    schreiber.join(LANGE_EINSPEISUNG_MILLIS + 10_000)
                }
                "F" -> {
                    Thread.sleep(500)
                    hauptfaden.post {
                        runCatching { erkenner?.startListening(absicht(lesen)) }
                            .onFailure { notiere("zweites startListening warf " +
                                it.javaClass.simpleName) }
                    }
                    notiere("zweites startListening")
                    schreiber.join(EINSPEISUNG_MILLIS + 5_000)
                }
            }
            fertig.await(15, TimeUnit.SECONDS)
        }.onFailure { absturz = "${it.javaClass.simpleName} ${it.message.orEmpty()}" }

        hauptfaden.post { runCatching { erkenner?.destroy() } }
        runCatching { lesen.close() }
        runCatching { schreiben.close() }
        Thread.sleep(RUHE_MILLIS)
        // Erst jetzt gilt jeder weitere Rückruf als zu spät -- vorher wäre
        // das Aufräumen selbst als Fehler gezählt worden.
        beendet.set(true)
        Thread.sleep(RUHE_MILLIS)

        val nachprobe = nachprobe(pcm)
        return Fall(
            name, was, zeigerVorher, offeneZeiger(), faedenVorher, Thread.activeCount(),
            synchronized(ablauf) { ablauf.toList() }, nachDemEnde, absturz,
            nachprobe.first, nachprobe.second
        )
    }

    /**
     * Nach jedem Fall ein kurzes, gewöhnliches Diktat -- mit demselben
     * bekannten Ton, damit das Ergebnis vergleichbar ist.
     *
     * Ein eigener, frischer Erkenner: geprüft wird, ob die App nach dem
     * Abbruch **wieder von vorn** anfangen kann, nicht ob ein bereits
     * offener Erkenner weiterläuft.
     */
    private fun nachprobe(pcm: ByteArray): Pair<String, Int?> {
        val segmente = mutableListOf<String>()
        val lesarten = mutableListOf<String>()
        val teiltexte = mutableListOf<String>()
        var fehler: Int? = null
        val fertig = CountDownLatch(1)
        var erkenner: SpeechRecognizer? = null
        val (lesen, schreiben) = ParcelFileDescriptor.createPipe()
        val gestartet = CountDownLatch(1)

        hauptfaden.post {
            val neuer = runCatching {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(zusammenhang)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(zusammenhang)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(zusammenhang)
                }
            }.getOrNull()
            if (neuer == null) {
                gestartet.countDown()
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
                    if (fehler == null) fehler = code
                    fertig.countDown()
                }
                override fun onResults(werte: Bundle?) {
                    lesarten += lies(werte)
                    fertig.countDown()
                }
                override fun onPartialResults(werte: Bundle?) {
                    lies(werte).firstOrNull()?.let { teiltexte += it }
                }
                override fun onSegmentResults(werte: Bundle) {
                    lies(werte).firstOrNull()?.let { segmente += it }
                }
                override fun onEndOfSegmentedSession() = fertig.countDown()
                override fun onEvent(art: Int, p: Bundle?) = Unit
            })
            runCatching { neuer.startListening(absicht(lesen)) }
                .onFailure { fertig.countDown() }
            gestartet.countDown()
        }
        gestartet.await(5, TimeUnit.SECONDS)

        val schreiber = thread(name = "nachprobe") {
            runCatching {
                FileOutputStream(schreiben.fileDescriptor).use { strom ->
                    var stelle = 0
                    while (stelle < pcm.size) {
                        val menge = minOf(2048, pcm.size - stelle)
                        strom.write(pcm, stelle, menge)
                        stelle += menge
                        Thread.sleep(menge.toLong() * 1000 / (16_000 * 2))
                    }
                }
            }
            runCatching { schreiben.close() }
        }
        schreiber.join(30_000)
        fertig.await(15, TimeUnit.SECONDS)
        hauptfaden.post { runCatching { erkenner?.destroy() } }
        runCatching { lesen.close() }
        Thread.sleep(1_000)

        val wahl = Ergebniswahl.waehle(
            segmente = Ergebniswahl.ohneWiederholung(segmente),
            endergebnis = lesarten,
            zwischenstaende = teiltexte
        )
        return wahl.text to fehler
    }

    private fun absicht(lesen: ParcelFileDescriptor) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, lesen)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        }

    /**
     * Wie viele Dateizeiger der Prozess gerade offen hält.
     *
     * Über `/proc/self/fd`, weil Android keine Zahl dafür anbietet. Gibt
     * das Verzeichnis nichts her, wird **-1** zurückgegeben statt 0 --
     * sonst sähe jeder Fall aus, als hätte er aufgeräumt.
     */
    private fun lies(werte: Bundle?): List<String> =
        werte?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty().filter { it.isNotBlank() }

    private fun offeneZeiger(): Int =
        File("/proc/self/fd").list()?.size ?: -1

    private fun vorzeichen(wert: Int) = if (wert > 0) "+$wert" else "$wert"

    companion object {
        const val EINSPEISUNG_MILLIS = 6_000L

        /** Für Fall J: lang genug, dass eine Schaltung von außen hineinfällt. */
        const val LANGE_EINSPEISUNG_MILLIS = 22_000L
        const val RUHE_MILLIS = 2_000L

        /**
         * Android hält je Erkennerverbindung selbst Zeiger und Fäden. Die
         * Toleranz deckt das ab -- knapp, damit ein echtes Leck nicht darin
         * verschwindet.
         */
        const val ZEIGER_TOLERANZ = 3
        const val FADEN_TOLERANZ = 2
    }
}
