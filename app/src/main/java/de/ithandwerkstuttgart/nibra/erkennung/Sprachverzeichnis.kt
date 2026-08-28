package de.ithandwerkstuttgart.nibra.erkennung

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import de.ithandwerkstuttgart.nibra.ui.modell.Diktatsprache
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Fragt das Gerät, welche Diktatsprachen es kann. Nichts wird geraten:
 * gemeldet wird nur, was Android selbst zurückgibt -- ab API 33 über
 * `checkRecognitionSupport`, darunter über die Sprachliste des
 * Erkennungsdienstes.
 */
@Singleton
class Sprachverzeichnis @Inject constructor(
    private val context: Context
) {

    /**
     * @param anzeigeSprache Sprache, in der die Namen erscheinen sollen
     *        (die Oberflächensprache).
     */
    suspend fun verfuegbareSprachen(anzeigeSprache: Locale): List<Diktatsprache> {
        val gemeldet = withTimeoutOrNull(ABFRAGE_ZEIT_MILLIS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                ueberUnterstuetzung()
            } else {
                ueberRundruf()
            }
        } ?: Gemeldet(emptySet(), emptySet())

        val codes = (gemeldet.aufGeraet + gemeldet.weitere).ifEmpty {
            // Meldet das Gerät nichts, bleibt nur die Systemsprache --
            // erfunden wird keine Liste.
            setOf(Locale.getDefault().toLanguageTag())
        }

        return codes
            .map { code -> baueSprache(code, gemeldet.aufGeraet.contains(code), anzeigeSprache) }
            .distinctBy { it.code }
            .sortedBy { it.name.lowercase(anzeigeSprache) }
    }

    private data class Gemeldet(val aufGeraet: Set<String>, val weitere: Set<String>)

    private fun baueSprache(
        code: String,
        aufGeraet: Boolean,
        anzeigeSprache: Locale
    ): Diktatsprache {
        val locale = Locale.forLanguageTag(code.replace('_', '-'))
        val name = locale.getDisplayName(anzeigeSprache).ifBlank { code }
        val eigen = locale.getDisplayName(locale).ifBlank { code }
        return Diktatsprache(
            code = code,
            name = name.replaceFirstChar { it.uppercase(anzeigeSprache) },
            eigenName = eigen.replaceFirstChar { it.uppercase(locale) },
            aufGeraetVerfuegbar = aufGeraet
        )
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun ueberUnterstuetzung(): Gemeldet =
        suspendCancellableCoroutine { fortsetzung ->
            val hauptfaden = Handler(Looper.getMainLooper())
            hauptfaden.post {
                val erkenner = runCatching {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                }.getOrNull()
                if (erkenner == null) {
                    if (fortsetzung.isActive) fortsetzung.resume(Gemeldet(emptySet(), emptySet()))
                    return@post
                }
                val ausfuehrer = Executor { befehl -> hauptfaden.post(befehl) }
                val absicht = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                }
                runCatching {
                    erkenner.checkRecognitionSupport(
                        absicht,
                        ausfuehrer,
                        object : RecognitionSupportCallback {
                            override fun onSupportResult(unterstuetzung: RecognitionSupport) {
                                // Nur installierte Pakete liegen wirklich auf
                                // dem Gerät. "supported" heißt bloß: könnte
                                // geladen werden -- das wäre sonst gelogen.
                                val aufGeraet = unterstuetzung.installedOnDeviceLanguages.toSet()
                                val weitere = (unterstuetzung.supportedOnDeviceLanguages +
                                    unterstuetzung.onlineLanguages).toSet()
                                erkenner.destroy()
                                if (fortsetzung.isActive) {
                                    fortsetzung.resume(Gemeldet(aufGeraet, weitere))
                                }
                            }

                            override fun onError(fehler: Int) {
                                erkenner.destroy()
                                if (fortsetzung.isActive) {
                                    fortsetzung.resume(Gemeldet(emptySet(), emptySet()))
                                }
                            }
                        }
                    )
                }.onFailure {
                    erkenner.destroy()
                    if (fortsetzung.isActive) fortsetzung.resume(Gemeldet(emptySet(), emptySet()))
                }
            }
        }

    private suspend fun ueberRundruf(): Gemeldet =
        suspendCancellableCoroutine { fortsetzung ->
            val empfaenger = object : BroadcastReceiver() {
                override fun onReceive(kontext: Context?, absicht: Intent?) {
                    val ergebnis: Bundle? = getResultExtras(true)
                    val liste = ergebnis
                        ?.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)
                        ?.toSet()
                        .orEmpty()
                    if (fortsetzung.isActive) {
                        // Ohne die neuere Schnittstelle meldet das Gerät nicht,
                        // ob eine Sprache offline liegt -- deshalb "weitere".
                        fortsetzung.resume(Gemeldet(emptySet(), liste))
                    }
                }
            }
            runCatching {
                context.sendOrderedBroadcast(
                    Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS),
                    null,
                    empfaenger,
                    Handler(Looper.getMainLooper()),
                    0,
                    null,
                    null
                )
            }.onFailure {
                if (fortsetzung.isActive) fortsetzung.resume(Gemeldet(emptySet(), emptySet()))
            }
        }

    private companion object {
        const val ABFRAGE_ZEIT_MILLIS = 4_000L
    }
}
