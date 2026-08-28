package de.ithandwerkstuttgart.nibra.forschung

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Findet heraus, **welcher** Zusatz an der Absicht die Erkennung stumm macht.
 *
 * Der Sprachlauf lieferte auf beiden Geräten leere Ergebnisse ohne Fehler,
 * obwohl Deutsch nachweislich auf dem Gerät liegt. Die Ursache muss also in
 * den Zusätzen der Absicht stecken -- und der Unterschied zur laufenden
 * Nibra-Fassung ist überschaubar.
 *
 * Statt zu raten und viermal jemanden vorlesen zu lassen: vier Varianten
 * hintereinander in einem einzigen Sprechvorgang, je fünfzehn Sekunden.
 * Wer redet, redet einfach durch.
 */
class Absichtsversuch(
    private val zusammenhang: Context,
    private val aufStand: (Sprachlauf.Stand) -> Unit
) {

    private data class Variante(
        val name: String,
        val was: String,
        val baue: (Intent) -> Unit
    )

    data class Lesart(val text: String, val konfidenz: Float?)

    data class Befund(
        val name: String,
        val was: String,
        val lesarten: List<Lesart>,
        val ereignisse: List<String>,
        val fehler: Int?,
        val zusaetze: List<String>,
        val bereitMillis: Long?,
        val spracheBeginnMillis: Long?,
        val erstemZwischenstandMillis: Long?,
        val spracheEndeMillis: Long?,
        val ergebnisMillis: Long?
    ) {
        val texte: List<String> get() = lesarten.map { it.text }
    }

    private val hauptfaden = Handler(Looper.getMainLooper())

    /**
     * Die Grundausstattung, die in allen Varianten gleich ist -- genau das,
     * was die laufende Fassung von Nibra setzt.
     */
    private fun grundlage(absicht: Intent) = absicht.apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putExtra(
                RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
            )
            putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
        }
    }

    private val VARIANTEN = listOf(
        Variante("A", "wie die laufende Fassung von Nibra") { },
        Variante("B", "zusätzlich EXTRA_MAX_RESULTS = 5") {
            it.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        },
        Variante("C", "zusätzlich MINIMUM_LENGTH = 10000 ms") {
            it.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10_000)
        },
        Variante("D", "die bisherige Fassung des Sprachlaufs") {
            it.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            it.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10_000)
            it.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500)
            it.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                2_000
            )
            it.removeExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE)
            it.removeExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING)
            it.removeExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION)
        }
    )

    fun fuehreDurch(): String {
        val befunde = VARIANTEN.mapIndexed { stelle, variante ->
            val fortschritt = "Variante ${variante.name} (${stelle + 1} von ${VARIANTEN.size})"
            eineVariante(variante, fortschritt)
        }
        return bericht(befunde)
    }

    private fun eineVariante(variante: Variante, fortschritt: String): Befund {
        val lesarten = mutableListOf<Lesart>()
        val ereignisse = mutableListOf<String>()
        var fehler: Int? = null
        var bereitMillis: Long? = null
        var spracheBeginnMillis: Long? = null
        var erstemZwischenstandMillis: Long? = null
        var spracheEndeMillis: Long? = null
        var ergebnisMillis: Long? = null
        var zusaetze: List<String> = emptyList()
        val nullpunkt = SystemClock.elapsedRealtime()
        val fertig = CountDownLatch(1)
        var erkenner: SpeechRecognizer? = null

        fun notiere(was: String) {
            ereignisse += "%6d ms  %s".format(SystemClock.elapsedRealtime() - nullpunkt, was)
        }

        hauptfaden.post {
            val neuer = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(zusammenhang)
                ) {
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
                    bereitMillis = SystemClock.elapsedRealtime() - nullpunkt
                    notiere("bereit")
                }
                override fun onBeginningOfSpeech() {
                    // Nur der erste Einsatz zählt; der Erkenner meldet das
                    // während einer Sitzung mehrfach.
                    if (spracheBeginnMillis == null) {
                        spracheBeginnMillis = SystemClock.elapsedRealtime() - nullpunkt
                    }
                    notiere("Sprache beginnt")
                }
                override fun onRmsChanged(rms: Float) = Unit
                override fun onBufferReceived(b: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    spracheEndeMillis = SystemClock.elapsedRealtime() - nullpunkt
                    notiere("Sprache endet")
                }
                override fun onError(code: Int) {
                    fehler = code
                    notiere("Fehler $code")
                    fertig.countDown()
                }
                override fun onResults(werte: Bundle?) {
                    val treffer = lies(werte)
                    lesarten += treffer
                    ergebnisMillis = SystemClock.elapsedRealtime() - nullpunkt
                    notiere("Ergebnis: ${treffer.size} Lesart(en) " +
                        treffer.firstOrNull()?.text.orEmpty())
                    fertig.countDown()
                }
                override fun onPartialResults(werte: Bundle?) {
                    val treffer = lies(werte)
                    if (erstemZwischenstandMillis == null && treffer.isNotEmpty()) {
                        erstemZwischenstandMillis = SystemClock.elapsedRealtime() - nullpunkt
                    }
                    notiere("Zwischenstand: ${treffer.size} Lesart(en) " +
                        treffer.firstOrNull()?.text.orEmpty())
                }
                override fun onEvent(art: Int, p: Bundle?) = Unit
            })
            val absicht = grundlage(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
            variante.baue(absicht)
            // Die tatsächlich gesetzten Zusätze aus dem Bündel lesen, statt
            // aufzuschreiben, was gemeint war. Nur so belegt der Bericht,
            // worin sich die Varianten unterscheiden.
            zusaetze = absicht.extras?.let { buendel ->
                buendel.keySet().sorted().map { name ->
                    "$name = ${@Suppress("DEPRECATION") buendel.get(name)}"
                }
            }.orEmpty()
            runCatching { neuer.startListening(absicht) }
                .onFailure { notiere("startListening warf ${it.javaClass.simpleName}") }
        }

        // Fünfzehn Sekunden reden lassen, dann abschließen -- ob der
        // Erkenner von selbst fertig wird oder nicht, ist selbst ein Befund.
        val bis = SystemClock.elapsedRealtime() + SPRECHDAUER_MS
        while (SystemClock.elapsedRealtime() < bis) {
            val rest = bis - SystemClock.elapsedRealtime()
            aufStand(
                Sprachlauf.Stand(
                    fortschritt, "Weiterlesen, ohne Pause.", true,
                    ((rest + 999) / 1000).toInt()
                )
            )
            Thread.sleep(minOf(rest, 200))
        }
        hauptfaden.post { runCatching { erkenner?.stopListening() } }
        fertig.await(6, TimeUnit.SECONDS)
        hauptfaden.post { runCatching { erkenner?.destroy() } }
        Thread.sleep(500)

        return Befund(
            variante.name, variante.was, lesarten, ereignisse, fehler, zusaetze,
            bereitMillis, spracheBeginnMillis, erstemZwischenstandMillis,
            spracheEndeMillis, ergebnisMillis
        )
    }

    private fun lies(werte: Bundle?): List<Lesart> {
        val texte = werte?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val sicher = werte?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        return texte.filter { it.isNotBlank() }.mapIndexed { stelle, text ->
            // Werte außerhalb von 0 bis 1 sind unbrauchbar und gelten als
            // fehlend -- eine erfundene Sicherheit wäre schlimmer als keine.
            Lesart(text, sicher?.getOrNull(stelle)?.takeIf { it in 0f..1f })
        }
    }

    private fun bericht(befunde: List<Befund>) = buildString {
        appendLine("ABSICHTSVERSUCH -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Welcher Zusatz macht die Erkennung stumm?")
        appendLine()
        befunde.forEach { befund ->
            appendLine("VARIANTE ${befund.name} -- ${befund.was}")
            appendLine("  Text gekommen: ${if (befund.lesarten.isEmpty()) "NEIN" else "ja"}")
            appendLine("  Lesarten (n-best): ${befund.lesarten.size}")
            befund.lesarten.forEachIndexed { stelle, lesart ->
                val sicher = lesart.konfidenz?.let { "%.3f".format(it) } ?: "nicht geliefert"
                appendLine("    ${stelle + 1}. [$sicher] ${lesart.text}")
            }
            appendLine("  Fehler: ${befund.fehler?.toString() ?: "keiner"}")
            appendLine("  Zeiten ab startListening:")
            appendLine("    bereit             ${ms(befund.bereitMillis)}")
            appendLine("    Sprache beginnt    ${ms(befund.spracheBeginnMillis)}")
            appendLine("    erster Zwischenstand ${ms(befund.erstemZwischenstandMillis)}")
            appendLine("    Sprache endet      ${ms(befund.spracheEndeMillis)}")
            appendLine("    Ergebnis           ${ms(befund.ergebnisMillis)}")
            appendLine("  Gesetzte Zusätze:")
            befund.zusaetze.forEach { appendLine("    $it") }
            appendLine("  Ereignisse:")
            befund.ereignisse.forEach { appendLine("    $it") }
            appendLine()
        }
        appendLine("URTEIL")
        val ergiebig = befunde.filter { it.texte.isNotEmpty() }.map { it.name }
        val leer = befunde.filter { it.texte.isEmpty() }.map { it.name }
        appendLine("  Text geliefert: ${ergiebig.joinToString().ifBlank { "keine Variante" }}")
        appendLine("  leer geblieben: ${leer.joinToString().ifBlank { "keine" }}")
        appendLine()
        appendLine(
            when {
                ergiebig.isEmpty() ->
                    "  Keine Variante liefert Text. Dann liegt es nicht an den Zusätzen,\n" +
                        "  sondern am Aufbau des Versuchs selbst -- oder daran, dass diese\n" +
                        "  Ausprägung den Erkenner anders erreicht als die laufende Fassung."
                leer.isEmpty() ->
                    "  Alle Varianten liefern Text. Der Fehler steckt dann nicht in der\n" +
                        "  Absicht, sondern im Ablauf des Sprachlaufs -- am ehesten im\n" +
                        "  Neustarten des Erkenners über mehrere Abschnitte."
                else ->
                    "  Der Unterschied liegt zwischen ${ergiebig.last()} und ${leer.first()}.\n" +
                        "  Genau den einen Zusatz, der dort dazukommt, aus dem Sprachlauf\n" +
                        "  entfernen -- und danach noch einmal messen, nicht annehmen."
            }
        )
    }

    private fun ms(wert: Long?): String = wert?.let { "$it ms" } ?: "-"

    companion object {
        const val SPRECHDAUER_MS = 15_000L
    }
}
