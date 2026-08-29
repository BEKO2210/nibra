package de.ithandwerkstuttgart.nibra.forschung

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Hundert vollständige Diktate hintereinander, **im selben Prozess**.
 *
 * Der Anlass: die Dateizeiger wuchsen im Transportlauf je Durchgang um
 * zwei bis vier und gingen erst zurück, als die App in den Hintergrund
 * kam. Für den Betrieb reicht das nicht. Ein Nutzer kann fünfzig Diktate
 * hintereinander sprechen, ohne die App je zu verlassen -- und darf dabei
 * nicht in eine Grenze laufen, die niemand kommen sah.
 *
 * Deshalb hier ausdrücklich **ohne** die Auswege, die das Bild schönen
 * würden: kein Neustart der App, kein neuer Prozess, kein Wechsel in den
 * Hintergrund, kein erzwungenes Aufräumen der Halde. Nur das, was beim
 * Diktieren wirklich geschieht.
 *
 * Drei Ausgänge sind denkbar, und der Bericht benennt sie:
 *
 * - **Hochlauf und Plateau** -- es wird ein Vorrat angelegt und dann
 *   wiederverwendet. Unbedenklich.
 * - **Stetiges Wachstum** -- ein Leck. Die Strecke wäre nicht
 *   betriebstauglich.
 * - **Rückgabe erst beim Verlassen der App** -- ebenfalls zu klären: der
 *   gewöhnliche Weg gibt dann etwas nicht her, was er hergeben müsste.
 *
 * Gezählt wird nach **Art** der Zeiger, nicht nur ihre Zahl. Ohne das
 * wüsste man am Ende, dass etwas offen bleibt, aber nicht was.
 */
class Sitzungsdauerlauf(
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

    data class Sitzung(
        val nummer: Int,
        val hatText: Boolean,
        val fehler: Int?,
        val stand: Prozessbefund.Stand,
        val zeigerArten: Map<String, Int>,
        /** Von startListening bis onReadyForSpeech. */
        val startMillis: Long?,
        /** Vom ersten Byte im Rohr bis zum Ende der Sitzung. */
        val sitzungMillis: Long,
        /** Was das Aufräumen nach der Sitzung kostet. */
        val aufraeumMillis: Long
    )

    private val hauptfaden = Handler(Looper.getMainLooper())

    fun fuehreDurch(pcm: ByteArray, anzahl: Int): String = buildString {
        appendLine("SITZUNGSDAUERLAUF -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("$anzahl vollständige Diktate hintereinander, im selben Prozess.")
        appendLine("Ohne Neustart, ohne Hintergrund, ohne erzwungenes Aufräumen.")
        appendLine("Seitengröße für RSS: ${Prozessbefund.SEITE_KB} KB (fest angenommen).")
        appendLine()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appendLine("EXTRA_AUDIO_SOURCE gibt es erst ab Android 13. NICHT PRÜFBAR.")
            return@buildString
        }

        // Kontrollfall: eine einzelne Sitzung, bevor hundert gefahren
        // werden. Kommt hier kein Text, misst der Rest nur Rauschen.
        aufStand("Kontrollsitzung")
        val probe = sitzung(0, pcm)
        appendLine("KONTROLLSITZUNG")
        appendLine("  Text erkannt: ${if (probe.hatText) "ja" else "NEIN"}, " +
            "Fehler ${probe.fehler ?: "keiner"}, " +
            "Zeiger ${probe.stand.offeneZeiger}")
        if (!probe.hatText) {
            appendLine("  FEHLGESCHLAGEN -- ohne eine funktionierende Sitzung sagt der Lauf nichts.")
            return@buildString
        }
        appendLine("  in Ordnung")
        appendLine()

        val sitzungen = (1..anzahl).map { nummer ->
            if (nummer % 10 == 0) aufStand("Sitzung $nummer von $anzahl")
            sitzung(nummer, pcm)
        }

        appendLine("VERLAUF -- jede Sitzung")
        appendLine("  %-4s %-6s %-6s %-8s %-8s %-8s %-7s %-7s %-7s %s".format(
            "Nr", "Zeig", "Fäden", "Java KB", "nativ", "RSS KB",
            "Start", "Dauer", "Aufräum", "Text"))
        sitzungen.forEach { s ->
            appendLine("  %-4d %-6s %-6d %-8d %-8d %-8s %-7s %-7d %-7d %s".format(
                s.nummer, s.stand.offeneZeiger?.toString() ?: "-", s.stand.faeden,
                s.stand.speicherKb, s.stand.nativeKb,
                s.stand.rssKb?.toString() ?: "-",
                s.startMillis?.toString() ?: "-",
                s.sitzungMillis, s.aufraeumMillis,
                if (s.hatText) "ja" else "NEIN"))
        }
        appendLine()

        // Fehlgeschlagene Sitzungen einzeln, nicht als Prozentsatz. Bei
        // einer Kernfunktion zählt jeder Ausfall für sich.
        val gescheitert = sitzungen.filter { !it.hatText }
        if (gescheitert.isNotEmpty()) {
            appendLine("SITZUNGEN OHNE TEXT -- einzeln")
            gescheitert.forEach { s ->
                appendLine("  Nr ${s.nummer}: Fehler ${s.fehler ?: "keiner gemeldet"}, " +
                    "Zeiger ${s.stand.offeneZeiger}, Fäden ${s.stand.faeden}, " +
                    "Start ${s.startMillis ?: "-"} ms, Dauer ${s.sitzungMillis} ms")
                val davor = sitzungen.firstOrNull { it.nummer == s.nummer - 1 }
                val danach = sitzungen.firstOrNull { it.nummer == s.nummer + 1 }
                appendLine("    davor: ${davor?.let { if (it.hatText) "Text" else "auch kein Text" } ?: "-"}, " +
                    "danach: ${danach?.let { if (it.hatText) "Text -- erholt sich" else "auch kein Text" } ?: "-"}")
            }
            appendLine()
        }

        appendLine("ZEIGERARTEN ÜBER DEN LAUF")
        appendLine("  %-24s %-8s %-8s %-8s %s".format(
            "Art", "Sitz. 1", "Mitte", "Ende", "Einordnung"))
        val alleArten = sitzungen.flatMap { it.zeigerArten.keys }.toSet()
        val mitteNr = sitzungen.size / 2
        alleArten.sorted().forEach { art ->
            val reihe = sitzungen.map { it.zeigerArten[art] ?: 0 }
            val a = reihe.first()
            val m = reihe[mitteNr]
            val e = reihe.last()
            appendLine("  %-24s %-8d %-8d %-8d %s".format(art, a, m, e, when {
                e > m && m > a -> "**wächst weiter**"
                e > a && e == m -> "einmalig beim Aufbau"
                e == a && reihe.max() > a -> "je Sitzung, wird zurückgegeben"
                e > a -> "gewachsen, aber nicht stetig"
                else -> "unverändert"
            }))
        }
        appendLine()

        val erste = sitzungen.first()
        val letzte = sitzungen.last()
        val ohneText = sitzungen.count { !it.hatText }
        appendLine("ZEIGER NACH ART -- Unterschied zwischen Sitzung 1 und $anzahl")
        val unterschied = Zeigerbefund.unterschied(erste.zeigerArten, letzte.zeigerArten)
        if (unterschied.isEmpty()) {
            appendLine("  keine Art hat sich verändert")
        } else {
            unterschied.forEach { (art, d) ->
                appendLine("  %-24s %s".format(art, if (d > 0) "+$d" else "$d"))
            }
        }
        appendLine()
        appendLine("  Bestand am Ende:")
        letzte.zeigerArten.entries.sortedByDescending { it.value }.forEach { (art, n) ->
            appendLine("    %-24s %d".format(art, n))
            // Beispiele für die Arten, die sonst ein Rätsel bleiben.
            if (art == "sonstiges" || art.startsWith("namenlos")) {
                Zeigerbefund.beispieleFuer(art).forEach { appendLine("      $it") }
            }
        }
        appendLine()

        appendLine("URTEIL")
        appendLine("  Sitzungen ohne Text: $ohneText von $anzahl")
        val zeigerVerlauf = sitzungen.mapNotNull { it.stand.offeneZeiger }
        // Zähler und Speicher werden **verschieden** beurteilt. Ein
        // Dateizeiger ist entweder offen oder nicht; Speicher schwingt
        // zwischen zwei Bereinigungen um Megabyte. Dieselbe Schwelle für
        // beide zu nehmen war der neunte Messfehler.
        beurteile("Dateizeiger", zeigerVerlauf.map { it.toLong() }, anzahl)
        beurteile("Fäden", sitzungen.map { it.stand.faeden.toLong() }, anzahl)
        appendLine()
        appendLine("  Speicher -- beurteilt am Boden nach der Bereinigung, nicht am Mittel:")
        listOf(
            "Java-Halde" to sitzungen.map { it.stand.speicherKb },
            "native Halde" to sitzungen.map { it.stand.nativeKb },
            "RSS" to sitzungen.mapNotNull { it.stand.rssKb }
        ).forEach { (name, reihe) ->
            // Genug Fenster, damit eine Ausgleichsgerade etwas sieht.
            // Drei Fenster reichten nicht: über 900 Sitzungen las das
            // Urteil daraus „pendelt sich ein", während die Reihe mit
            // 1,66 KB je Sitzung bei siebenfachem Standardfehler stieg.
            val fenster = maxOf(10, reihe.size / 20)
            appendLine("  %-14s %s".format(
                "$name:", Verlaufsurteil.beschreibe(Verlaufsurteil.beurteile(reihe, fenster))))
        }
        appendLine()
        appendLine("  Wird es langsamer? Ein Ressourcenstau zeigt sich oft zuerst so.")
        beurteile("Startlatenz", sitzungen.mapNotNull { it.startMillis }, anzahl)
        beurteile("Sitzungsdauer", sitzungen.map { it.sitzungMillis }, anzahl)
        beurteile("Aufräumdauer", sitzungen.map { it.aufraeumMillis }, anzahl)
    }

    /**
     * Wächst die Größe weiter, oder findet sie ein Plateau?
     *
     * Verglichen wird das letzte Fünftel mit dem mittleren Fünftel, nicht
     * mit dem ersten. Der Anfang enthält den einmaligen Aufbau -- Verbindung
     * zum Erkennerdienst, geladene Bibliotheken -- und ließe jeden Verlauf
     * wie Wachstum aussehen. Gefragt ist, ob es **nach** dem Einschwingen
     * weitergeht.
     */
    private fun StringBuilder.beurteile(was: String, werte: List<Long>, anzahl: Int) {
        if (werte.size < 10) {
            appendLine("  $was: zu wenige Werte für eine Aussage")
            return
        }
        val fuenftel = werte.size / 5
        val mitte = werte.subList(fuenftel * 2, fuenftel * 3).average()
        val ende = werte.takeLast(fuenftel).average()
        val zuwachs = ende - mitte
        // Auf die zweite Haelfte des Laufs bezogen, damit die Zahl als
        // „je Sitzung" lesbar ist.
        val jeSitzung = zuwachs / (anzahl * 0.5)
        appendLine("  %-14s Mitte %.0f, Ende %.0f, Zuwachs %.0f (%.2f je Sitzung) -- %s".format(
            "$was:", mitte, ende, zuwachs, jeSitzung,
            when {
                zuwachs <= 0 -> "kein Wachstum"
                jeSitzung < PLATEAU_GRENZE -> "Plateau erreicht"
                else -> "**WÄCHST WEITER**"
            }
        ))
    }

    private fun sitzung(nummer: Int, pcm: ByteArray): Sitzung {
        var hatText = false
        var fehler: Int? = null
        var startMillis: Long? = null
        var ersteBytes = 0L
        val begonnen = android.os.SystemClock.elapsedRealtime()
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
                override fun onReadyForSpeech(p: Bundle?) {
                    if (startMillis == null) {
                        startMillis = android.os.SystemClock.elapsedRealtime() - begonnen
                    }
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rms: Float) = Unit
                override fun onBufferReceived(b: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(code: Int) {
                    if (fehler == null) fehler = code
                    fertig.countDown()
                }
                override fun onResults(werte: Bundle?) {
                    if (lies(werte).isNotEmpty()) hatText = true
                    fertig.countDown()
                }
                override fun onPartialResults(werte: Bundle?) {
                    if (lies(werte).isNotEmpty()) hatText = true
                }
                override fun onSegmentResults(werte: Bundle) {
                    if (lies(werte).isNotEmpty()) hatText = true
                }
                override fun onEndOfSegmentedSession() = fertig.countDown()
                override fun onEvent(art: Int, p: Bundle?) = Unit
            })
            runCatching { neuer.startListening(absicht(lesen)) }
                .onFailure { fertig.countDown() }
            gestartet.countDown()
        }
        gestartet.await(5, TimeUnit.SECONDS)

        val schreiber = thread(name = "sitzung-$nummer") {
            runCatching {
                FileOutputStream(schreiben.fileDescriptor).use { strom ->
                    var stelle = 0
                    val hoechstens = minOf(pcm.size, ABTASTRATE * 2 * SPRECHDAUER_SEKUNDEN)
                    while (stelle < hoechstens) {
                        val menge = minOf(2048, hoechstens - stelle)
                        strom.write(pcm, stelle, menge)
                        if (ersteBytes == 0L) {
                            ersteBytes = android.os.SystemClock.elapsedRealtime()
                        }
                        stelle += menge
                        Thread.sleep(menge.toLong() * 1000 / (ABTASTRATE * 2))
                    }
                }
            }
            runCatching { schreiben.close() }
        }
        schreiber.join(20_000)
        fertig.await(10, TimeUnit.SECONDS)
        val sitzungFertig = android.os.SystemClock.elapsedRealtime()
        // Der gewöhnliche Abschluss einer Sitzung -- genau der, den die App
        // auch geht. Wird hier mehr aufgeräumt als dort, misst der Lauf
        // etwas, das es im Betrieb nicht gibt.
        hauptfaden.post { runCatching { erkenner?.destroy() } }
        runCatching { lesen.close() }
        Thread.sleep(RUHE_MILLIS)

        val aufgeraeumt = android.os.SystemClock.elapsedRealtime()
        return Sitzung(
            nummer, hatText, fehler, Prozessbefund.nimmAuf(), Zeigerbefund.nachArt(),
            startMillis = startMillis,
            sitzungMillis = if (ersteBytes == 0L) 0 else sitzungFertig - ersteBytes,
            // Die Ruhepause ist Absicht und gehört nicht zum Aufräumen.
            aufraeumMillis = aufgeraeumt - sitzungFertig - RUHE_MILLIS
        )
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

    companion object {
        const val ABTASTRATE = 16_000
        const val SPRECHDAUER_SEKUNDEN = 3
        const val RUHE_MILLIS = 400L
        const val SITZUNGEN = 100

        /**
         * Unter diesem Zuwachs je Sitzung gilt eine Größe als eingependelt.
         * Bei Dateizeigern hiesse ein Zehntel je Sitzung: zehn Sitzungen
         * für einen einzigen Zeiger -- das erreicht in keinem denkbaren
         * Gebrauch eine Grenze.
         */
        const val PLATEAU_GRENZE = 0.1
    }
}
