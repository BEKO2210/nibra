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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Die Zielarchitektur am lebenden Mikrofon.
 *
 * ```
 * AudioRecord ──▶ Tonstrecke ──▶ Rohr ──▶ SpeechRecognizer
 * ```
 *
 * Nibra besitzt das Mikrofon **allein**; der Erkenner bekommt nur, was
 * Nibra ihm gibt. Das ist der Unterschied zum gescheiterten Nebenlauf aus
 * P1, wo beide gleichzeitig zugreifen wollten und keiner etwas bekam.
 *
 * Zwei Durchgänge, damit der Vorlauf nicht nur behauptet wird:
 *
 * 1. **ohne Vorlauf** -- die Erkennung startet verspätet, der Anfang fehlt
 * 2. **mit Vorlauf** -- derselbe Ablauf, aber die Strecke reicht das
 *    Gesammelte nach
 *
 * Steht in Durchgang 2 der Wortanfang, den Durchgang 1 verloren hat, ist
 * der Vorlauf belegt. Steht er in beiden, hat der Versuch nichts gezeigt --
 * dann war die Verzögerung zu kurz, und das gehört gesagt statt gefeiert.
 */
class Livestreckenversuch(
    private val zusammenhang: Context,
    /**
     * Welche Sprache der Erkenner verwenden soll.
     *
     * Nicht fest verdrahtet: das Pixel 9 hat nur en-US auf dem Gerät, und
     * eine Messung mit de-DE liefert dort schlicht nichts. Ein fest
     * eingebautes de-DE hätte das als Versagen der Strecke gelesen --
     * dabei fehlt nur das Sprachmodell.
     */
    private val sprache: String = "de-DE",
    private val aufStand: (String) -> Unit
) {

    data class Durchgang(
        val name: String,
        val mitVorlauf: Boolean,
        val strecke: Tonstrecke.Befund,
        val zeitleiste: List<String>,
        val ereignisse: List<String>,
        val teiltexte: List<String>,
        val segmente: List<String>,
        val lesarten: List<String>,
        val fehler: Int?
    ) {
        /**
         * Über die geprüfte Ergebniswahl -- Segment, sonst Endergebnis,
         * sonst geretteter Zwischenstand. Wiederholungen zwischen den
         * Segmenten werden vorher entfernt, sonst stünde derselbe Satz
         * zweimal da.
         */
        private val wahl: Ergebniswahl.Wahl
            get() = Ergebniswahl.waehle(
                segmente = Ergebniswahl.ohneWiederholung(segmente),
                endergebnis = lesarten,
                zwischenstaende = teiltexte
            )

        val text: String get() = wahl.text
        val herkunft: String get() = wahl.herkunft.name
    }

    private val hauptfaden = Handler(Looper.getMainLooper())

    fun fuehreDurch(bezugstext: String, verzoegerungMillis: Long): String = buildString {
        appendLine("LIVESTRECKE -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("AudioRecord besitzt das Mikrofon allein und speist den Erkenner")
        appendLine("über EXTRA_AUDIO_SOURCE. Die Erkennung startet absichtlich erst")
        appendLine("$verzoegerungMillis ms nach der Aufnahme -- ohne Vorlauf fehlt")
        appendLine("dann der Anfang, mit Vorlauf nicht.")
        appendLine()
        appendLine("Bezugstext: $bezugstext")
        appendLine()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appendLine("EXTRA_AUDIO_SOURCE gibt es erst ab Android 13. NICHT PRÜFBAR.")
            return@buildString
        }

        val ohne = durchgang("1", mitVorlauf = false, verzoegerungMillis, bezugstext)
        val mit = durchgang("2", mitVorlauf = true, verzoegerungMillis, bezugstext)
        listOf(ohne, mit).forEach { schreibe(it, bezugstext) }

        appendLine("URTEIL")
        appendLine("  Ton lückenlos ohne Vorlauf: ${jaNein(ohne.strecke.luekenlos)}")
        appendLine("  Ton lückenlos mit Vorlauf:  ${jaNein(mit.strecke.luekenlos)}")
        appendLine("  Text ohne Vorlauf: ${ohne.text.ifBlank { "(keiner)" }}")
        appendLine("  Text mit Vorlauf:  ${mit.text.ifBlank { "(keiner)" }}")
        appendLine()

        val ersteWorte = Wortvergleich.zerlege(bezugstext).take(2)
        val ohneHatAnfang = ersteWorte.all { Wortvergleich.zerlege(ohne.text).contains(it) }
        val mitHatAnfang = ersteWorte.all { Wortvergleich.zerlege(mit.text).contains(it) }
        appendLine("  Anfang „${ersteWorte.joinToString(" ")}\" ohne Vorlauf: ${jaNein(ohneHatAnfang)}")
        appendLine("  Anfang „${ersteWorte.joinToString(" ")}\" mit Vorlauf:  ${jaNein(mitHatAnfang)}")
        appendLine()
        appendLine(
            when {
                mit.text.isBlank() ->
                    "  Mit Vorlauf kam kein Text. Die Strecke trägt nicht -- alles " +
                        "Weitere wäre Spekulation."
                mitHatAnfang && !ohneHatAnfang ->
                    "  **Der Vorlauf rettet den Wortanfang.** Ohne ihn fehlt er, mit " +
                        "ihm ist er da. Genau dafür ist er gebaut."
                mitHatAnfang && ohneHatAnfang ->
                    "  Beide Durchgänge haben den Anfang. Die Verzögerung war zu kurz, " +
                        "um einen Verlust zu erzeugen -- der Vorlauf ist damit **nicht " +
                        "belegt**, nur unschädlich. Mit größerer Verzögerung wiederholen."
                else ->
                    "  Auch mit Vorlauf fehlt der Anfang. Der Vorlauf greift nicht wie " +
                        "gedacht; Reihenfolge und Länge prüfen."
            }
        )
    }

    private fun StringBuilder.schreibe(d: Durchgang, bezugstext: String) {
        appendLine("DURCHGANG ${d.name} -- Vorlauf ${if (d.mitVorlauf) "an" else "aus"}")
        val s = d.strecke
        appendLine("  AUFNAHME")
        appendLine("    gelesene Rahmen        ${s.geleseneRahmen}")
        appendLine("    Laufzeit               ${s.laufzeitMillis} ms")
        appendLine("    Verlust gegen die Uhr  ${s.verlustMillis} ms " +
            if (s.luekenlos) "(lückenlos)" else "(ES FEHLT TON)")
        appendLine("    verworfene Blöcke      ${s.verworfeneBloecke}")
        appendLine("    größte Warteschlange   ${s.groessteWarteschlange} von 64")
        appendLine("    leere Warteversuche    ${s.blockierteSchreibversuche}")
        appendLine("    Lesefehler             ${s.leseFehler}")
        appendLine("    größter Ausschlag      ${s.spitze}/32767")
        appendLine("    an das Rohr            ${s.gesendeteBytes} Bytes " +
            "(davon Vorlauf ${s.vorlaufBytes})")
        appendLine("    Fehler                 ${s.fehler ?: "keiner"}")
        appendLine("  ERKENNUNG")
        appendLine("    Herkunft des Textes    ${d.herkunft}")
        appendLine("    Text                   ${d.text.ifBlank { "(keiner)" }}")
        appendLine("    Segmente               ${d.segmente.size}")
        appendLine("    Zwischenstände         ${d.teiltexte.size}")
        appendLine("    Fehler                 ${d.fehler?.toString() ?: "keiner"}")
        if (d.text.isNotBlank()) {
            val v = Wortvergleich.vergleiche(bezugstext, d.text)
            appendLine("    Wortfehlerrate         %.1f %%".format(v.fehlerrate * 100))
        }
        appendLine("  ZEITLEISTE")
        d.zeitleiste.forEach { appendLine("    $it") }
        d.ereignisse.forEach { appendLine("    $it") }
        appendLine()
    }

    private fun durchgang(
        name: String,
        mitVorlauf: Boolean,
        verzoegerungMillis: Long,
        bezugstext: String
    ): Durchgang {
        aufStand("Durchgang $name -- Vorlauf ${if (mitVorlauf) "an" else "aus"}. Bitte vorlesen.")
        val strecke = Tonstrecke(ABTASTRATE, VORLAUF_MILLIS)
        val ereignisse = mutableListOf<String>()
        val teiltexte = mutableListOf<String>()
        val segmente = mutableListOf<String>()
        val lesarten = mutableListOf<String>()
        var fehler: Int? = null
        val nullpunkt = SystemClock.elapsedRealtime()
        val fertig = CountDownLatch(1)
        var erkenner: SpeechRecognizer? = null

        fun notiere(was: String) {
            ereignisse += "%6d ms  %s".format(SystemClock.elapsedRealtime() - nullpunkt, was)
        }

        strecke.starte()
        // Absichtlich zu spät: ohne Vorlauf muss hier Anfang verloren gehen.
        Thread.sleep(verzoegerungMillis)

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
                notiere("Erkenner nicht erzeugbar")
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
                    fehler = code
                    notiere("onError $code")
                    fertig.countDown()
                }
                override fun onResults(werte: Bundle?) {
                    lesarten += lies(werte)
                    notiere("onResults: ${lies(werte).size} Lesart(en)")
                    fertig.countDown()
                }
                override fun onPartialResults(werte: Bundle?) {
                    lies(werte).firstOrNull()?.let { teiltexte += it }
                }
                override fun onSegmentResults(werte: Bundle) {
                    // Nur die beste Lesart. Alle mit Trennstrich zu verketten
                    // war ein Fehler der ersten Fassung: der Wortvergleich
                    // zählte dann jede Alternative als zusätzliche Wörter und
                    // meldete 133 % Fehlerrate für einen Satz, der fast
                    // richtig erkannt war.
                    lies(werte).firstOrNull()?.let { segmente += it }
                    notiere("onSegmentResults: ${lies(werte).firstOrNull().orEmpty()}")
                }
                override fun onEndOfSegmentedSession() {
                    notiere("onEndOfSegmentedSession")
                    fertig.countDown()
                }
                override fun onEvent(art: Int, p: Bundle?) = Unit
            })
            val absicht = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, sprache)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sprache)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, lesen)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, ABTASTRATE)
                putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_AUDIO_SOURCE
                )
            }
            strecke.marke("Erkennung gestartet")
            notiere("startListening")
            runCatching { neuer.startListening(absicht) }
                .onFailure {
                    notiere("startListening warf ${it.javaClass.simpleName}")
                    fertig.countDown()
                }
        }

        Thread.sleep(300)
        strecke.speiseIn(schreiben, mitVorlauf)
        aufStand("Durchgang $name: JETZT vorlesen")
        Thread.sleep(SPRECHDAUER_MILLIS)
        strecke.beendeEinspeisung()
        strecke.marke("Einspeisung beendet")
        fertig.await(20, TimeUnit.SECONDS)
        val befund = strecke.halteAn()
        hauptfaden.post { runCatching { erkenner?.destroy() } }
        runCatching { lesen.close() }
        Thread.sleep(1_000)

        return Durchgang(
            name, mitVorlauf, befund, strecke.zeitleiste(),
            ereignisse, teiltexte, segmente, lesarten, fehler
        )
    }

    private fun lies(werte: Bundle?): List<String> =
        werte?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty().filter { it.isNotBlank() }

    private fun jaNein(wert: Boolean) = if (wert) "ja" else "nein"

    companion object {
        const val ABTASTRATE = 16_000
        const val VORLAUF_MILLIS = 1_500
        const val SPRECHDAUER_MILLIS = 12_000L
    }
}
