package de.ithandwerkstuttgart.nibra.erkennung

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import java.util.concurrent.Executor
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

/** Was während einer Erkennung passiert. */
sealed interface Erkennungsereignis {
    /** Der Erkenner hört jetzt zu. */
    data object Hoert : Erkennungsereignis

    /** Neuer Pegel, bereits auf 0f..1f gebracht. */
    data class Pegel(val wert: Float) : Erkennungsereignis

    /** Der Sprecher hat aufgehört, der Erkenner wandelt noch. */
    data object Stille : Erkennungsereignis

    /** Zwischenstand, kann sich noch ändern. */
    data class Teiltext(val text: String) : Erkennungsereignis

    /**
     * Endgültiges Ergebnis. Danach folgt kein weiteres Ereignis.
     *
     * Trägt die n-beste Liste und, wo das Gerät sie meldet, die
     * Sicherheiten -- beides wurde früher verworfen.
     */
    data class Ergebnis(val ergebnis: Erkennungsergebnis) : Erkennungsereignis {
        /** Für Aufrufer ohne Alternativen -- die Sicherheit bleibt unbekannt. */
        constructor(text: String) : this(
            Erkennungsergebnis(listOf(Lesart(text, konfidenz = null)))
        )

        val text: String get() = ergebnis.text
    }

    /** Abbruch mit einem im Klartext erklärbaren Grund. */
    data class Fehlgeschlagen(val art: Fehlerart) : Erkennungsereignis
}

/**
 * Kapselt Androids [SpeechRecognizer]. Ab API 33 wird der reine
 * Geräte-Erkenner verwendet (`createOnDeviceSpeechRecognizer`), darunter
 * der normale Erkenner mit `EXTRA_PREFER_OFFLINE` (AUFTRAG.md, Nachtrag
 * "Spracherkennung -- Entscheidung"). Kein fremder Endpunkt, kein Schlüssel.
 */
@Singleton
class Spracherkenner @Inject constructor(
    private val context: Context,
    private val halter: Erkennerhalter
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
     * abgebrochen, hört der Erkenner sofort auf. Ein Stopp von außen
     * (Nutzer tippt auf "beenden") geschieht über [stoppen].
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
        // Manche Geräte melden ihre Sprache mit Erweiterungen
        // ("de-DE-u-fw-mon") oder kennen nur die Sprache ohne Land. Deshalb
        // der Reihe nach probieren, bevor ein Sprachfehler gemeldet wird.
        val kandidaten = sprachkandidaten(sprachCode)
        var kandidat = 0

        val zuhoerer = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Erkennungsprotokoll.rueckruf("onReadyForSpeech")
                trySend(Erkennungsereignis.Hoert)
            }

            override fun onBeginningOfSpeech() {
                // Es geht weiter: eine etwa gestellte Wache wird
                // zurückgenommen, damit sie kein laufendes Diktat abbricht.
                nimmWacheZurueck()
                Erkennungsprotokoll.rueckruf("onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                trySend(Erkennungsereignis.Pegel(pegelAus(rmsdB)))
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                Erkennungsprotokoll.rueckruf("onEndOfSpeech")
                trySend(Erkennungsereignis.Stille)
                // **Hier wird keine Wache gestellt.** `onEndOfSpeech` heißt
                // nur „gerade ist es still" -- danach darf weitergesprochen
                // werden, und beim Dauerdiktat passiert genau das. Eine
                // Wache an dieser Stelle schoss die laufende Erkennung ab,
                // sobald jemand länger als die Wachzeit nachdachte.
                //
                // Gewacht wird erst, wenn der Nutzer wirklich beendet hat --
                // siehe `stoppen()`.
            }

            override fun onError(error: Int) {
                Erkennungsprotokoll.rueckruf("onError", "code=$error")
                nimmWacheZurueck()
                val art = fehlerartAus(error)
                if (art == Fehlerart.SPRACHE_NICHT_AUF_GERAET && kandidat + 1 < kandidaten.size) {
                    kandidat += 1
                    hauptfaden.post {
                        // Ohne Erkenner gibt es keinen zweiten Versuch --
                        // und vor allem: ohne Meldung wartete der Fluss hier
                        // für immer. Ein stiller Ausgang ohne Ereignis ist
                        // genau das, was die Oberfläche festfahren lässt.
                        val laufender = erkenner
                        if (laufender == null) {
                            trySend(Erkennungsereignis.Fehlgeschlagen(art))
                            close()
                            return@post
                        }
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
                // Nur Anzahlen, nie der Text selbst.
                val anzahl = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.size
                Erkennungsprotokoll.rueckruf(
                    "onResults",
                    "lesarten=${anzahl?.toString() ?: "kein Schlüssel"}"
                )
                nimmWacheZurueck()
                val ergebnis = Erkennungsergebnis.aus(
                    texte = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
                    sicherheiten = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                )
                if (ergebnis.text.isBlank()) {
                    trySend(Erkennungsereignis.Fehlgeschlagen(Fehlerart.NICHTS_VERSTANDEN))
                } else {
                    trySend(Erkennungsereignis.Ergebnis(ergebnis))
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

        // Die Wache gehört in den Fluss, denn nur hier lässt sich senden und
        // schließen. Sie wird von zwei Seiten gestellt: von `onEndOfSpeech`
        // und von `stoppen()` -- beides bedeutet „der Erkenner wertet jetzt
        // aus". Meldet er sich nicht, endet der Fluss trotzdem.
        //
        // Sie ersetzt nicht die Ursachensuche. Sie sorgt dafür, dass ein
        // Ausbleiben nie wieder unbegrenzt bestehen kann.
        wacheStellen = {
            hauptfaden.removeCallbacks(wacheLaeuft ?: Runnable {})
            val neueWache = Runnable {
                Erkennungsprotokoll.rueckruf("WACHE greift", "kein Ergebnis nach dem Stoppen")
                trySend(Erkennungsereignis.Fehlgeschlagen(Fehlerart.KEIN_ERGEBNIS))
                close()
            }
            wacheLaeuft = neueWache
            hauptfaden.postDelayed(neueWache, ERGEBNIS_GRENZE_MILLIS)
        }

        hauptfaden.post {
            // Ein einmal gebauter Erkenner wird wiederverwendet.
            //
            // Früher entstand je Satz ein neuer und der alte wurde
            // zerstört. Beim Dauerdiktat heißt das nach jedem Satz:
            // zerstören, neu bauen, an den Systemdienst binden, wieder
            // zuhören -- und in dieser Zeit hört niemand zu. Wer ohne
            // Pause weiterspricht, verliert den Anfang des nächsten Satzes.
            //
            // Ausdrücklich: das verkleinert die Lücke, es beseitigt sie
            // nicht. Wie groß sie noch ist, lässt sich erst messen, wenn
            // Nibra den Ton selbst aufnimmt -- heute besitzt sie ihn nicht.
            // Über den einen Erkenner des Prozesses. Ein zweiter, während
            // ein anderer lebt, bekommt vom Systemdienst keine Antwort.
            val bereiter = halter.leihe(ZWECK_DIKTAT)
            if (bereiter == null) {
                trySend(Erkennungsereignis.Fehlgeschlagen(Fehlerart.ERKENNUNG_NICHT_VERFUEGBAR))
                close()
                return@post
            }
            erkenner = bereiter
            laufender = bereiter
            bereiter.setRecognitionListener(zuhoerer)
            Erkennungsprotokoll.aufruf("startListening", "sprache=${kandidaten[kandidat]}")
            runCatching { bereiter.startListening(absicht(kandidaten[kandidat], stoppBeiStille)) }
                .onFailure {
                    // Ein gehaltener Erkenner kann in einen unbrauchbaren
                    // Zustand geraten. Dann wird er weggeworfen, damit der
                    // nächste Versuch mit einem frischen beginnt.
                    verwirfGehaltenen()
                    trySend(Erkennungsereignis.Fehlgeschlagen(Fehlerart.UNBEKANNT))
                    close()
                }
        }

        awaitClose {
            nimmWacheZurueck()
            wacheStellen = null
            hauptfaden.post {
                val zuBeenden = erkenner ?: return@post
                // Nur abbrechen, nicht zerstören -- der nächste Satz
                // benutzt denselben Erkenner weiter. Zerstört wird er in
                // `gib frei`, wenn das Diktat wirklich zu Ende ist.
                runCatching { zuBeenden.cancel() }
                runCatching { zuBeenden.setRecognitionListener(null) }
                if (laufender === zuBeenden) laufender = null
            }
        }
    }

    /**
     * Beendet die laufende Aufnahme und lässt den Erkenner das bereits
     * Gesprochene noch auswerten -- anders als ein Abbruch des Flusses.
     */
    override fun stoppen() {
        Handler(Looper.getMainLooper()).post {
            Erkennungsprotokoll.aufruf("stopListening")
            runCatching { laufender?.stopListening() }
            // Auch hier: ab jetzt wertet der Erkenner aus. `onEndOfSpeech`
            // kommt nicht in jedem Fall -- etwa wenn gar nicht gesprochen
            // wurde. Deshalb wird die Wache von beiden Seiten gestellt.
            wacheStellen?.invoke()
        }
    }

    /** Stellt die Wache. Wird vom laufenden Fluss gesetzt. */
    @Volatile
    private var wacheStellen: (() -> Unit)? = null

    /** Die gestellte Wache, damit sie zurückgenommen werden kann. */
    @Volatile
    private var wacheLaeuft: Runnable? = null

    private fun nimmWacheZurueck() {
        val gestellt = wacheLaeuft ?: return
        wacheLaeuft = null
        Handler(Looper.getMainLooper()).removeCallbacks(gestellt)
    }

    @Volatile
    private var laufender: SpeechRecognizer? = null


    /**
     * Gibt den gehaltenen Erkenner frei.
     *
     * Muss aufgerufen werden, wenn das Diktat endet oder der Dienst geht --
     * sonst hält der Systemdienst eine Bindung, die niemand mehr braucht.
     */
    override fun gibFrei() {
        Handler(Looper.getMainLooper()).post { verwirfGehaltenen() }
    }

    private fun verwirfGehaltenen() {
        laufender = null
        halter.schliesse()
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
                // Android setzt Satzzeichen und Großschreibung selbst, wenn
                // man es darum bittet -- ohne das kommt reiner Kleintext ohne
                // Punkt und Komma an.
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
                )
                // Zwischenstände ohne wackelndes Satzzeichen am Ende.
                putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
            }
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Die Stillezeiten werden **immer** gesetzt, nicht nur ohne
            // "Stopp bei Stille". Früher ging im Regelfall kein einziger
            // dieser Werte an den Erkenner, und es galt, was der Hersteller
            // voreingestellt hat -- üblicherweise ein bis zwei Sekunden.
            // Das schnitt Sätze ab, sobald jemand kurz nachdachte.
            //
            // Android darf diese Angaben ignorieren, und viele Geräte tun
            // das. Sie zu setzen ist eine Bitte, keine Zusage -- aber eine
            // ausgesprochene Bitte ist besser als gar keine.
            val vollstaendigeStille =
                if (stoppBeiStille) STILLE_FERTIG_MILLIS else LANGE_STILLE_MILLIS
            val moeglicheStille =
                if (stoppBeiStille) STILLE_DENKPAUSE_MILLIS else LANGE_STILLE_MILLIS
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                vollstaendigeStille
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                moeglicheStille
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                MINDESTDAUER_MILLIS
            )
        }

    /**
     * Bittet Android, das Sprachpaket für [sprachCode] auf das Gerät zu
     * holen. Das ist der einzige Vorgang, der überhaupt Netz berührt --
     * und er läuft im Erkennungsdienst des Systems, nicht in Nibra
     * (AUFTRAG.md, Antwort 4).
     */
    /**
     * Holt das Sprachpaket und meldet, wie weit es ist.
     *
     * Ab Android 14 gibt Android echten Fortschritt zurück. Darunter
     * (Android 13) lässt sich das Laden nur anstoßen -- dann meldet der
     * Fluss [Ladestand.Angestossen] und endet, statt einen Fortschritt
     * vorzutäuschen, den niemand kennt.
     */
    override fun ladeSprachpaket(sprachCode: String): Flow<Ladestand> = callbackFlow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            trySend(Ladestand.NurUeberEinstellungen)
            close()
            return@callbackFlow
        }
        val hauptfaden = Handler(Looper.getMainLooper())
        var lader: SpeechRecognizer? = null
        hauptfaden.post {
            val erkenner = halter.leihe(ZWECK_LADEN)
            if (erkenner == null) {
                trySend(Ladestand.Fehlgeschlagen(null))
                close()
                return@post
            }
            lader = erkenner
            val absicht = absicht(sprachCode, stoppBeiStille = true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatching {
                    erkenner.triggerModelDownload(
                        absicht,
                        Executor { befehl -> hauptfaden.post(befehl) },
                        object : ModelDownloadListener {
                            override fun onScheduled() {
                                trySend(Ladestand.Angestossen)
                            }

                            override fun onProgress(anteil: Int) {
                                trySend(Ladestand.Laeuft(anteil.coerceIn(0, 100)))
                            }

                            override fun onSuccess() {
                                trySend(Ladestand.Fertig)
                                close()
                            }

                            override fun onError(grund: Int) {
                                trySend(Ladestand.Fehlgeschlagen(grund))
                                close()
                            }
                        }
                    )
                }.onFailure {
                    trySend(Ladestand.Fehlgeschlagen(null))
                    close()
                }
            } else {
                // Android 13 kennt nur den Anstoß ohne Rückmeldung. Einen
                // Fortschritt zu zeigen, den niemand kennt, wäre gelogen.
                runCatching { erkenner.triggerModelDownload(absicht) }
                    .onSuccess { trySend(Ladestand.Angestossen) }
                    .onFailure { trySend(Ladestand.Fehlgeschlagen(null)) }
                close()
            }
        }
        awaitClose {
            hauptfaden.post { halter.gibZurueck(ZWECK_LADEN) }
        }
    }

    fun ladeSprachmodell(sprachCode: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        Handler(Looper.getMainLooper()).post {
            val erkenner = halter.leihe(ZWECK_ANSTOSS) ?: return@post
            runCatching {
                erkenner.triggerModelDownload(absicht(sprachCode, stoppBeiStille = true))
            }
            // Der Anstoß läuft im Systemdienst weiter; die Hülle hier
            // wird kurz danach freigegeben, damit nichts leckt.
            Handler(Looper.getMainLooper()).postDelayed(
                { halter.gibZurueck(ZWECK_ANSTOSS) },
                ANSTOSS_HALTEZEIT_MILLIS
            )
        }
    }

    /**
     * Reihenfolge der Versuche: der gewünschte Code ohne Erweiterungen,
     * dann Sprache mit Land, dann nur die Sprache, zuletzt ohne Angabe --
     * dann wählt der Erkenner selbst.
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
        const val ZWECK_DIKTAT = "Diktat"
        const val ZWECK_ANSTOSS = "Nachladen anstoßen"
        const val ZWECK_LADEN = "Sprachpaket laden"

        /**
         * Wie lange nach dem Auswertungsbeginn höchstens auf ein Ergebnis
         * gewartet wird.
         *
         * Sie läuft **nur** nach `stopListening()` -- also wenn der Nutzer
         * das Diktat beendet hat und nur noch die Auswertung aussteht.
         * Früher wurde sie schon bei `onEndOfSpeech` gestellt; das war
         * falsch und hat laufende Diktate abgeschossen, sobald jemand
         * länger nachdachte.
         *
         * **Nicht gemessen.** Ein Erkenner auf dem Gerät braucht
         * erfahrungsgemäß deutlich unter drei Sekunden; fünfzehn lassen
         * auch einem langsamen Gerät mit langem Diktat reichlich Luft.
         *
         * Kürzer als die Wache im Ansichtsmodell (12 s): der Erkenner soll
         * zuerst antworten dürfen, das Modell ist nur das Netz darunter.
         */
        const val ERGEBNIS_GRENZE_MILLIS = 15_000L

        const val ANSTOSS_HALTEZEIT_MILLIS = 5_000L
        const val LANGE_STILLE_MILLIS = 600_000L
        const val MINDESTDAUER_MILLIS = 1_000L

        /**
         * Stille, nach der das Diktat als beendet gilt.
         *
         * 2000 ms statt der üblichen Herstellervoreinstellung von etwa
         * 1000. Wer einen Satz formuliert, macht mitten darin Pausen; eine
         * Sekunde schneidet regelmäßig ab. Der Wert ist gesetzt und nicht
         * gemessen -- messen lässt er sich erst, wenn wir eigenes Audio
         * haben.
         */
        const val STILLE_FERTIG_MILLIS = 2_000L

        /**
         * Stille, nach der das Diktat *möglicherweise* beendet ist. Der
         * Erkenner darf hier schon rechnen, aber noch nicht abschließen.
         */
        const val STILLE_DENKPAUSE_MILLIS = 1_200L

        /** Der Erkenner meldet etwa -2 dB (Stille) bis 10 dB (laut). */
        fun pegelAus(rmsdB: Float): Float = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)

        fun fehlerartAus(fehler: Int): Fehlerart = when (fehler) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Fehlerart.KEIN_MIKROFON_RECHT
            // Getrennt, weil es zwei verschiedene Dinge sind: NO_MATCH
            // heißt "ich habe etwas gehört und nicht zuordnen können",
            // SPEECH_TIMEOUT heißt "es kam nichts". Nur das zweite ist
            // eine Pause.
            SpeechRecognizer.ERROR_NO_MATCH -> Fehlerart.NICHTS_VERSTANDEN
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Fehlerart.NICHTS_GEHOERT
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
