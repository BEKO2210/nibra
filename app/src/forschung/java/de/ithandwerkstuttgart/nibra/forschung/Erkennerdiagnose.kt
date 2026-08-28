package de.ithandwerkstuttgart.nibra.forschung

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Fragt das Gerät, was seine Spracherkennung **wirklich** kann.
 *
 * Der Sprachlauf lieferte auf beiden Geräten leere Ergebnisse -- ohne
 * Fehler. Ein Erkenner, der `onResults` meldet und nichts hineinlegt, sagt
 * von sich aus nicht, warum. `checkRecognitionSupport` sagt es: welche
 * Sprachen liegen auf dem Gerät, welche wären zu haben, welche werden
 * gerade geladen.
 *
 * Das braucht kein gesprochenes Wort und ist deshalb der richtige erste
 * Schritt, bevor noch einmal jemand dreißig Sekunden vorliest.
 */
object Erkennerdiagnose {

    /** Die Sprachangaben, die in Frage kommen -- einschließlich „gar keine". */
    private val SPRACHEN = listOf(null, "de-DE", "de", "en-US")

    /**
     * Vergleicht die Absicht, mit der **Nibra selbst** nach Sprachen fragt,
     * gegen die, mit der diese Diagnose fragt.
     *
     * Anlass: Nibra zeigte „Deutsch (Deutschland) -- Nicht auf dem Gerät"
     * und eine leere Sprachliste, während diese Diagnose auf demselben
     * Gerät `de-DE` als installiert meldete. Einer von beiden fragt falsch.
     * Der Unterschied liegt in den Zusätzen der Absicht, und genau den
     * misst dieser Vergleich -- statt ihn zu vermuten.
     */
    fun vergleicheAbsichten(zusammenhang: Context): String = buildString {
        appendLine("VERGLEICH DER ABFRAGE-ABSICHTEN -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine()
        val aufDemGerät = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(zusammenhang)

        appendLine("--- so fragt Nibra: nur EXTRA_LANGUAGE_MODEL ---")
        appendLine(frageMit(zusammenhang, aufDemGerät) { })
        appendLine()
        appendLine("--- zusätzlich EXTRA_CALLING_PACKAGE ---")
        appendLine(frageMit(zusammenhang, aufDemGerät) {
            it.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
        })
        appendLine()
        appendLine("--- volle Zusätze wie in dieser Diagnose ---")
        appendLine(frageMit(zusammenhang, aufDemGerät) {
            it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
            it.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            it.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
        })
    }

    private fun frageMit(
        zusammenhang: Context,
        aufDemGerät: Boolean,
        ergaenze: (Intent) -> Unit
    ): String {
        val absicht = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            ergaenze(this)
        }
        return frageAbsicht(zusammenhang, absicht, aufDemGerät)
    }

    fun erhebe(zusammenhang: Context): String = buildString {
        appendLine("ERKENNERDIAGNOSE -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Systemsprache: ${java.util.Locale.getDefault().toLanguageTag()}")
        appendLine()

        val aufDemGerät = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(zusammenhang)
        appendLine("isOnDeviceRecognitionAvailable: $aufDemGerät")
        appendLine("isRecognitionAvailable:         " +
            SpeechRecognizer.isRecognitionAvailable(zusammenhang))
        appendLine()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appendLine("checkRecognitionSupport gibt es erst ab Android 13.")
            return@buildString
        }

        SPRACHEN.forEach { sprache ->
            appendLine("--- EXTRA_LANGUAGE = ${sprache ?: "nicht gesetzt"} ---")
            appendLine(frage(zusammenhang, sprache, aufDemGerät))
            appendLine()
        }
    }

    private fun frage(
        zusammenhang: Context,
        sprache: String?,
        aufDemGerät: Boolean
    ): String = frageAbsicht(zusammenhang, baueAbsicht(zusammenhang, sprache), aufDemGerät)

    private fun baueAbsicht(zusammenhang: Context, sprache: String?): Intent {
        val absicht = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            sprache?.let {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, it)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, it)
            }
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
        }
        return absicht
    }

    private fun frageAbsicht(
        zusammenhang: Context,
        absicht: Intent,
        aufDemGerät: Boolean
    ): String {
        val ausgabe = StringBuilder()
        val fertig = CountDownLatch(1)
        val faden = Executors.newSingleThreadExecutor()
        val hauptfaden = android.os.Handler(android.os.Looper.getMainLooper())

        hauptfaden.post {
            val erkenner = runCatching {
                if (aufDemGerät) SpeechRecognizer.createOnDeviceSpeechRecognizer(zusammenhang)
                else SpeechRecognizer.createSpeechRecognizer(zusammenhang)
            }.getOrElse {
                ausgabe.append("  Erkenner nicht erzeugbar: ${it.javaClass.simpleName}")
                fertig.countDown()
                return@post
            }
            runCatching {
                erkenner.checkRecognitionSupport(absicht, faden,
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(hilfe: RecognitionSupport) {
                            ausgabe.appendLine("  auf dem Gerät installiert: " +
                                liste(hilfe.installedOnDeviceLanguages))
                            ausgabe.appendLine("  auf dem Gerät möglich:     " +
                                liste(hilfe.supportedOnDeviceLanguages))
                            ausgabe.appendLine("  wird gerade geladen:       " +
                                liste(hilfe.pendingOnDeviceLanguages))
                            ausgabe.append("  nur über das Netz:         " +
                                liste(hilfe.onlineLanguages))
                            runCatching { erkenner.destroy() }
                            fertig.countDown()
                        }

                        override fun onError(fehler: Int) {
                            ausgabe.append("  checkRecognitionSupport meldet Fehler $fehler" +
                                " (${fehlertext(fehler)})")
                            runCatching { erkenner.destroy() }
                            fertig.countDown()
                        }
                    })
            }.onFailure {
                ausgabe.append("  Aufruf warf ${it.javaClass.simpleName}: ${it.message}")
                runCatching { erkenner.destroy() }
                fertig.countDown()
            }
        }

        if (!fertig.await(15, TimeUnit.SECONDS)) {
            ausgabe.append("  keine Antwort innerhalb von 15 Sekunden")
        }
        faden.shutdown()
        return ausgabe.toString().ifBlank { "  keine Auskunft" }
    }

    private fun liste(werte: List<String>?): String =
        werte?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "keine"

    /**
     * Die Fehlercodes, die hier auftreten können. Eine nackte Zahl im
     * Bericht zwingt später zum Nachschlagen; das gehört hierhin.
     */
    private fun fehlertext(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "Unterstützung nicht prüfbar"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Sprache nicht unterstützt"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Sprache nicht verfügbar"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Dienst getrennt"
        SpeechRecognizer.ERROR_CLIENT -> "Client-Fehler"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Recht fehlt"
        else -> "unbekannt"
    }
}
