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
    private val context: Context,
    private val halter: Erkennerhalter
) {

    /**
     * @param anzeigeSprache Sprache, in der die Namen erscheinen sollen
     *        (die Oberflächensprache).
     */
    suspend fun verfuegbareSprachen(anzeigeSprache: Locale): List<Diktatsprache> {
        val gemeldet = try {
            withTimeoutOrNull(ABFRAGE_ZEIT_MILLIS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    ueberUnterstuetzung()
                } else {
                    ueberRundruf()
                }
            } ?: Gemeldet(emptySet(), emptySet())
        } finally {
            // **Unbedingt zurückgeben.** Auf dem S23 Ultra antwortet
            // checkRecognitionSupport überhaupt nicht -- weder mit einem
            // Ergebnis noch mit einem Fehler. Ohne diese Zeile bliebe der
            // eine Erkenner des Prozesses für immer verliehen, und das
            // Diktat bekäme nie wieder einen. Gemessen am Gerät:
            //
            //   -> Erkenner verliehen  Sprachliste
            //   (und nie zurück)
            //
            // Verworfen wird er dabei: ein Erkenner, der auf eine Frage
            // nicht geantwortet hat, ist in unklarem Zustand.
            Handler(Looper.getMainLooper()).post {
                if (halter.istVerliehen()) halter.gibZurueck(ZWECK, wegwerfen = true)
            }
        }

        // Meldet das Gerät nichts, bleibt nur die Systemsprache -- erfunden
        // wird keine Liste.
        val nichtsGemeldet = gemeldet.aufGeraet.isEmpty() && gemeldet.weitere.isEmpty()
        val codes = (gemeldet.aufGeraet + gemeldet.weitere).ifEmpty {
            setOf(Locale.getDefault().toLanguageTag())
        }

        return codes
            .map { code ->
                // Hat das Gerät gar nichts gemeldet, ist „nicht auf dem
                // Gerät" eine **Behauptung**, keine Auskunft -- und auf dem
                // Gerät von Belkis war sie nachweislich falsch: Nibra zeigte
                // „Nicht auf dem Gerät", während dasselbe Gerät auf Nachfrage
                // de-DE als installiert meldete. Lieber unbekannt sagen als
                // etwas Falsches behaupten.
                baueSprache(
                    code = code,
                    aufGeraet = gemeldet.aufGeraet.contains(code),
                    verfuegbarkeitBekannt = !nichtsGemeldet,
                    anzeigeSprache = anzeigeSprache
                )
            }
            .distinctBy { it.code }
            .sortedBy { it.name.lowercase(anzeigeSprache) }
    }

    private data class Gemeldet(val aufGeraet: Set<String>, val weitere: Set<String>)

    private fun baueSprache(
        code: String,
        aufGeraet: Boolean,
        verfuegbarkeitBekannt: Boolean,
        anzeigeSprache: Locale
    ): Diktatsprache {
        val locale = Locale.forLanguageTag(code.replace('_', '-'))
        val name = locale.getDisplayName(anzeigeSprache).ifBlank { code }
        val eigen = locale.getDisplayName(locale).ifBlank { code }
        return Diktatsprache(
            code = code,
            name = name.replaceFirstChar { it.uppercase(anzeigeSprache) },
            eigenName = eigen.replaceFirstChar { it.uppercase(locale) },
            aufGeraetVerfuegbar = aufGeraet,
            verfuegbarkeitBekannt = verfuegbarkeitBekannt
        )
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun ueberUnterstuetzung(): Gemeldet =
        suspendCancellableCoroutine { fortsetzung ->
            val hauptfaden = Handler(Looper.getMainLooper())
            hauptfaden.post {
                // Über den einen Erkenner des Prozesses. Vorher legte diese
                // Abfrage einen zweiten an, während der Bedienungshilfen-
                // Dienst schon einen hielt -- und bekam nie eine Antwort.
                val erkenner = halter.leihe(ZWECK)
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
                                halter.gibZurueck(ZWECK)
                                if (fortsetzung.isActive) {
                                    fortsetzung.resume(Gemeldet(aufGeraet, weitere))
                                }
                            }

                            override fun onError(fehler: Int) {
                                halter.gibZurueck(ZWECK)
                                if (fortsetzung.isActive) {
                                    fortsetzung.resume(Gemeldet(emptySet(), emptySet()))
                                }
                            }
                        }
                    )
                }.onFailure {
                    halter.gibZurueck(ZWECK)
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
        const val ZWECK = "Sprachliste"

        /**
         * Wie lange auf die Auskunft des Geräts gewartet wird.
         *
         * Vorher vier Sekunden. Auf dem Gerät von Belkis kam in dieser Zeit
         * nichts an -- die App zeigte daraufhin eine einzige Sprache als
         * „nicht auf dem Gerät", während dasselbe Gerät auf direkte
         * Nachfrage de-DE und dreißig weitere meldete. Der erste Aufruf
         * weckt den Erkennungsdienst mit auf und braucht länger als spätere.
         *
         * Am Gerät gemessen: der A15 antwortet in 669 ms, das S23 Ultra
         * **überhaupt nicht**. Ein langes Fenster hilft dem einen nicht und
         * blockiert beim anderen den Erkenner -- wer in dieser Zeit auf die
         * Aufnahmefläche tippt, bekommt „Erkennung nicht verfügbar". Für
         * eine Diktier-App der schlechtestmögliche Moment zu versagen.
         *
         * Drei Sekunden sind reichlich für ein Gerät, das antwortet, und
         * kurz genug für eines, das schweigt.
         */
        const val ABFRAGE_ZEIT_MILLIS = 3_000L
    }
}
