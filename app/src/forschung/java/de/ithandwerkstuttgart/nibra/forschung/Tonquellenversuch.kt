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

/**
 * Prüft den offiziellen Weg, dem Erkenner **eigenes** Audio zu geben.
 *
 * Bisher galt: eine eigene Aufnahme und der Android-Erkenner können nicht
 * gleichzeitig laufen -- gemessen, beide Geräte, null erkannte Worte. Daraus
 * wurde zu schnell gefolgert, ein eigener Erkenner müsse den von Android
 * ersetzen. Diese Folgerung ist **zurückgenommen**, denn ein Weg blieb
 * ungeprüft:
 *
 * `RecognizerIntent.EXTRA_AUDIO_SOURCE` nimmt ab Android 13 einen bereits
 * geöffneten Strom entgegen. Dann öffnet nur noch **eine** Seite das
 * Mikrofon -- Nibra -- und reicht das Signal weiter.
 *
 * Ginge das, hätte Nibra beides: eigenes PCM mit Vorlauf und Messung, und
 * den brauchbaren Erkenner von Android. Das wäre erheblich risikoärmer als
 * ein eigenes Modell.
 *
 * **Die Doku sagt ausdrücklich, dass ein Erkenner das Extra ignorieren
 * darf.** Genau darum wird hier zuerst mit einer *bekannten* Aufnahme
 * geprüft und nicht mit dem Mikrofon: Kommt der erwartete Text, ist das
 * ein Beweis. Kommt etwas anderes oder nichts, hat der Dienst das Extra
 * ignoriert und selbst das Mikrofon geöffnet -- und das ist dann das
 * Ergebnis, nicht ein Anlass für Umwege.
 */
class Tonquellenversuch(
    private val zusammenhang: Context,
    private val aufStand: (String) -> Unit
) {

    data class Befund(
        val name: String,
        val beschreibung: String,
        val ereignisse: List<String>,
        val lesarten: List<String>,
        val teiltexte: List<String>,
        val segmentergebnisse: List<String>,
        val fehler: Int?,
        val bereitMillis: Long?,
        val ersteSpracheMillis: Long?,
        val ergebnisMillis: Long?,
        val gesendeteBytes: Long
    ) {
        /**
         * Der beste Text dieses Versuchs: das Endergebnis, und wenn das
         * leer blieb, der letzte Zwischenstand.
         *
         * Der erste Wurf dieser Auswertung sah nur auf die Lesarten und
         * urteilte „NICHT UNTERSTÜTZT" -- dabei hatte der Erkenner den
         * eingespeisten Strom vollständig erkannt und Wort für Wort
         * gemeldet. Nur sein Schlussbericht war leer, wie schon im echten
         * Diktat. Wer das Endergebnis für den einzigen Beleg hält, verwirft
         * ein funktionierendes Verfahren.
         */
        val text: String
            get() = Ergebniswahl.waehle(
                segmente = Ergebniswahl.ohneWiederholung(segmentergebnisse),
                endergebnis = lesarten,
                zwischenstaende = teiltexte
            ).text

        val kamText: Boolean get() = text.isNotBlank()

        /** Wahr, wenn nur der Zwischenstand geblieben ist. */
        val nurGerettet: Boolean
            get() = lesarten.firstOrNull().isNullOrBlank() && teiltexte.isNotEmpty()
    }

    private val hauptfaden = Handler(Looper.getMainLooper())

    /**
     * @param pcm 16 Bit, ein Kanal, [ABTASTRATE] Hz -- roh, ohne Kopf.
     */
    fun fuehreDurch(pcm: ByteArray, bezugstext: String): String = buildString {
        appendLine("TONQUELLENVERSUCH -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("Eingespeist: ${pcm.size} Bytes = " +
            "${pcm.size / 2 * 1000 / ABTASTRATE} ms bei $ABTASTRATE Hz, 16 Bit, 1 Kanal")
        appendLine("Bezugstext: $bezugstext")
        appendLine()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appendLine("EXTRA_AUDIO_SOURCE gibt es erst ab Android 13. NICHT PRÜFBAR.")
            return@buildString
        }

        val befunde = listOf(
            lauf("A", "nur EXTRA_AUDIO_SOURCE", pcm) { },
            lauf("B", "zusätzlich EXTRA_SEGMENTED_SESSION", pcm) {
                it.putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_AUDIO_SOURCE
                )
            }
        )
        befunde.forEach { schreibe(it, bezugstext) }

        appendLine("URTEIL")
        val a = befunde[0]
        appendLine(
            when {
                a.kamText -> {
                    val vergleich = Wortvergleich.vergleiche(bezugstext, a.text)
                    "  EXTRA_AUDIO_SOURCE wird unterstützt. Der Erkenner hat den " +
                        "eingespeisten Strom gelesen und daraus Text gemacht " +
                        "(%.1f %% Wortfehlerrate gegen den Bezugstext).\n".format(
                            vergleich.fehlerrate * 100
                        ) +
                        "  Damit kann Nibra das Mikrofon selbst besitzen und den " +
                        "Android-Erkenner behalten.\n" +
                        if (a.nurGerettet) {
                            "  ACHTUNG: das Endergebnis war leer, der Text stammt aus " +
                                "dem letzten Zwischenstand -- dasselbe Verhalten wie " +
                                "beim Diktat über das Mikrofon."
                        } else {
                            ""
                        }
                }
                a.ereignisse.any { it.contains("bereit") } ->
                    "  Der Erkenner lief, lieferte aber keinen Text. Entweder hat er " +
                        "das Extra ignoriert und auf das (stille) Mikrofon gehört, " +
                        "oder er kam mit dem Format nicht zurecht. NICHT UNTERSTÜTZT, " +
                        "solange kein Text aus dem eingespeisten Strom kommt."
                else ->
                    "  Der Erkenner meldete sich gar nicht. NICHT UNTERSTÜTZT."
            }
        )
        appendLine()
        appendLine("  Zur Einordnung: die Doku erlaubt einem Erkenner ausdrücklich,")
        appendLine("  EXTRA_AUDIO_SOURCE zu ignorieren. Ein Fehlschlag hier ist kein")
        appendLine("  Fehler in Nibra, sondern eine Eigenschaft des Geräts.")
    }

    private fun StringBuilder.schreibe(befund: Befund, bezugstext: String) {
        appendLine("VERSUCH ${befund.name} -- ${befund.beschreibung}")
        appendLine("  Text gekommen:     ${if (befund.kamText) "ja" else "NEIN"}")
        appendLine("  gesendete Bytes:   ${befund.gesendeteBytes}")
        appendLine("  bereit nach:       ${ms(befund.bereitMillis)}")
        appendLine("  Sprache erkannt:   ${ms(befund.ersteSpracheMillis)}")
        appendLine("  Ergebnis nach:     ${ms(befund.ergebnisMillis)}")
        appendLine("  Fehler:            ${befund.fehler?.toString() ?: "keiner"}")
        appendLine("  Zwischenstände:    ${befund.teiltexte.size}")
        befund.teiltexte.takeLast(3).forEach { appendLine("      $it") }
        appendLine("  Segmentergebnisse: ${befund.segmentergebnisse.size}")
        befund.segmentergebnisse.forEach { appendLine("      $it") }
        appendLine("  Lesarten:          ${befund.lesarten.size}")
        befund.lesarten.forEach { appendLine("      $it") }
        if (befund.nurGerettet) {
            appendLine("  ACHTUNG: Endergebnis leer, Text aus dem Zwischenstand")
        }
        appendLine("  bester Text:       ${befund.text}")
        if (befund.kamText) {
            val v = Wortvergleich.vergleiche(bezugstext, befund.text)
            appendLine("  Wortfehlerrate:    %.1f %%".format(v.fehlerrate * 100))
            appendLine("  Unterschiede:")
            appendLine(v.unterschiede())
        }
        appendLine("  Ereignisse:")
        befund.ereignisse.forEach { appendLine("    $it") }
        appendLine()
    }

    private fun lauf(
        name: String,
        beschreibung: String,
        pcm: ByteArray,
        ergaenze: (Intent) -> Unit
    ): Befund {
        aufStand("Versuch $name: $beschreibung")
        val ereignisse = mutableListOf<String>()
        val lesarten = mutableListOf<String>()
        val teiltexte = mutableListOf<String>()
        val segmente = mutableListOf<String>()
        var fehler: Int? = null
        var bereit: Long? = null
        var ersteSprache: Long? = null
        var ergebnis: Long? = null
        var gesendet = 0L
        val nullpunkt = SystemClock.elapsedRealtime()
        val fertig = CountDownLatch(1)

        fun notiere(was: String) {
            ereignisse += "%6d ms  %s".format(SystemClock.elapsedRealtime() - nullpunkt, was)
        }

        // Die Leseseite geht an den Erkenner, auf die Schreibseite legen wir
        // das PCM. Beide Enden gehören geschlossen -- ein offener Schreiber
        // hält den Erkenner sonst am Warten.
        val (lesen, schreiben) = ParcelFileDescriptor.createPipe()
        var erkenner: SpeechRecognizer? = null

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
                override fun onReadyForSpeech(p: Bundle?) {
                    bereit = SystemClock.elapsedRealtime() - nullpunkt
                    notiere("onReadyForSpeech")
                }

                override fun onBeginningOfSpeech() {
                    if (ersteSprache == null) {
                        ersteSprache = SystemClock.elapsedRealtime() - nullpunkt
                    }
                    notiere("onBeginningOfSpeech")
                }

                override fun onRmsChanged(rms: Float) = Unit
                override fun onBufferReceived(b: ByteArray?) =
                    notiere("onBufferReceived ${b?.size ?: 0} B")

                override fun onEndOfSpeech() = notiere("onEndOfSpeech")

                override fun onError(code: Int) {
                    fehler = code
                    notiere("onError $code")
                    fertig.countDown()
                }

                override fun onResults(werte: Bundle?) {
                    ergebnis = SystemClock.elapsedRealtime() - nullpunkt
                    lesarten += lies(werte)
                    notiere("onResults: ${lies(werte).size} Lesart(en)")
                    fertig.countDown()
                }

                override fun onPartialResults(werte: Bundle?) {
                    lies(werte).firstOrNull()?.let {
                        teiltexte += it
                        notiere("onPartialResults: $it")
                    }
                }

                override fun onSegmentResults(werte: Bundle) {
                    segmente += lies(werte).joinToString(" | ")
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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
                // Der eingespeiste Strom und seine genauen Angaben. Ohne sie
                // rät der Erkenner das Format -- und rät falsch.
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, lesen)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, ABTASTRATE)
                ergaenze(this)
            }
            notiere("startListening mit EXTRA_AUDIO_SOURCE")
            runCatching { neuer.startListening(absicht) }
                .onFailure {
                    notiere("startListening warf ${it.javaClass.simpleName}")
                    fertig.countDown()
                }
        }

        // Das PCM in Echtzeit hineingeben. Alles auf einmal zu schreiben
        // wäre unrealistisch -- ein Mikrofon liefert auch in Häppchen, und
        // manche Erkenner stolpern über einen Strom, der schneller kommt
        // als die Wirklichkeit.
        val schreiber = Thread {
            runCatching {
                FileOutputStream(schreiben.fileDescriptor).use { strom ->
                    var stelle = 0
                    while (stelle < pcm.size) {
                        val menge = minOf(BLOCK_BYTES, pcm.size - stelle)
                        strom.write(pcm, stelle, menge)
                        strom.flush()
                        stelle += menge
                        gesendet += menge
                        Thread.sleep(BLOCK_BYTES.toLong() * 1000 / (ABTASTRATE * 2))
                    }
                }
            }.onFailure { notiere("Schreiben abgebrochen: ${it.javaClass.simpleName}") }
            runCatching { schreiben.close() }
            notiere("Strom geschlossen, $gesendet Bytes gesendet")
        }
        Thread.sleep(400)
        schreiber.start()
        schreiber.join(60_000)

        fertig.await(20, TimeUnit.SECONDS)
        hauptfaden.post { runCatching { erkenner?.destroy() } }
        runCatching { lesen.close() }
        Thread.sleep(800)

        return Befund(
            name, beschreibung, ereignisse, lesarten, teiltexte, segmente,
            fehler, bereit, ersteSprache, ergebnis, gesendet
        )
    }

    private fun lies(werte: Bundle?): List<String> =
        werte?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty().filter { it.isNotBlank() }

    private fun ms(wert: Long?): String = wert?.let { "$it ms" } ?: "-"

    companion object {
        const val ABTASTRATE = 16_000

        /** Rund 64 ms je Block -- nah an dem, was ein Mikrofon liefert. */
        const val BLOCK_BYTES = 2048
    }
}
