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
    private val aufStand: (String) -> Unit
) {

    data class Lauf(
        val nummer: Int,
        val t1BereitschaftMillis: Long?,
        val t3SprachbeginnMillis: Long?,
        val t4ErsterStandMillis: Long?,
        val t5ErstesSegmentMillis: Long?,
        val t6EndeMillis: Long?,
        val fehler: Int?
    )

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
        appendLine("  %-26s %-9s %-9s %-9s %s".format(
            "Größe", "P50", "P95", "Mittel", "Ausbeute"))
        listOf(
            "t2->t3 Sprache erkannt" to laeufe.map { it.t3SprachbeginnMillis },
            "t2->t4 erster Text sichtbar" to laeufe.map { it.t4ErsterStandMillis },
            "t2->t5 erstes Segment" to laeufe.map { it.t5ErstesSegmentMillis },
            "t2->t6 Sitzungsende" to laeufe.map { it.t6EndeMillis }
        ).forEach { (name, werte) ->
            val da = werte.filterNotNull()
            val (n, gesamt) = Kennzahlen.ausbeute(werte)
            appendLine("  %-26s %-9s %-9s %-9s %d von %d".format(
                name,
                ms(Kennzahlen.perzentil(da, 0.5)),
                ms(Kennzahlen.perzentil(da, 0.95)),
                ms(Kennzahlen.mittel(da)),
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
        var fehler: Int? = null
        // Erst gesetzt, wenn wirklich Ton fließt. Alle Zeiten beziehen sich
        // darauf -- ohne diesen Nullpunkt würde die Ladezeit des Dienstes
        // mitgemessen, die der Nutzer nie sieht.
        var t2: Long = -1

        fun seitTon(): Long? = if (t2 < 0) null else SystemClock.elapsedRealtime() - t2

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
                    if (t5 == null && lies(werte).isNotEmpty()) t5 = seitTon()
                    fertig.countDown()
                }
                override fun onPartialResults(werte: Bundle?) {
                    if (t4 == null && lies(werte).isNotEmpty()) t4 = seitTon()
                }
                override fun onSegmentResults(werte: Bundle) {
                    if (t5 == null && lies(werte).isNotEmpty()) t5 = seitTon()
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
            runCatching { schreiben.close() }
        }

        schreiber.join(SPRECHDAUER_MILLIS + 5_000)
        fertig.await(15, TimeUnit.SECONDS)
        hauptfaden.post { runCatching { erkenner?.destroy() } }
        runCatching { lesen.close() }
        // Pause zwischen den Läufen: ohne sie trifft der nächste Lauf auf
        // einen Dienst, der noch aufräumt, und misst dessen Aufräumen mit.
        Thread.sleep(PAUSE_MILLIS)

        return Lauf(nummer, t1, t3, t4, t5, t6, fehler)
    }

    private fun absicht(lesen: ParcelFileDescriptor) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
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

        /** Unter dieser Schwelle merkt der Nutzer das Warten nicht. */
        const val GUT_MILLIS = 800L

        /** Darüber liest sich Stille als Fehler. */
        const val ERTRAEGLICH_MILLIS = 1_500L
    }
}
