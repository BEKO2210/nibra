package de.ithandwerkstuttgart.nibra.forschung

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Der Versuch, den man sonst mit einer Annahme abtut: Können
 * `SpeechRecognizer` und `AudioRecord` gleichzeitig am Mikrofon liegen?
 *
 * Moderne Geräte haben mehrere Mikrofone. Daraus folgt **nicht**, dass
 * Android zwei unabhängige Aufnahmepfade gibt -- aber es folgt auch nicht
 * das Gegenteil. Das lässt sich nur messen.
 *
 * Drei Durchgänge, jeder rund fuenf Sekunden:
 *
 * 1. nur der Erkenner
 * 2. nur die eigene Aufnahme
 * 3. beide gleichzeitig
 *
 * Gemessen wird, ob die eigene Aufnahme echte Abtastwerte bekommt oder nur
 * Stille, ob sich das aktive Mikrofon oder die Kanalzuordnung ändert, und
 * ob die Erkennung weiter arbeitet.
 *
 * **Nur Forschung.** Was hier herauskommt, ist noch kein Entwurf für den
 * Betrieb.
 */
class Nebenlaufversuch(private val zusammenhang: Context) {

    /** Was eine Aufnahme über ihren eigenen Lauf sagen kann. */
    data class Tonbefund(
        val rahmen: Int,
        val groessterAusschlag: Int,
        val effektivwert: Double,
        val stilleRahmen: Int,
        val aktiveMikrofone: List<String>,
        val fehler: String?
    ) {
        /**
         * Der entscheidende Punkt: kam überhaupt Signal an?
         *
         * Ein Strom, der läuft und nur Nullen liefert, ist der Fall, den
         * Android still herbeiführt, wenn zwei Aufnehmer streiten.
         */
        val hatSignal: Boolean get() = groessterAusschlag > 32
    }

    data class Erkennerbefund(
        val ereignisse: List<String>,
        val text: String?,
        val fehlercode: Int?
    )

    data class Durchgang(
        val name: String,
        val ton: Tonbefund?,
        val erkenner: Erkennerbefund?
    )

    private val hauptfaden = Handler(Looper.getMainLooper())

    fun fuehreDurch(): String = buildString {
        appendLine("NEBENLAUFVERSUCH -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Bitte während der Messung gleichmäßig sprechen.")
        appendLine()

        val nurErkenner = Durchgang("1) nur Erkenner", null, erkennerLauf())
        schreibe(nurErkenner)

        val nurTon = Durchgang("2) nur eigene Aufnahme", tonLauf(), null)
        schreibe(nurTon)

        // Beide zugleich: erst den Erkenner anwerfen, dann die eigene
        // Aufnahme dazu. Andersherum wäre die Frage eine andere.
        val erkennerErgebnis = mutableListOf<String>()
        var erkennerText: String? = null
        var erkennerFehler: Int? = null
        val fertig = java.util.concurrent.CountDownLatch(1)
        hauptfaden.post {
            starteErkenner(
                sammle = { erkennerErgebnis += it },
                aufText = { erkennerText = it },
                aufFehler = { erkennerFehler = it },
                aufEnde = { fertig.countDown() }
            )
        }
        Thread.sleep(300)
        val tonWährenddessen = tonLauf()
        fertig.await(8, java.util.concurrent.TimeUnit.SECONDS)

        schreibe(
            Durchgang(
                "3) beide gleichzeitig",
                tonWährenddessen,
                Erkennerbefund(erkennerErgebnis, erkennerText, erkennerFehler)
            )
        )

        appendLine("BEWERTUNG")
        val alleinSignal = nurTon.ton?.hatSignal == true
        val zusammenSignal = tonWährenddessen.hatSignal
        val erkennerAllein = nurErkenner.erkenner!!
        val erkennerZusammen = Erkennerbefund(erkennerErgebnis, erkennerText, erkennerFehler)

        // Der Ablauf des Erkenners ist aussagekräftiger als sein Ergebnis:
        // bleibt die Ereignisfolge unter Nebenlauf dieselbe wie allein, hat
        // die zweite Aufnahme ihn nicht gestört.
        val gleicherAblauf = erkennerAllein.ereignisse == erkennerZusammen.ereignisse

        appendLine("  eigene Aufnahme allein liefert Signal:      ${jaNein(alleinSignal)}")
        appendLine("  eigene Aufnahme neben dem Erkenner:         ${jaNein(zusammenSignal)}")
        appendLine("  Erkenner-Ablauf allein wie nebenläufig:    ${jaNein(gleicherAblauf)}")
        appendLine("  Erkenner allein:      ${befundWort(erkennerAllein)}")
        appendLine("  Erkenner nebenläufig: ${befundWort(erkennerZusammen)}")
        appendLine()

        // Ohne gesprochenes Wort bleibt der Versuch strukturell -- er zeigt
        // dann, dass beide Pfade laufen, aber nicht, dass die Erkennung unter
        // Nebenlauf dieselbe Qualität hat. Das gehört benannt.
        val sprachnachweis = erkennerAllein.text != null || erkennerZusammen.text != null
        appendLine(
            when {
                !alleinSignal ->
                    "  Nicht auswertbar: schon allein kam kein Signal an. " +
                        "Mikrofonrecht fehlt oder das Mikrofon ist belegt."
                !zusammenSignal ->
                    "  Nebenläufig wird die eigene Aufnahme stummgeschaltet. " +
                        "Als Betriebsgrundlage verworfen."
                storendeFehler(erkennerZusammen.fehlercode) ->
                    "  Der Erkenner meldet nebenläufig einen Konfliktfehler " +
                        "(${erkennerZusammen.fehlercode}). Nicht tragfähig."
                gleicherAblauf ->
                    "  Beide Pfade liefen gleichzeitig, der Erkenner-Ablauf blieb " +
                        "unverändert."
                else ->
                    "  Beide Pfade liefen, aber der Erkenner-Ablauf war ein anderer. " +
                        "Vor jeder Nutzung genauer ansehen."
            }
        )
        if (!sprachnachweis) {
            appendLine()
            appendLine(
                "  ACHTUNG: In keinem Durchgang wurde Text erkannt. Der Versuch " +
                    "belegt damit nur, dass beide Aufnahmepfade offen sind und Signal " +
                    "führen -- nicht, dass die Erkennungsqualität unter Nebenlauf " +
                    "gleich bleibt. Dafür muss während der Messung gesprochen werden."
            )
        }
    }

    /**
     * Fehler, die auf einen Streit um das Mikrofon hindeuten -- im Gegensatz
     * zu `ERROR_NO_MATCH` (7) und `ERROR_SPEECH_TIMEOUT` (6), die in einem
     * stillen Raum der Normalfall sind und nichts über Nebenlauf sagen.
     */
    private fun storendeFehler(code: Int?): Boolean = code in setOf(
        SpeechRecognizer.ERROR_AUDIO,
        SpeechRecognizer.ERROR_CLIENT,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED
    )

    private fun befundWort(befund: Erkennerbefund): String = when {
        befund.text != null -> "Text erkannt"
        befund.fehlercode == SpeechRecognizer.ERROR_NO_MATCH -> "nichts verstanden (7)"
        befund.fehlercode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "nichts gehört (6)"
        befund.fehlercode != null -> "Fehler ${befund.fehlercode}"
        else -> "kein Ergebnis, kein Fehler"
    }

    private fun StringBuilder.schreibe(durchgang: Durchgang) {
        appendLine(durchgang.name)
        durchgang.ton?.let { ton ->
            appendLine("  Ton:  Rahmen=${ton.rahmen}  größter=${ton.groessterAusschlag}/32767  " +
                "Effektivwert=${"%.1f".format(ton.effektivwert)}  " +
                "stille Rahmen=${ton.stilleRahmen}  Signal=${jaNein(ton.hatSignal)}")
            ton.fehler?.let { appendLine("  Ton-Fehler: $it") }
            ton.aktiveMikrofone.forEach { appendLine("  aktiv: $it") }
        }
        durchgang.erkenner?.let { erk ->
            appendLine("  Erkenner-Ereignisse: ${erk.ereignisse.joinToString(", ").ifBlank { "keine" }}")
            appendLine("  Erkenner-Text:       ${erk.text ?: "keiner"}")
            appendLine("  Erkenner-Fehler:     ${erk.fehlercode?.toString() ?: "keiner"}")
        }
        appendLine()
    }

    /** Nimmt rund fuenf Sekunden auf und misst, was ankommt. */
    private fun tonLauf(): Tonbefund {
        val rate = 48_000
        val kleinste = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        var aufnahme: AudioRecord? = null
        return try {
            aufnahme = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                kleinste * 4
            )
            if (aufnahme.state != AudioRecord.STATE_INITIALIZED) {
                return Tonbefund(0, 0, 0.0, 0, emptyList(), "nicht bereit")
            }
            aufnahme.startRecording()
            val puffer = ShortArray(kleinste / 2)
            var rahmen = 0
            var größter = 0
            var quadratsumme = 0.0
            var stille = 0
            val bis = System.nanoTime() + 5_000_000_000L
            while (System.nanoTime() < bis) {
                val n = aufnahme.read(puffer, 0, puffer.size)
                if (n <= 0) break
                rahmen += n
                var blockGrößter = 0
                for (i in 0 until n) {
                    val wert = puffer[i].toInt()
                    val betrag = abs(wert)
                    if (betrag > blockGrößter) blockGrößter = betrag
                    quadratsumme += wert.toDouble() * wert
                }
                if (blockGrößter <= 2) stille += n
                if (blockGrößter > größter) größter = blockGrößter
            }
            val aktive = Mikrofonbefund.aktiveMikrofone(aufnahme)
            Tonbefund(
                rahmen = rahmen,
                groessterAusschlag = größter,
                effektivwert = if (rahmen > 0) sqrt(quadratsumme / rahmen) else 0.0,
                stilleRahmen = stille,
                aktiveMikrofone = aktive,
                fehler = null
            )
        } catch (fehler: Throwable) {
            Tonbefund(0, 0, 0.0, 0, emptyList(), "${fehler.javaClass.simpleName} ${fehler.message}")
        } finally {
            runCatching { aufnahme?.stop() }
            runCatching { aufnahme?.release() }
        }
    }

    private fun erkennerLauf(): Erkennerbefund {
        val ereignisse = mutableListOf<String>()
        var text: String? = null
        var fehler: Int? = null
        val fertig = java.util.concurrent.CountDownLatch(1)
        hauptfaden.post {
            starteErkenner({ ereignisse += it }, { text = it }, { fehler = it }) {
                fertig.countDown()
            }
        }
        fertig.await(10, java.util.concurrent.TimeUnit.SECONDS)
        return Erkennerbefund(ereignisse, text, fehler)
    }

    private fun starteErkenner(
        sammle: (String) -> Unit,
        aufText: (String) -> Unit,
        aufFehler: (Int) -> Unit,
        aufEnde: () -> Unit
    ) {
        val erkenner = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(zusammenhang)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(zusammenhang)
            } else {
                SpeechRecognizer.createSpeechRecognizer(zusammenhang)
            }
        }.getOrNull()
        if (erkenner == null) {
            sammle("Erkenner nicht erzeugbar")
            aufEnde()
            return
        }
        erkenner.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) = sammle("bereit")
            override fun onBeginningOfSpeech() = sammle("Sprache beginnt")
            override fun onRmsChanged(rms: Float) = Unit
            override fun onBufferReceived(b: ByteArray?) =
                sammle("Puffer erhalten (${b?.size ?: 0} Bytes)")
            override fun onEndOfSpeech() = sammle("Sprache endet")
            override fun onError(code: Int) {
                sammle("Fehler $code")
                aufFehler(code)
                runCatching { erkenner.destroy() }
                aufEnde()
            }
            override fun onResults(ergebnisse: Bundle?) {
                val treffer = ergebnisse
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                sammle("Ergebnis")
                treffer?.let(aufText)
                runCatching { erkenner.destroy() }
                aufEnde()
            }
            override fun onPartialResults(teil: Bundle?) = sammle("Zwischenstand")
            override fun onEvent(art: Int, p: Bundle?) = Unit
        })
        val absicht = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
        }
        runCatching { erkenner.startListening(absicht) }.onFailure {
            sammle("startListening warf ${it.javaClass.simpleName}")
            aufEnde()
        }
    }

    private fun jaNein(wert: Boolean) = if (wert) "ja" else "nein"
}
