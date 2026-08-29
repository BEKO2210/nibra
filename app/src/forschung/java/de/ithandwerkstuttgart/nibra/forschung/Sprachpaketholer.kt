package de.ithandwerkstuttgart.nibra.forschung

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Holt ein Sprachpaket **auf das Gerät**.
 *
 * Das Laden braucht Netz -- das Paket muss ja irgendwoher kommen. Die
 * Erkennung danach läuft auf dem Gerät, und genau darum geht es: ein
 * Erkenner über das Netz wäre eine andere Messung und würde die Frage,
 * die wir stellen, gar nicht beantworten.
 *
 * Es ist derselbe Weg, den Nibra dem Nutzer anbietet, wenn ihm eine
 * Sprache fehlt. Nichts daran ist ein Trick für die Messung.
 */
object Sprachpaketholer {

    fun hole(zusammenhang: Context, sprachCode: String): String = buildString {
        appendLine("SPRACHPAKET HOLEN -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Gewünscht: $sprachCode")
        appendLine()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appendLine("triggerModelDownload gibt es erst ab Android 13. NICHT MÖGLICH.")
            return@buildString
        }

        val hauptfaden = Handler(Looper.getMainLooper())
        val fertig = CountDownLatch(1)
        var meldung = "keine Rückmeldung"
        var erkenner: SpeechRecognizer? = null

        hauptfaden.post {
            val neuer = runCatching {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(zusammenhang)
            }.getOrNull()
            if (neuer == null) {
                meldung = "Erkenner nicht erzeugbar"
                fertig.countDown()
                return@post
            }
            erkenner = neuer
            val absicht = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, sprachCode)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
            }
            val ausfuehrer = Executor { befehl -> hauptfaden.post(befehl) }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    neuer.triggerModelDownload(absicht, ausfuehrer, object : ModelDownloadListener {
                        override fun onProgress(anteil: Int) = Unit
                        override fun onSuccess() {
                            meldung = "geladen"
                            fertig.countDown()
                        }
                        override fun onScheduled() {
                            meldung = "eingeplant -- läuft im Hintergrund weiter"
                            fertig.countDown()
                        }
                        override fun onError(fehler: Int) {
                            meldung = "Fehler $fehler"
                            fertig.countDown()
                        }
                    })
                } else {
                    neuer.triggerModelDownload(absicht)
                    meldung = "angestoßen (ohne Rückmeldung vor Android 14)"
                    fertig.countDown()
                }
            }.onFailure {
                meldung = "${it.javaClass.simpleName}: ${it.message}"
                fertig.countDown()
            }
        }

        fertig.await(WARTEGRENZE_MINUTEN, TimeUnit.MINUTES)
        hauptfaden.post { runCatching { erkenner?.destroy() } }
        appendLine("Ergebnis: $meldung")
        appendLine()
        appendLine("Danach der Bestand:")
        appendLine(Erkennerdiagnose.erhebe(zusammenhang).lineSequence()
            .filter { it.contains("installiert") || it.contains("geladen") }
            .take(4).joinToString("\n"))
    }

    private const val WARTEGRENZE_MINUTEN = 10L
}
