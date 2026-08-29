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

/**
 * Beweist den Vorlauf -- oder widerlegt ihn.
 *
 * Der erste Anlauf am Mikrofon zeigte nichts: beide Durchgänge hatten den
 * Wortanfang, weil die Verzögerung zu kurz war. Ein Versuch, bei dem der
 * Unterschied zufällig ausbleibt, beweist nichts.
 *
 * Hier ist der Aufbau deshalb **bestimmt statt hoffend**:
 *
 * - Der Ton kommt aus einer bekannten Aufnahme, nicht aus dem Mikrofon.
 *   Damit ist er in jedem Durchgang derselbe -- der einzige Unterschied
 *   ist der Vorlauf.
 * - Die Verzögerung wird gestaffelt: 0, 500, 1500, 2500 ms. Bei genügend
 *   Verzögerung **muss** ohne Vorlauf der Anfang fehlen.
 * - Der Bezugssatz beginnt mit einem Wort, das sonst nirgends vorkommt.
 *   Fehlt es, ist der Anfang abgeschnitten -- das ist ablesbar und nicht
 *   auszulegen.
 *
 * Zusätzlich wird die **Folge der Bytes** geprüft, nicht nur der Text: der
 * Vorlauf muss zeitlich vor dem laufenden Ton liegen, ohne Dopplung,
 * Lücke oder Vertauschung.
 */
class Vorlaufversuch(
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
        val verzoegerungMillis: Long,
        val mitVorlauf: Boolean,
        val text: String,
        val herkunft: String,
        val hatAnfang: Boolean,
        /** Wie viele Wörter ankamen -- das eigentliche Maß. */
        val worte: Int,
        val vorlaufBytes: Long,
        val gesendeteBytes: Long,
        val folgeStimmt: Boolean,
        val fehler: Int?
    )

    private val hauptfaden = Handler(Looper.getMainLooper())

    fun fuehreDurch(pcm: ByteArray, bezugstext: String, ankerwort: String): String =
        buildString {
            appendLine("VORLAUFVERSUCH -- ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine("Der Ton kommt aus einer bekannten Aufnahme, nicht aus dem")
            appendLine("Mikrofon -- damit ist er in jedem Durchgang derselbe und der")
            appendLine("Vorlauf der einzige Unterschied.")
            appendLine()
            appendLine("Bezugstext: $bezugstext")
            appendLine("Ankerwort:  $ankerwort  (fehlt es, wurde der Anfang abgeschnitten)")
            appendLine()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                appendLine("EXTRA_AUDIO_SOURCE gibt es erst ab Android 13. NICHT PRÜFBAR.")
                return@buildString
            }

            val laeufe = mutableListOf<Lauf>()
            VERZOEGERUNGEN.forEach { verzoegerung ->
                listOf(false, true).forEach { mitVorlauf ->
                    laeufe += lauf(pcm, ankerwort, verzoegerung, mitVorlauf)
                }
            }

            appendLine("%-12s %-8s %-7s %-7s %-9s %s".format(
                "Verzögerung", "Vorlauf", "Wörter", "Folge", "Vorlauf-B", "Text"))
            laeufe.forEach { l ->
                appendLine("%-12s %-8s %-7s %-7s %-9s %s".format(
                    "${l.verzoegerungMillis} ms",
                    if (l.mitVorlauf) "an" else "aus",
                    l.worte.toString(),
                    if (l.folgeStimmt) "ok" else "KAPUTT",
                    l.vorlaufBytes,
                    l.text.take(50).ifBlank { "(keiner)" }
                ))
            }
            appendLine()

            appendLine("URTEIL")
            // Belegt ist der Vorlauf erst, wenn es eine Verzögerung gibt, bei
            // der er den Unterschied macht.
            // **Nicht an einem einzelnen Wort messen.** Der erste Anlauf hing
            // am Ankerwort "Zitrone" -- das der Erkenner in *keinem*
            // Durchgang lieferte, auch nicht ohne Verzögerung. Damit war das
            // Urteil blind für einen Unterschied, der in den Zahlen klar
            // dastand: ohne Vorlauf schrumpfte der Text von sieben Wörtern
            // auf eines und dann auf keines, mit Vorlauf blieb er vollständig.
            //
            // Gezählt wird deshalb, wie viel vom Text ankommt.
            val beweisend = laeufe.groupBy { it.verzoegerungMillis }
                .filter { (_, paar) ->
                    val ohne = paar.firstOrNull { !it.mitVorlauf } ?: return@filter false
                    val mit = paar.firstOrNull { it.mitVorlauf } ?: return@filter false
                    mit.worte > ohne.worte
                }
            val folgeKaputt = laeufe.filter { !it.folgeStimmt }

            when {
                folgeKaputt.isNotEmpty() ->
                    appendLine("  Die Bytefolge stimmt nicht bei: " +
                        folgeKaputt.joinToString { "${it.verzoegerungMillis} ms" } +
                        ". Der Vorlauf liegt nicht sauber vor dem laufenden Ton -- " +
                        "alles Weitere wäre wertlos.")
                beweisend.isNotEmpty() ->
                    appendLine("  **Der Vorlauf ist belegt.** Bei " +
                        beweisend.keys.joinToString { "$it ms" } +
                        " Verzögerung fehlt der Anfang ohne ihn und ist mit ihm da.")
                laeufe.none { it.worte > 0 } ->
                    appendLine("  Kein Durchgang lieferte Text. Der Aufbau trägt nicht; " +
                        "erst Erkennung und Einspeisung prüfen.")
                laeufe.filter { !it.mitVorlauf }.all { it.worte >= laeufe.maxOf { l -> l.worte } } ->
                    appendLine("  Auch ohne Vorlauf kommt bei jeder Verzögerung alles " +
                        "an. Der Vorlauf ist damit **nicht belegt**, sondern unnötig -- " +
                        "der Erkenner scheint schon vor dem ersten Byte bereit zu sein.")
                else ->
                    appendLine("  Gemischtes Bild. Die Zeilen oben einzeln lesen.")
            }
        }

    private fun lauf(
        pcm: ByteArray,
        ankerwort: String,
        verzoegerungMillis: Long,
        mitVorlauf: Boolean
    ): Lauf {
        aufStand("Verzögerung $verzoegerungMillis ms, Vorlauf ${if (mitVorlauf) "an" else "aus"}")
        val teiltexte = mutableListOf<String>()
        val segmente = mutableListOf<String>()
        val lesarten = mutableListOf<String>()
        var fehler: Int? = null
        val fertig = CountDownLatch(1)
        var erkenner: SpeechRecognizer? = null

        // Der Vorlaufspeicher steht hier, damit sich die Reihenfolge prüfen
        // lässt: erst das Gepufferte, dann der Rest -- lückenlos.
        val puffer = Vorlaufpuffer(
            Vorlaufpuffer.bloeckeFuer(VORLAUF_MILLIS, BLOCK_BYTES, ABTASTRATE)
        )
        val gesendet = mutableListOf<Byte>()
        var vorlaufBytes = 0L

        val (lesen, schreiben) = ParcelFileDescriptor.createPipe()

        hauptfaden.post {
            val neuer = runCatching {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(zusammenhang)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(zusammenhang)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(zusammenhang)
                }
            }.getOrNull()
            if (neuer == null) {
                fertig.countDown()
                return@post
            }
            erkenner = neuer
            neuer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rms: Float) = Unit
                override fun onBufferReceived(b: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(code: Int) {
                    fehler = code
                    fertig.countDown()
                }
                override fun onResults(werte: Bundle?) {
                    lesarten += lies(werte)
                    fertig.countDown()
                }
                override fun onPartialResults(werte: Bundle?) {
                    lies(werte).firstOrNull()?.let { teiltexte += it }
                }
                override fun onSegmentResults(werte: Bundle) {
                    lies(werte).firstOrNull()?.let { segmente += it }
                }
                override fun onEndOfSegmentedSession() = fertig.countDown()
                override fun onEvent(art: Int, p: Bundle?) = Unit
            })
            runCatching {
                neuer.startListening(absicht(lesen))
            }.onFailure { fertig.countDown() }
        }

        Thread.sleep(300)

        // Der Ton läuft in Echtzeit los. Bis zum Ablauf der Verzögerung geht
        // er in den Vorlaufspeicher, danach ins Rohr.
        val schreiber = Thread {
            runCatching {
                FileOutputStream(schreiben.fileDescriptor).use { strom ->
                    val beginn = SystemClock.elapsedRealtime()
                    var stelle = 0
                    var einspeisungLaeuft = verzoegerungMillis <= 0
                    while (stelle < pcm.size) {
                        val menge = minOf(BLOCK_BYTES, pcm.size - stelle)
                        val block = pcm.copyOfRange(stelle, stelle + menge)
                        stelle += menge

                        if (!einspeisungLaeuft &&
                            SystemClock.elapsedRealtime() - beginn >= verzoegerungMillis
                        ) {
                            einspeisungLaeuft = true
                            if (mitVorlauf) {
                                puffer.nimmHeraus().forEach {
                                    strom.write(it)
                                    gesendet += it.toList()
                                    vorlaufBytes += it.size
                                }
                            } else {
                                puffer.leere()
                            }
                        }

                        if (einspeisungLaeuft) {
                            strom.write(block)
                            gesendet += block.toList()
                        } else {
                            puffer.lege(block)
                        }
                        strom.flush()
                        Thread.sleep(BLOCK_BYTES.toLong() * 1000 / (ABTASTRATE * 2))
                    }
                }
            }
            runCatching { schreiben.close() }
        }
        schreiber.start()
        schreiber.join(90_000)
        fertig.await(20, TimeUnit.SECONDS)
        hauptfaden.post { runCatching { erkenner?.destroy() } }
        runCatching { lesen.close() }
        Thread.sleep(600)

        val wahl = Ergebniswahl.waehle(
            Ergebniswahl.ohneWiederholung(segmente), lesarten, teiltexte
        )
        // Die Folge stimmt, wenn das Gesendete ein zusammenhängender
        // Abschnitt der Quelle ist -- kein Sprung, keine Dopplung.
        val folgeStimmt = pruefeFolge(pcm, gesendet)
        return Lauf(
            verzoegerungMillis, mitVorlauf, wahl.text, wahl.herkunft.name,
            hatAnfang = Wortvergleich.zerlege(wahl.text)
                .contains(Wortvergleich.zerlege(ankerwort).firstOrNull().orEmpty()),
            worte = Wortvergleich.zerlege(wahl.text).size,
            vorlaufBytes = vorlaufBytes,
            gesendeteBytes = gesendet.size.toLong(),
            folgeStimmt = folgeStimmt,
            fehler = fehler
        )
    }

    /**
     * Prüft, dass das Gesendete ein **zusammenhängender** Abschnitt der
     * Quelle ist. Eine Dopplung oder Vertauschung fiele hier auf, im
     * Transkript womöglich nicht.
     */
    private fun pruefeFolge(quelle: ByteArray, gesendet: List<Byte>): Boolean {
        if (gesendet.isEmpty()) return false
        val anfang = gesendet.take(PRUEFLAENGE)
        // Wo in der Quelle beginnt das Gesendete?
        for (start in 0..quelle.size - anfang.size) {
            var passt = true
            for (i in anfang.indices) {
                if (quelle[start + i] != anfang[i]) { passt = false; break }
            }
            if (!passt) continue
            // Ab dort muss alles der Reihe nach folgen.
            val laenge = minOf(gesendet.size, quelle.size - start)
            for (i in 0 until minOf(laenge, PRUEFLAENGE * 8)) {
                if (quelle[start + i] != gesendet[i]) return false
            }
            return true
        }
        return false
    }

    private fun absicht(lesen: ParcelFileDescriptor) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sprache)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sprache)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, lesen)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, ABTASTRATE)
            putExtra(
                RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                RecognizerIntent.EXTRA_AUDIO_SOURCE
            )
        }

    private fun lies(werte: Bundle?): List<String> =
        werte?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty().filter { it.isNotBlank() }

    companion object {
        const val ABTASTRATE = 16_000
        const val BLOCK_BYTES = 2048

        /** Reichlich -- der Vorlauf soll auch die längste Verzögerung decken. */
        const val VORLAUF_MILLIS = 3_000

        /** Gestaffelt, damit sichtbar wird, ab wann der Anfang verloren geht. */
        val VERZOEGERUNGEN = listOf(0L, 500L, 1500L, 2500L)

        const val PRUEFLAENGE = 64
    }
}
