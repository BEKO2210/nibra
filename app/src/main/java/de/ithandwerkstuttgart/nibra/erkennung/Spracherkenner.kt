package de.ithandwerkstuttgart.nibra.erkennung

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import de.ithandwerkstuttgart.nibra.ui.modell.Fehlerart
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Was waehrend einer Erkennung passiert. */
sealed interface Erkennungsereignis {
    /** Der Erkenner hoert jetzt zu. */
    data object Hoert : Erkennungsereignis

    /** Neuer Pegel, bereits auf 0f..1f gebracht. */
    data class Pegel(val wert: Float) : Erkennungsereignis

    /** Der Sprecher hat aufgehoert, der Erkenner wandelt noch. */
    data object Stille : Erkennungsereignis

    /** Zwischenstand, kann sich noch aendern. */
    data class Teiltext(val text: String) : Erkennungsereignis

    /** Endgueltiger Text. Danach folgt kein weiteres Ereignis. */
    data class Ergebnis(val text: String) : Erkennungsereignis

    /** Abbruch mit einem im Klartext erklaerbaren Grund. */
    data class Fehlgeschlagen(val art: Fehlerart) : Erkennungsereignis
}

/**
 * Kapselt Androids [SpeechRecognizer]. Ab API 33 wird der reine
 * Geraete-Erkenner verwendet (`createOnDeviceSpeechRecognizer`), darunter
 * der normale Erkenner mit `EXTRA_PREFER_OFFLINE` (AUFTRAG.md, Nachtrag
 * "Spracherkennung -- Entscheidung"). Kein fremder Endpunkt, kein Schluessel.
 */
@Singleton
class Spracherkenner @Inject constructor(
    private val context: Context
) : Erkennerquelle {

    fun istVerfuegbar(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ||
                SpeechRecognizer.isRecognitionAvailable(context)
        } else {
            SpeechRecognizer.isRecognitionAvailable(context)
        }

    fun hatMikrofonRecht(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Startet eine Erkennung und liefert ihren Verlauf. Wird der Fluss
     * abgebrochen, hoert der Erkenner sofort auf. Ein Stopp von aussen
     * (Nutzer tippt auf "beenden") geschieht ueber [stoppen].
     */
    override fun erkenne(
        sprachCode: String,
        stoppBeiStille: Boolean
    ): Flow<Erkennungsereignis> = callbackFlow {
        if (!hatMikrofonRecht()) {
            trySend(Erkennungsereignis.Fehlgeschlagen(Fehlerart.KEIN_MIKROFON_RECHT))
            close()
            return@callbackFlow
        }
        if (!istVerfuegbar()) {
            trySend(Erkennungsereignis.Fehlgeschlagen(Fehlerart.ERKENNUNG_NICHT_VERFUEGBAR))
            close()
            return@callbackFlow
        }

        val hauptfaden = Handler(Looper.getMainLooper())
        var erkenner: SpeechRecognizer? = null
        // Manche Geraete melden ihre Sprache mit Erweiterungen
        // ("de-DE-u-fw-mon") oder kennen nur die Sprache ohne Land. Deshalb
        // der Reihe nach probieren, bevor ein Sprachfehler gemeldet wird.
        val kandidaten = sprachkandidaten(sprachCode)
        var kandidat = 0

        val zuhoerer = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(Erkennungsereignis.Hoert)
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                trySend(Erkennungsereignis.Pegel(pegelAus(rmsdB)))
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                trySend(Erkennungsereignis.Stille)
            }

            override fun onError(error: Int) {
                val art = fehlerartAus(error)
                if (art == Fehlerart.SPRACHE_NICHT_AUF_GERAET && kandidat + 1 < kandidaten.size) {
                    kandidat += 1
                    hauptfaden.post {
                        val laufender = erkenner ?: return@post
                        runCatching {
                            laufender.cancel()
                            laufender.startListening(
                                absicht(kandidaten[kandidat], stoppBeiStille)
                            )
                        }.onFailure {
                            trySend(Erkennungsereignis.Fehlgeschlagen(art))
                            close()
                        }
                    }
                    return
                }
                trySend(Erkennungsereignis.Fehlgeschlagen(art))
                close()
            }

            override fun onResults(results: Bundle?) {
                val text = ersterText(results)
                if (text.isNullOrBlank()) {
                    trySend(Erkennungsereignis.Fehlgeschlagen(Fehlerart.NICHTS_VERSTANDEN))
                } else {
                    trySend(Erkennungsereignis.Ergebnis(text))
                }
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                ersterText(partialResults)?.takeIf { it.isNotBlank() }?.let {
                    trySend(Erkennungsereignis.Teiltext(it))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        hauptfaden.post {
            val neuerErkenner = runCatching { baueErkenner() }.getOrNull()
            if (neuerErkenner == null) {
                trySend(Erkennungsereignis.Fehlgeschlagen(Fehlerart.ERKENNUNG_NICHT_VERFUEGBAR))
                close()
                return@post
            }
            erkenner = neuerErkenner
            laufender = neuerErkenner
            neuerErkenner.setRecognitionListener(zuhoerer)
            runCatching { neuerErkenner.startListening(absicht(kandidaten[kandidat], stoppBeiStille)) }
                .onFailure {
                    trySend(Erkennungsereignis.Fehlgeschlagen(Fehlerart.UNBEKANNT))
                    close()
                }
        }

        awaitClose {
            hauptfaden.post {
                val zuBeenden = erkenner ?: return@post
                runCatching { zuBeenden.cancel() }
                runCatching { zuBeenden.destroy() }
                if (laufender === zuBeenden) laufender = null
            }
        }
    }

    /**
     * Beendet die laufende Aufnahme und laesst den Erkenner das bereits
     * Gesprochene noch auswerten -- anders als ein Abbruch des Flusses.
     */
    override fun stoppen() {
        Handler(Looper.getMainLooper()).post {
            runCatching { laufender?.stopListening() }
        }
    }

    @Volatile
    private var laufender: SpeechRecognizer? = null

    private fun baueErkenner(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    private fun absicht(sprachCode: String, stoppBeiStille: Boolean) =
        android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            if (sprachCode.isNotBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, sprachCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sprachCode)
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android setzt Satzzeichen und Grossschreibung selbst, wenn
                // man es darum bittet -- ohne das kommt reiner Kleintext ohne
                // Punkt und Komma an.
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
                )
                // Zwischenstaende ohne wackelndes Satzzeichen am Ende.
                putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
            }
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (!stoppBeiStille) {
                // Ohne "Stopp bei Stille" soll eine Sprechpause die Aufnahme
                // nicht beenden -- der Nutzer beendet selbst.
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    LANGE_STILLE_MILLIS
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    LANGE_STILLE_MILLIS
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    MINDESTDAUER_MILLIS
                )
            }
        }

    /**
     * Bittet Android, das Sprachpaket fuer [sprachCode] auf das Geraet zu
     * holen. Das ist der einzige Vorgang, der ueberhaupt Netz beruehrt --
     * und er laeuft im Erkennungsdienst des Systems, nicht in Nibra
     * (AUFTRAG.md, Antwort 4).
     */
    fun ladeSprachmodell(sprachCode: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        Handler(Looper.getMainLooper()).post {
            val erkenner = runCatching {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            }.getOrNull() ?: return@post
            runCatching {
                erkenner.triggerModelDownload(absicht(sprachCode, stoppBeiStille = true))
            }
            // Der Anstoss laeuft im Systemdienst weiter; die Huelle hier
            // wird kurz danach freigegeben, damit nichts leckt.
            Handler(Looper.getMainLooper()).postDelayed(
                { runCatching { erkenner.destroy() } },
                ANSTOSS_HALTEZEIT_MILLIS
            )
        }
    }

    /**
     * Reihenfolge der Versuche: der gewuenschte Code ohne Erweiterungen,
     * dann Sprache mit Land, dann nur die Sprache, zuletzt ohne Angabe --
     * dann waehlt der Erkenner selbst.
     */
    internal fun sprachkandidaten(sprachCode: String): List<String> {
        val locale = Locale.forLanguageTag(sprachCode.replace('_', '-'))
        val sprache = locale.language
        val land = locale.country
        return listOfNotNull(
            sprachCode.takeIf { it.isNotBlank() },
            if (sprache.isNotBlank() && land.isNotBlank()) "$sprache-$land" else null,
            sprache.takeIf { it.isNotBlank() },
            ""
        ).distinct()
    }

    private fun ersterText(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private companion object {
        const val ANSTOSS_HALTEZEIT_MILLIS = 5_000L
        const val LANGE_STILLE_MILLIS = 600_000L
        const val MINDESTDAUER_MILLIS = 1_000L

        /** Der Erkenner meldet etwa -2 dB (Stille) bis 10 dB (laut). */
        fun pegelAus(rmsdB: Float): Float = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)

        fun fehlerartAus(fehler: Int): Fehlerart = when (fehler) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Fehlerart.KEIN_MIKROFON_RECHT
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Fehlerart.NICHTS_VERSTANDEN
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> Fehlerart.SPRACHE_NICHT_AUF_GERAET
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Fehlerart.ERKENNUNG_NICHT_VERFUEGBAR
            else -> Fehlerart.UNBEKANNT
        }
    }
}
