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
import kotlin.concurrent.thread

/**
 * Gate 4: **Wie lange dauert es, bis der Nutzer etwas sieht?**
 *
 * Diktat wird nicht an der Fehlerrate gemessen, sondern am Gefühl. Wer
 * spricht und zwei Sekunden lang nichts sieht, spricht langsamer, wartet,
 * verliert den Faden -- selbst wenn danach jedes Wort richtig ist.
 *
 * Sieben Zeitpunkte je Lauf:
 *
 * ```
 * t0  startListening gerufen
 * t1  onReadyForSpeech       -- der Dienst ist bereit
 * t2  erstes Byte im Rohr    -- ab hier fließt Ton
 * t3  onBeginningOfSpeech    -- der Dienst hört Sprache
 * t4  erster Zwischenstand   -- der Nutzer sieht das erste Wort
 * t5  erstes Segment         -- der erste bestätigte Abschnitt
 * t6  Sitzungsende
 * ```
 *
 * Die Zahl, um die es geht, ist **t2 bis t4**: von „ich rede" bis „ich
 * sehe etwas". Alles davor ist Vorbereitung, die sich verstecken lässt;
 * t2 bis t4 sieht der Nutzer.
 *
 * Der Ton kommt wieder aus einer Aufnahme. Beim Vorlesen wäre der
 * Sprechbeginn selbst um Hunderte Millisekunden unsicher -- und die
 * gesuchte Größe liegt in derselben Größenordnung. Man kann nicht mit
 * einem Maßstab messen, der so ungenau ist wie das Gemessene.
 */
class Verzugsversuch(
    private val zusammenhang: Context,
    /**
     * Welche Sprache der Erkenner verwenden soll.
     *
     * Nicht fest verdrahtet: das Pixel 9 hat nur en-US auf dem Gerät, und
     * eine Messung mit de-DE liefert dort schlicht nichts. Ein fest
     * eingebautes de-DE hätte das als Versagen der Strecke gelesen --
     * dabei fehlt nur das Sprachmodell.
     */
    private val sprache: String = "de-DE",
    private val aufStand: (String) -> Unit
) {

    data class Lauf(
        val nummer: Int,
        /** t0 bis t1: Aufnahme angefordert bis AudioRecord liefert. */
        val t0bis1AufnahmeMillis: Long?,
        val t1BereitschaftMillis: Long?,
        val t3SprachbeginnMillis: Long?,
        val t4ErsterStandMillis: Long?,
        val t5ErstesSegmentMillis: Long?,
        val t6EndeMillis: Long?,
        /** t7: letztes eingespeistes Sprachsample, ab t2. */
        val t7SprachendeMillis: Long?,
        /** t8: bestätigter Text, ab t2. */
        val t8BestaetigtMillis: Long?,
        /** t9: alles freigegeben, ab t2. */
        val t9FreigegebenMillis: Long?,
        val fehler: Int?
    ) {
        /** Vom Ende des Sprechens bis der Text feststeht. */
        val sprachendeBisTextMillis: Long?
            get() = if (t8BestaetigtMillis == null || t7SprachendeMillis == null) null
            else t8BestaetigtMillis - t7SprachendeMillis

        /** Vom Ende des Sprechens bis alles freigegeben ist. */
        val sprachendeBisFreiMillis: Long?
            get() = if (t9FreigegebenMillis == null || t7SprachendeMillis == null) null
            else t9FreigegebenMillis - t7SprachendeMillis
    }

    private val hauptfaden = Handler(Looper.getMainLooper())

    fun fuehreDurch(pcm: ByteArray, wiederholungen: Int): String = buildString {
        appendLine("VERZUG -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("$wiederholungen Läufe mit derselben Aufnahme. Alle Zeiten ab t2 --")
        appendLine("dem ersten Byte im Rohr. Vorher fließt kein Ton, da wäre jede")
        appendLine("Messung nur die Ladezeit des Dienstes.")
        appendLine()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appendLine("EXTRA_AUDIO_SOURCE gibt es erst ab Android 13. NICHT PRÜFBAR.")
            return@buildString
        }

        val laeufe = (1..wiederholungen).map { nummer ->
            aufStand("Verzug: Lauf $nummer von $wiederholungen")
            lauf(nummer, pcm)
        }

        appendLine("EINZELNE LÄUFE (ms ab t2)")
        appendLine("  %-4s %-10s %-12s %-12s %-12s %-8s".format(
            "Nr", "t1 bereit", "t3 Sprache", "t4 1. Stand", "t5 1. Segm", "Fehler"))
        laeufe.forEach { l ->
            appendLine("  %-4d %-10s %-12s %-12s %-12s %-8s".format(
                l.nummer, ms(l.t1BereitschaftMillis), ms(l.t3SprachbeginnMillis),
                ms(l.t4ErsterStandMillis), ms(l.t5ErstesSegmentMillis),
                l.fehler?.toString() ?: "-"))
        }
        appendLine()

        appendLine("KENNZAHLEN")
        appendLine("  %-28s %-9s %-9s %-9s %-13s %s".format(
            "Größe", "P50", "P95", "Mittel", "min..max", "Ausbeute"))
        listOf(
            "t0->t1 Aufnahme bereit" to laeufe.map { it.t0bis1AufnahmeMillis },
            "t2->t4 Erkenner bereit" to laeufe.map { it.t1BereitschaftMillis },
            "t2->t3 Sprache erkannt" to laeufe.map { it.t3SprachbeginnMillis },
            "t2->t5 erster Text sichtbar" to laeufe.map { it.t4ErsterStandMillis },
            "t2->t6 erstes Segment" to laeufe.map { it.t5ErstesSegmentMillis },
            "t7 Sprachende" to laeufe.map { it.t7SprachendeMillis },
            "t7->t8 Sprachende bis Text" to laeufe.map { it.sprachendeBisTextMillis },
            "t7->t9 Sprachende bis frei" to laeufe.map { it.sprachendeBisFreiMillis }
        ).forEach { (name, werte) ->
            val da = werte.filterNotNull()
            val (n, gesamt) = Kennzahlen.ausbeute(werte)
            // Bei wenigen Beobachtungen steht kein P95 da, sondern die
            // Spanne. Ein P95 aus fünf Werten ist der fünfte Wert.
            appendLine("  %-28s %-9s %-9s %-9s %-13s %d von %d".format(
                name,
                ms(Kennzahlen.perzentil(da, 0.5)),
                if (da.size >= P95_AB) ms(Kennzahlen.perzentil(da, 0.95)) else "zu wenige",
                ms(Kennzahlen.mittel(da)),
                if (da.isEmpty()) "-" else "${da.min()}..${da.max()}",
                n, gesamt))
        }
        appendLine()

        appendLine("URTEIL")
        val sichtbar = laeufe.mapNotNull { it.t4ErsterStandMillis }
        val p95 = Kennzahlen.perzentil(sichtbar, 0.95)
        when {
            sichtbar.size < wiederholungen / 2 ->
                appendLine("  Weniger als die Hälfte der Läufe lieferte einen " +
                    "Zwischenstand. Die Kennzahlen tragen nicht -- erst den Aufbau prüfen.")
            p95 == null -> appendLine("  Keine Messwerte.")
            p95 <= GUT_MILLIS ->
                appendLine("  **Der Verzug ist gut.** In 95 von 100 Fällen sieht der " +
                    "Nutzer binnen $p95 ms das erste Wort -- unter der Schwelle von " +
                    "$GUT_MILLIS ms, ab der Warten spürbar wird.")
            p95 <= ERTRAEGLICH_MILLIS ->
                appendLine("  **Der Verzug ist erträglich, aber spürbar**: $p95 ms im " +
                    "schlechten Fall. Der Nutzer merkt die Pause. Die Anzeige sollte " +
                    "in dieser Zeit erkennbar arbeiten, statt leer zu bleiben.")
            else ->
                appendLine("  **Der Verzug ist zu groß**: $p95 ms im schlechten Fall. " +
                    "So lange still zu bleiben liest sich als Fehler, nicht als Warten.")
        }
        val spanne = laeufe.mapNotNull { it.t4ErsterStandMillis }
        if (spanne.size >= 2 && spanne.max() > spanne.min() * 3) {
            appendLine()
            appendLine("  Die Streuung ist groß (${spanne.min()} bis ${spanne.max()} ms). " +
                "Unregelmäßiger")
            appendLine("  Verzug stört mehr als gleichmäßig langsamer -- der Nutzer kann " +
                "sich nicht darauf einstellen.")
        }
    }

    private fun lauf(nummer: Int, pcm: ByteArray): Lauf {
        var t1: Long? = null
        var t3: Long? = null
        var t4: Long? = null
        var t5: Long? = null
        var t6: Long? = null
        var t7: Long? = null
        var t8: Long? = null
        var t9: Long? = null
        var fehler: Int? = null
        var t0bis1: Long? = null
        // Erst gesetzt, wenn wirklich Ton fließt. Alle Zeiten beziehen sich
        // darauf -- ohne diesen Nullpunkt würde die Ladezeit des Dienstes
        // mitgemessen, die der Nutzer nie sieht.
        var t2: Long = -1

        fun seitTon(): Long? = if (t2 < 0) null else SystemClock.elapsedRealtime() - t2

        // t0 bis t1 braucht den echten Aufnahmeweg: eine kurze Tonstrecke,
        // nur um zu messen, wie lange AudioRecord bis zum ersten Block
        // braucht. Sie wird sofort wieder angehalten und speist nichts ein.
        run {
            val strecke = Tonstrecke(ABTASTRATE, 200)
            val vorher = android.os.SystemClock.elapsedRealtime()
            if (strecke.starte()) {
                Thread.sleep(600)
                val befund = strecke.halteAn()
                if (befund.geleseneRahmen > 0) {
                    t0bis1 = befund.ersterBlockMillis?.minus(vorher)
                }
            }
        }

        val fertig = CountDownLatch(1)
        var erkenner: SpeechRecognizer? = null
        val (lesen, schreiben) = ParcelFileDescriptor.createPipe()
        val gestartet = CountDownLatch(1)

        hauptfaden.post {
            val neuer = runCatching {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(zusammenhang)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(zusammenhang)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(zusammenhang)
                }
            }.getOrNull()
            if (neuer == null) {
                gestartet.countDown()
                fertig.countDown()
                return@post
            }
            erkenner = neuer
            neuer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { t1 = seitTon() }
                override fun onBeginningOfSpeech() { if (t3 == null) t3 = seitTon() }
                override fun onRmsChanged(rms: Float) = Unit
                override fun onBufferReceived(b: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(code: Int) {
                    if (fehler == null) fehler = code
                    fertig.countDown()
                }
                override fun onResults(werte: Bundle?) {
                    if (lies(werte).isNotEmpty()) {
                        if (t5 == null) t5 = seitTon()
                        // t8 ist der **letzte** bestätigte Text, nicht der
                        // erste: der Nutzer wartet auf den vollständigen
                        // Satz, nicht auf dessen ersten Abschnitt.
                        t8 = seitTon()
                    }
                    fertig.countDown()
                }
                override fun onPartialResults(werte: Bundle?) {
                    if (t4 == null && lies(werte).isNotEmpty()) t4 = seitTon()
                }
                override fun onSegmentResults(werte: Bundle) {
                    if (lies(werte).isNotEmpty()) {
                        if (t5 == null) t5 = seitTon()
                        t8 = seitTon()
                    }
                }
                override fun onEndOfSegmentedSession() {
                    t6 = seitTon()
                    fertig.countDown()
                }
                override fun onEvent(art: Int, p: Bundle?) = Unit
            })
            runCatching { neuer.startListening(absicht(lesen)) }
                .onFailure { fertig.countDown() }
            gestartet.countDown()
        }
        gestartet.await(5, TimeUnit.SECONDS)

        val schreiber = thread(name = "verzug-$nummer") {
            runCatching {
                FileOutputStream(schreiben.fileDescriptor).use { strom ->
                    var stelle = 0
                    val bis = SystemClock.elapsedRealtime() + SPRECHDAUER_MILLIS
                    while (SystemClock.elapsedRealtime() < bis && stelle < pcm.size) {
                        val menge = minOf(2048, pcm.size - stelle)
                        strom.write(pcm, stelle, menge)
                        // Erst nach dem ersten wirklich geschriebenen Block.
                        if (t2 < 0) t2 = SystemClock.elapsedRealtime()
                        stelle += menge
                        if (stelle >= pcm.size) stelle = 0
                        Thread.sleep(menge.toLong() * 1000 / (ABTASTRATE * 2))
                    }
                }
            }
            t7 = seitTon()
            runCatching { schreiben.close() }
        }

        schreiber.join(SPRECHDAUER_MILLIS + 5_000)
        fertig.await(15, TimeUnit.SECONDS)
        // Freigabe **abwarten**, nicht nur anstoßen -- sonst misst t9 nur,
        // wie schnell sich eine Nachricht absetzen lässt.
        val abgeraeumt = CountDownLatch(1)
        hauptfaden.post {
            runCatching { erkenner?.destroy() }
            abgeraeumt.countDown()
        }
        abgeraeumt.await(5, TimeUnit.SECONDS)
        runCatching { lesen.close() }
        t9 = seitTon()
        // Pause zwischen den Läufen: ohne sie trifft der nächste Lauf auf
        // einen Dienst, der noch aufräumt, und misst dessen Aufräumen mit.
        Thread.sleep(PAUSE_MILLIS)

        return Lauf(nummer, t0bis1, t1, t3, t4, t5, t6, t7, t8, t9, fehler)
    }

    private fun absicht(lesen: ParcelFileDescriptor) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sprache)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, lesen)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, ABTASTRATE)
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        }

    private fun lies(werte: Bundle?): List<String> =
        werte?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty().filter { it.isNotBlank() }

    private fun ms(wert: Long?): String = wert?.let { "$it" } ?: "-"

    companion object {
        const val ABTASTRATE = 16_000
        const val SPRECHDAUER_MILLIS = 6_000L
        const val PAUSE_MILLIS = 1_500L
        const val WIEDERHOLUNGEN = 20

        /** Ab so vielen Beobachtungen wird ein P95 ausgewiesen. */
        const val P95_AB = 20

        /** Unter dieser Schwelle merkt der Nutzer das Warten nicht. */
        const val GUT_MILLIS = 800L

        /** Darüber liest sich Stille als Fehler. */
        const val ERTRAEGLICH_MILLIS = 1_500L
    }
}
