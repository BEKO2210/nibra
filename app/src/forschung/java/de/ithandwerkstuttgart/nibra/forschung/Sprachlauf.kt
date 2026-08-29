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
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import de.ithandwerkstuttgart.nibra.daten.EinstellungenAblage
import kotlinx.coroutines.flow.first
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Der kontrollierte Sprachlauf.
 *
 * Zweimal derselbe Ablauf mit demselben gesprochenen Text -- einmal mit dem
 * Erkenner allein, einmal mit einer eigenen Aufnahme daneben. Nur so lässt
 * sich sagen, ob der Nebenlauf die Erkennung verschlechtert; alles andere
 * wäre ein Eindruck.
 *
 * **Beide Läufe haben denselben Zeitplan**, auch der erste, in dem gar nicht
 * aufgenommen wird. Sonst wäre die Sprechdauer der Unterschied und nicht der
 * Nebenlauf.
 *
 * Der Zeitplan ist zugleich so gebaut, dass er die offene Frage nach dem
 * Pegelsprung beantwortet -- es gibt **vier** vergleichbare Abschnitte:
 *
 * ```
 *  0 s ................ eigene Aufnahme läuft, Erkenner aus, Stille
 *  3 s ................ Erkenner startet, weiterhin Stille
 *  5 s ................ Sprache
 * 35 s ................ Erkenner endet, wieder Stille
 * 38 s ................ Ende
 * ```
 *
 * Springt der Pegel bei 3 s und fällt bei 35 s zurück, hat der Erkenner den
 * Aufnahmepfad umgeschaltet. Ist es nur ein einzelnes Fach, war es ein
 * Startton. Springt er erst bei 5 s, war es schlicht die Stimme.
 */
class Sprachlauf(
    private val zusammenhang: Context,
    /** Die Diktatsprache. Bestimmt den Bezugstext -- siehe [bezugstextFuer]. */
    private val sprachCode: String,
    private val aufStand: (Stand) -> Unit
) {

    /** Der Text, der in diesem Lauf vorgelesen wird. */
    val bezugstext: String get() = bezugstextFuer(sprachCode)

    /** Was gerade läuft -- die Oberfläche zeigt genau das an. */
    data class Stand(
        val lauf: String,
        val anweisung: String,
        val sprechen: Boolean,
        val restSekunden: Int,
        /**
         * Der Prüfsatz, den der Bildschirm zeigen soll.
         *
         * **Dieselbe Sache, die die Auswertung bewertet** -- nicht eine
         * Kopie davon. Steht hier `null`, zeigt die Anzeige den festen Text
         * des alten Sprachlaufs; das gilt nur für ihn selbst.
         */
        val testfall: Testfall? = null
    )

    data class Abschnittsbefund(
        val nummer: Int,
        val startMillis: Long,
        val bereitMillis: Long?,
        val spracheBeginnMillis: Long?,
        val ersterTeiltextMillis: Long?,
        val spracheEndeMillis: Long?,
        val ergebnisMillis: Long?,
        val lesarten: List<Lesart>,
        /** Der zuletzt gesehene Zwischenstand -- die Rettung bei leerem Ende. */
        val letzterZwischenstand: String?,
        val fehler: Int?
    ) {
        data class Lesart(val text: String, val konfidenz: Float?)

        /**
         * Was die App aus diesem Abschnitt gemacht hätte: das Endergebnis,
         * und wenn das leer war, der letzte Zwischenstand. Genau so rettet
         * es der Spracherkenner seit dem Fund vom 28.08.2026.
         */
        val text: String
            get() = lesarten.firstOrNull()?.text?.takeIf { it.isNotBlank() }
                ?: letzterZwischenstand.orEmpty()

        /** Wahr, wenn nur der Zwischenstand geblieben ist. */
        val nurGerettet: Boolean
            get() = lesarten.firstOrNull()?.text.isNullOrBlank() &&
                !letzterZwischenstand.isNullOrBlank()

        /** Zeit vom Start des Abschnitts bis zum ersten sichtbaren Wort. */
        val bisErstemTeiltext: Long? get() = ersterTeiltextMillis?.minus(startMillis)

        /** Zeit vom Start des Abschnitts bis zum endgültigen Ergebnis. */
        val bisErgebnis: Long? get() = ergebnisMillis?.minus(startMillis)
    }

    data class Erkennerprotokoll(
        val abschnitte: List<Abschnittsbefund>,
        val ereignisse: List<String>
    ) {
        val volltext: String
            get() = abschnitte.mapNotNull { it.text.ifBlank { null } }.joinToString(" ")

        /**
         * Die Lücken zwischen den Abschnitten: von einem Ergebnis bis zu dem
         * Zeitpunkt, an dem der Erkenner im nächsten Abschnitt wieder Sprache
         * bemerkt. In dieser Zeit hört niemand zu -- was hier gesagt wird,
         * ist verloren.
         */
        fun lücken(): List<Long> = abschnitte.zipWithNext().mapNotNull { (vorher, danach) ->
            val ende = vorher.ergebnisMillis ?: return@mapNotNull null
            val wieder = danach.spracheBeginnMillis ?: danach.bereitMillis
            ?: return@mapNotNull null
            wieder - ende
        }

        /**
         * Die technische Neustartlücke: vom endgültigen Ergebnis eines
         * Abschnitts bis zu dem Augenblick, in dem der Erkenner wieder
         * aufnahmebereit meldet.
         *
         * Das ist das Fenster, in dem gesprochene Wörter tatsächlich ins
         * Leere gehen. Es ist etwas anderes als [lücken] -- dort steckt auch
         * die Zeit drin, in der schlicht niemand gesprochen hat.
         */
        fun neustartlücken(): List<Long> = abschnitte.zipWithNext().mapNotNull { (vorher, danach) ->
            val ende = vorher.ergebnisMillis ?: return@mapNotNull null
            val bereit = danach.bereitMillis ?: return@mapNotNull null
            bereit - ende
        }
    }

    data class Ergebnis(
        val sprachCode: String,
        val bezugstext: String,
        val erkennerAllein: Erkennerprotokoll,
        val erkennerNebenlauf: Erkennerprotokoll,
        val verlauf: Pegelverlauf,
        val quelle: String,
        val abtastrate: Int,
        val aktiveMikrofone: List<String>,
        val aufnahmefehler: String?
    )

    private val hauptfaden = Handler(Looper.getMainLooper())

    fun fuehreDurch(): Ergebnis {
        val allein = einLauf("1 von 2: Erkenner allein", null)
        pause("Kurz durchatmen", PAUSE_MS)
        val verlauf = Pegelverlauf(ABTASTRATE)
        val mitAufnahme = MitAufnahme(verlauf)
        val nebenlauf = einLauf("2 von 2: Erkenner + eigene Aufnahme", mitAufnahme)
        return Ergebnis(
            sprachCode = sprachCode,
            bezugstext = bezugstext,
            erkennerAllein = allein,
            erkennerNebenlauf = nebenlauf,
            verlauf = verlauf,
            quelle = "VOICE_RECOGNITION",
            abtastrate = ABTASTRATE,
            aktiveMikrofone = mitAufnahme.aktiveMikrofone,
            aufnahmefehler = mitAufnahme.fehler
        )
    }

    // ---------------------------------------------------------------- Ablauf

    private fun einLauf(name: String, aufnahme: MitAufnahme?): Erkennerprotokoll {
        aufnahme?.starte()

        zaehleHerunter(name, "Gleich geht es los. Bitte noch nicht sprechen.", false, VORLAUF_MS)

        aufnahme?.verlauf?.merke("Erkenner startet")
        val läuft = AtomicBoolean(true)
        val sammler = Erkennersitzung()
        val faden = Thread { sammler.laufe(läuft) }
        faden.start()

        zaehleHerunter(name, "Erkenner läuft. Bitte noch immer nicht sprechen.", false, ERKENNER_VORLAUF_MS)
        aufnahme?.verlauf?.merke("Sprechen beginnt")
        zaehleHerunter(name, "Jetzt den Text vorlesen -- ohne Pausen.", true, SPRECHDAUER_MS)

        läuft.set(false)
        sammler.brichAb()
        faden.join(5_000)
        aufnahme?.verlauf?.merke("Erkenner beendet")

        if (aufnahme != null) {
            zaehleHerunter(name, "Danke. Bitte nicht mehr sprechen.", false, NACHLAUF_MS)
            aufnahme.halteAn()
        }
        return sammler.protokoll()
    }

    private fun pause(text: String, dauer: Long) =
        zaehleHerunter("Pause", text, false, dauer)

    private fun zaehleHerunter(lauf: String, text: String, sprechen: Boolean, dauer: Long) {
        val bis = SystemClock.elapsedRealtime() + dauer
        while (true) {
            val rest = bis - SystemClock.elapsedRealtime()
            if (rest <= 0) break
            aufStand(Stand(lauf, text, sprechen, ((rest + 999) / 1000).toInt()))
            Thread.sleep(minOf(rest, 200))
        }
    }

    // ------------------------------------------------------------- Aufnahme

    /**
     * Die eigene Aufnahme, die im zweiten Lauf neben dem Erkenner liegt.
     *
     * Sie startet **vor** dem Erkenner und endet **nach** ihm -- nur deshalb
     * gibt es die stillen Abschnitte davor und danach, an denen sich ein
     * umgeschalteter Aufnahmepfad ablesen lässt.
     */
    private inner class MitAufnahme(val verlauf: Pegelverlauf) {
        var aktiveMikrofone: List<String> = emptyList()
        var fehler: String? = null
        private val läuft = AtomicBoolean(false)
        private var faden: Thread? = null

        fun starte() {
            läuft.set(true)
            faden = Thread {
                var aufnahme: AudioRecord? = null
                try {
                    val kleinste = AudioRecord.getMinBufferSize(
                        ABTASTRATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                    )
                    aufnahme = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION, ABTASTRATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        kleinste * 4
                    )
                    if (aufnahme.state != AudioRecord.STATE_INITIALIZED) {
                        fehler = "AudioRecord nicht bereit"
                        return@Thread
                    }
                    aufnahme.startRecording()
                    verlauf.merke("Aufnahme läuft")
                    val block = ShortArray(kleinste / 2)
                    var ersterBlock = true
                    while (läuft.get()) {
                        val n = aufnahme.read(block, 0, block.size)
                        if (n <= 0) {
                            fehler = "read lieferte $n"
                            break
                        }
                        verlauf.nimm(block, n)
                        // Die Uhr erst nach dem ersten Block starten: davor
                        // liegt die Anlaufzeit des Geräts, die kein Verlust
                        // ist und die Messung sonst verfälschen würde.
                        if (ersterBlock) {
                            verlauf.beginneZeitmessung(SystemClock.elapsedRealtime())
                            ersterBlock = false
                        }
                    }
                    verlauf.beendeZeitmessung(SystemClock.elapsedRealtime())
                    aktiveMikrofone = Mikrofonbefund.aktiveMikrofone(aufnahme)
                } catch (ausnahme: Throwable) {
                    fehler = "${ausnahme.javaClass.simpleName} ${ausnahme.message}"
                } finally {
                    runCatching { aufnahme?.stop() }
                    runCatching { aufnahme?.release() }
                    verlauf.schliesseAb()
                }
            }.also { it.start() }
        }

        fun halteAn() {
            läuft.set(false)
            faden?.join(3_000)
        }
    }

    // ------------------------------------------------------------- Erkenner

    /**
     * Hält den Erkenner über den ganzen Lauf am Leben und startet ihn neu,
     * sobald er einen Abschnitt abgeschlossen hat.
     *
     * Der Erkenner beendet sich von sich aus, sobald er meint, ein Satz sei
     * vorbei. Wer 30 Sekunden am Stück diktiert, braucht deshalb mehrere
     * Abschnitte -- und genau die Lücken dazwischen sind das, was der Nutzer
     * später als verschluckte Wörter erlebt.
     */
    private inner class Erkennersitzung {
        private val abschnitte = mutableListOf<Abschnittsbefund>()
        private val ereignisse = mutableListOf<String>()
        private var erkenner: SpeechRecognizer? = null
        private var nullpunkt = 0L
        private var laufend: Bau? = null
        private var fertig: CountDownLatch? = null

        private inner class Bau(val nummer: Int, val startMillis: Long) {
            var bereit: Long? = null
            var spracheBeginn: Long? = null
            var ersterTeiltext: Long? = null
            var letzterZwischenstand: String? = null
            var spracheEnde: Long? = null
            var ergebnis: Long? = null
            var lesarten: List<Abschnittsbefund.Lesart> = emptyList()
            var fehler: Int? = null

            fun fertigStellen() = Abschnittsbefund(
                nummer, startMillis, bereit, spracheBeginn, ersterTeiltext,
                spracheEnde, ergebnis, lesarten, letzterZwischenstand, fehler
            )
        }

        private fun jetzt() = SystemClock.elapsedRealtime() - nullpunkt

        private fun notiere(was: String) {
            ereignisse += "%6d ms  %s".format(jetzt(), was)
        }

        fun laufe(läuft: AtomicBoolean) {
            nullpunkt = SystemClock.elapsedRealtime()
            val bereit = CountDownLatch(1)
            hauptfaden.post {
                erkenner = erzeuge()?.also { es -> es.setRecognitionListener(zuhörer()) }
                bereit.countDown()
            }
            bereit.await(5, TimeUnit.SECONDS)
            if (erkenner == null) {
                notiere("Erkenner nicht erzeugbar")
                return
            }

            var nummer = 1
            // Bricht ab, wenn der Erkenner nur noch Fehler liefert. Am A15
            // gemessen: 1083 mal RECOGNIZER_BUSY in 35 Sekunden, 1106
            // Abschnitte, ein Bericht von 12810 Zeilen. Eine Messstrecke,
            // die im Kreis läuft, misst nichts und verdeckt die Ursache.
            var fehlerHintereinander = 0
            while (läuft.get() && fehlerHintereinander < FEHLER_ABBRUCH) {
                val bau = Bau(nummer, jetzt())
                laufend = bau
                val warten = CountDownLatch(1)
                fertig = warten
                notiere("Abschnitt $nummer: startListening")
                hauptfaden.post { runCatching { erkenner?.startListening(absicht()) } }
                // Großzügig warten: der Abschnitt endet normalerweise von
                // selbst. Läuft er in die Obergrenze, ist auch das ein Befund.
                warten.await(ABSCHNITT_GRENZE_MS, TimeUnit.MILLISECONDS)
                val fertigerAbschnitt = bau.fertigStellen()
                abschnitte += fertigerAbschnitt
                fehlerHintereinander =
                    if (fertigerAbschnitt.fehler != null &&
                        fertigerAbschnitt.lesarten.isEmpty()
                    ) {
                        fehlerHintereinander + 1
                    } else {
                        0
                    }
                if (fehlerHintereinander >= FEHLER_ABBRUCH) {
                    notiere("$FEHLER_ABBRUCH Fehler hintereinander, Lauf abgebrochen")
                }
                laufend = null
                nummer++
            }
            hauptfaden.post { runCatching { erkenner?.destroy() }; erkenner = null }
        }

        fun brichAb() {
            hauptfaden.post { runCatching { erkenner?.stopListening() } }
            fertig?.countDown()
        }

        fun protokoll() = Erkennerprotokoll(abschnitte.toList(), ereignisse.toList())

        private fun erzeuge(): SpeechRecognizer? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(zusammenhang)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(zusammenhang)
            } else {
                SpeechRecognizer.createSpeechRecognizer(zusammenhang)
            }
        }.getOrNull()

        private fun absicht() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sprachCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
            // Großzügige Endpunkterkennung: der Lauf beginnt mit zwei
            // Sekunden Stille, die er nicht als Satzende deuten soll.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10_000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                2_000
            )
        }

        private fun zuhörer() = object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {
                laufend?.bereit = jetzt(); notiere("bereit")
            }

            override fun onBeginningOfSpeech() {
                // Nur das **erste** Mal festhalten. Der Erkenner meldet
                // während eines Abschnitts mehrfach Sprachbeginn; wer den
                // Wert jedes Mal überschreibt, misst am Ende den letzten
                // Atemzug statt des Einsatzes.
                laufend?.let { if (it.spracheBeginn == null) it.spracheBeginn = jetzt() }
                notiere("Sprache beginnt")
            }

            override fun onRmsChanged(rms: Float) = Unit
            override fun onBufferReceived(b: ByteArray?) = notiere("Puffer ${b?.size ?: 0} B")

            override fun onEndOfSpeech() {
                laufend?.spracheEnde = jetzt(); notiere("Sprache endet")
            }

            override fun onError(code: Int) {
                laufend?.fehler = code
                notiere("Fehler $code")
                fertig?.countDown()
            }

            override fun onResults(werte: Bundle?) {
                laufend?.let { bau ->
                    bau.ergebnis = jetzt()
                    bau.lesarten = lies(werte)
                }
                notiere("Ergebnis: ${lies(werte).firstOrNull()?.text.orEmpty()}")
                fertig?.countDown()
            }

            override fun onPartialResults(werte: Bundle?) {
                val bau = laufend ?: return
                val stand = lies(werte).firstOrNull()?.text?.takeIf { it.isNotBlank() }
                // **Jeden** Zwischenstand behalten, nicht nur den ersten
                // vermerken. Er ist die Rettung, wenn das Endergebnis leer
                // bleibt -- und ein Messwerkzeug, das diese Rettung nicht
                // mitmisst, misst nicht mehr, was die App tut.
                if (stand != null) bau.letzterZwischenstand = stand
                if (bau.ersterTeiltext == null) {
                    bau.ersterTeiltext = jetzt()
                    notiere("erster Teiltext: ${stand.orEmpty()}")
                }
            }

            override fun onEvent(art: Int, p: Bundle?) = Unit
        }

        private fun lies(werte: Bundle?): List<Abschnittsbefund.Lesart> {
            val texte = werte?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val sicher = werte?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            return texte.filter { it.isNotBlank() }.mapIndexed { stelle, text ->
                Abschnittsbefund.Lesart(text, sicher?.getOrNull(stelle)?.takeIf { it in 0f..1f })
            }
        }
    }

    companion object {
        /**
         * Der Text, der in beiden Läufen gesprochen wird.
         *
         * Enthält absichtlich Eigennamen und Zahlwörter -- an ihnen bricht
         * Erkennung zuerst, und sie sind der Teil, den ein Nutzer am
         * unangenehmsten nachbessert.
         */
        /**
         * Die Diktatsprache, wie **Nibra** sie eingestellt hat.
         *
         * Nicht die Systemsprache. Am A15 gemessen: dort steht das System
         * auf en-CA, in Nibra aber de-DE. Der Lauf nahm den englischen
         * Bezugstext, es wurde deutsch vorgelesen -- und nichts passte
         * zusammen. Ein Messwerkzeug, das die eigene Einstellung nicht
         * kennt, misst am Ziel vorbei.
         */
        suspend fun diktatsprache(ablage: EinstellungenAblage): String =
            ablage.fluss.first().diktatSprachCode.ifBlank {
                java.util.Locale.getDefault().toLanguageTag()
            }

        /**
         * Der Bezugstext zur Diktatsprache.
         *
         * Ein Messlauf, dessen Text nicht zur eingestellten Sprache passt,
         * misst nichts -- er erzeugt nur NO_MATCH. Auf dem Emulator liegt
         * en-US, auf den Testgeräten de-DE; beide sollen messbar sein.
         */
        fun bezugstextFuer(sprachCode: String): String =
            if (sprachCode.startsWith("de", ignoreCase = true)) BEZUGSTEXT
            else BEZUGSTEXT_EN

        /**
         * Englische Entsprechung -- inhaltlich gleich aufgebaut: Eigennamen,
         * Zahlwörter, eine Uhrzeit. An denselben Stellen bricht Erkennung.
         */
        const val BEZUGSTEXT_EN =
            "Good morning, this is Belkis Aslani calling from Freiberg. " +
                "I am testing the speech recognition of Nibra on two devices. " +
                "The meeting starts at half past two in conference room three. " +
                "Please tell doctor Weinreich that the delivery of " +
                "two hundred and forty parts will not arrive before Friday. " +
                "The invoice for eight hundred euros has already been paid. " +
                "We will talk about it again tomorrow, once all documents " +
                "have been checked."

        const val BEZUGSTEXT =
            "Guten Morgen, hier spricht Belkis Aslani aus Freiberg am Neckar. " +
                "Ich teste heute die Spracherkennung von Nibra auf zwei Geräten. " +
                "Die Besprechung beginnt um vierzehn Uhr dreißig im Konferenzraum drei. " +
                "Bitte richte Herrn Doktor Weinreich aus, dass die Lieferung von " +
                "zweihundertvierzig Bauteilen erst am Freitag eintrifft. " +
                "Die Rechnung über achthundert Euro ist bereits bezahlt. " +
                "Wir sprechen morgen noch einmal darüber, sobald alle Unterlagen " +
                "vollständig geprüft sind."

        const val ABTASTRATE = 48_000
        const val VORLAUF_MS = 3_000L
        const val ERKENNER_VORLAUF_MS = 2_000L
        const val SPRECHDAUER_MS = 30_000L
        const val NACHLAUF_MS = 3_000L
        const val PAUSE_MS = 8_000L
        const val ABSCHNITT_GRENZE_MS = 40_000L

        /**
         * Nach so vielen Fehlern hintereinander bricht der Lauf ab.
         *
         * Ohne diese Grenze drehte die Schleife durch: 1083 mal
         * RECOGNIZER_BUSY, 1106 Abschnitte, ein Bericht von 12810 Zeilen --
         * und die eigentliche Ursache ging darin unter.
         */
        const val FEHLER_ABBRUCH = 5
    }
}
