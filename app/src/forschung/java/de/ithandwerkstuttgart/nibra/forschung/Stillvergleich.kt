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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Vergleich **ohne einen Ton im Raum**.
 *
 * Beide Seiten bekommen dieselbe Aufnahme über das Rohr eingespeist. Kein
 * Lautsprecher, kein Mikrofon, kein Raum -- und deshalb kein Ergebnis über
 * den Mikrofonweg. Was hier verglichen wird, ist die **Einstellung** des
 * Erkenners, nicht der Weg des Tons dorthin.
 *
 * Das ist ehrlicher als es klingt: gerade weil der Raum fehlt, ist der
 * Unterschied zwischen den Seiten sauber. Über den Lautsprecher gemessen
 * läge in jeder Zahl auch der Hall, die Entfernung und der Zufall des
 * Augenblicks.
 *
 * Zwei Fragen lassen sich so beantworten:
 *
 * - **Segmentsitzung**: erkennt der Dienst mit `EXTRA_SEGMENTED_SESSION`
 *   besser oder schlechter als ohne?
 * - **Vorgabeliste**: helfen die Eigennamen aus
 *   `EXTRA_BIASING_STRINGS`, und schadet es der gewöhnlichen Sprache?
 *
 * Was hier **nicht** beantwortet wird: ob der neue Weg am echten Mikrofon
 * besser hört. Dafür braucht es den Lautsprecher, und dafür braucht es
 * einen Zeitpunkt, an dem Lärm erlaubt ist.
 */
class Stillvergleich(
    private val zusammenhang: Context,
    private val sprache: String = "de-DE",
    private val aufStand: (String) -> Unit
) {

    enum class Frage { SEGMENTSITZUNG, VORGABELISTE }

    data class Seite(val name: String, val segment: Boolean, val vorgabe: Boolean)

    data class Lauf(
        val satzname: String,
        val seite: String,
        val text: String,
        val ersterTextMillis: Long?,
        val bestaetigtMillis: Long?,
        val fehler: Int?
    )

    private val hauptfaden = Handler(Looper.getMainLooper())

    fun fuehreDurch(
        korpus: File,
        frage: Frage,
        klassen: Map<String, Fehlerarten.Klasse>,
        vorgabeWorte: List<String>,
        paare: Int
    ): String = buildString {
        val seiten = when (frage) {
            Frage.SEGMENTSITZUNG -> listOf(
                Seite("OHNE", segment = false, vorgabe = false),
                Seite("MIT", segment = true, vorgabe = false)
            )
            Frage.VORGABELISTE -> listOf(
                Seite("OHNE", segment = true, vorgabe = false),
                Seite("MIT", segment = true, vorgabe = true)
            )
        }

        appendLine("STILLVERGLEICH -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("Frage: ${frage.name}")
        appendLine("Eingespeister Ton, kein Lautsprecher, kein Mikrofon.")
        // Liegt im Korpusordner eine Wortliste, gilt sie -- so passen die
        // vorgegebenen Wörter zu den Sätzen, die wirklich geprüft werden.
        // Eine Vorgabeliste mit Wörtern, die im Bezugstext nicht vorkommen,
        // könnte gar nichts bewirken und würde nichts beweisen.
        val worte = File(korpus, "vorgabeworte.txt").takeIf { it.exists() }
            ?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: vorgabeWorte
        if (frage == Frage.VORGABELISTE) {
            appendLine("Vorgegebene Wörter (${worte.size}): " +
                worte.take(12).joinToString(", ") + if (worte.size > 12) " ..." else "")
        }
        appendLine()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appendLine("EXTRA_AUDIO_SOURCE gibt es erst ab Android 13. NICHT PRÜFBAR.")
            return@buildString
        }

        val saetze = korpus.listFiles { _, n -> n.endsWith(".wav") }.orEmpty()
            .sortedBy { it.name }
            .mapNotNull { ton ->
                val text = File(korpus, ton.nameWithoutExtension + ".txt")
                if (text.exists()) Triple(ton.nameWithoutExtension, ton, text.readText().trim())
                else null
            }
        if (saetze.isEmpty()) {
            appendLine("Kein Prüfsatz in ${korpus.absolutePath}. Erwartet werden Paare aus")
            appendLine("<name>.wav und <name>.txt.")
            return@buildString
        }
        appendLine("${saetze.size} Prüfsätze, $paare Paare je Satz:")
        saetze.forEach { (name, _, bezug) -> appendLine("  $name: ${bezug.take(70)}") }
        appendLine()

        val laeufe = mutableListOf<Lauf>()
        saetze.forEach { (name, ton, _) ->
            val pcm = pcmAus(ton)
            if (pcm.isEmpty()) {
                appendLine("$name: Aufnahme nicht lesbar, übersprungen.")
                return@forEach
            }
            (1..paare).forEach { paar ->
                // Reihenfolge wechselt, damit ein Unterschied nicht an der
                // Stelle im Ablauf hängt.
                val reihe = if (paar % 2 == 1) seiten else seiten.reversed()
                reihe.forEach { seite ->
                    aufStand("$name $paar/$paare, ${seite.name}")
                    laeufe += lauf(name, seite, pcm, worte)
                }
            }
        }

        val bezuege = saetze.associate { (name, _, bezug) -> name to bezug }
        schreibeAuswertung(laeufe, bezuege, klassen, seiten.map { it.name })
    }

    private fun StringBuilder.schreibeAuswertung(
        laeufe: List<Lauf>,
        bezuege: Map<String, String>,
        klassen: Map<String, Fehlerarten.Klasse>,
        seiten: List<String>
    ) {
        appendLine("EINZELNE LÄUFE")
        laeufe.forEach {
            // Gekürzt, nur zum Überfliegen. Gerechnet wird nie hierauf --
            // siehe „je Aufnahme" weiter unten.
            appendLine("  %-14s %-5s %s".format(
                it.satzname, it.seite,
                it.text.ifBlank { it.fehler?.let { f -> "(Fehler $f)" } ?: "(kein Text)" }.take(72)
            ))
        }
        appendLine()

        val guete = seiten.associateWith { seite ->
            laeufe.filter { it.seite == seite && it.text.isNotBlank() }
                .map { Guetemasse.beurteile(bezuege.getValue(it.satzname), it.text) }
        }
        appendLine("GÜTE (niedriger ist besser)")
        appendLine("  %-30s %-10s %-10s %s".format("Mass", seiten[0], seiten[1], "Unterschied"))
        listOf<Pair<String, (Guetemasse.Befund) -> Double>>(
            "rohe Wortfehlerrate" to { it.roheWortfehlerrate },
            "bereinigte Wortfehlerrate" to { it.bereinigteWortfehlerrate },
            "Zeichenfehlerrate" to { it.zeichenfehlerrate },
            "Auslassungen" to { it.auslassungsrate },
            "Einfügungen" to { it.einfuegungsrate }
        ).forEach { (name, holen) ->
            val w = seiten.map { s ->
                guete.getValue(s).map(holen).let { if (it.isEmpty()) null else it.average() }
            }
            appendLine("  %-30s %-10s %-10s %s".format(
                name,
                w[0]?.let { "%.1f %%".format(it * 100) } ?: "-",
                w[1]?.let { "%.1f %%".format(it * 100) } ?: "-",
                if (w[0] != null && w[1] != null) "%+.1f Punkte".format((w[1]!! - w[0]!!) * 100) else "-"
            ))
        }
        listOf<Pair<String, (Guetemasse.Befund) -> Int>>(
            "Verlust am Satzanfang" to { it.verlustAmAnfang },
            "Verlust am Satzende" to { it.verlustAmEnde }
        ).forEach { (name, holen) ->
            val w = seiten.map { s ->
                guete.getValue(s).map { holen(it).toDouble() }
                    .let { if (it.isEmpty()) null else it.average() }
            }
            appendLine("  %-30s %-10s %-10s %s".format(
                name,
                w[0]?.let { "%.2f".format(it) } ?: "-",
                w[1]?.let { "%.2f".format(it) } ?: "-",
                if (w[0] != null && w[1] != null) "%+.2f".format(w[1]!! - w[0]!!) else "-"
            ))
        }
        appendLine()

        val klassenbefund = seiten.associateWith { seite ->
            laeufe.filter { it.seite == seite && it.text.isNotBlank() }.map {
                val bezug = bezuege.getValue(it.satzname)
                Fehlerarten.beurteile(Wortvergleich.vergleiche(bezug, it.text), bezug, klassen)
            }
        }
        appendLine("TREFFERQUOTE JE WORTKLASSE (höher ist besser)")
        appendLine("  %-16s %-10s %-10s %s".format("Klasse", seiten[0], seiten[1], "Unterschied"))
        Fehlerarten.Klasse.entries.forEach { klasse ->
            val w = seiten.map { s ->
                klassenbefund.getValue(s).mapNotNull { b ->
                    b.jeKlasse.first { it.klasse == klasse }.quote
                }.let { if (it.isEmpty()) null else it.average() }
            }
            appendLine("  %-16s %-10s %-10s %s".format(
                klasse.name,
                w[0]?.let { "%.0f %%".format(it * 100) } ?: "-",
                w[1]?.let { "%.0f %%".format(it * 100) } ?: "-",
                if (w[0] != null && w[1] != null) "%+.0f Punkte".format((w[1]!! - w[0]!!) * 100) else "-"
            ))
        }
        val erfunden = seiten.map { s ->
            klassenbefund.getValue(s).map { it.erfunden.toDouble() }
                .let { if (it.isEmpty()) null else it.average() }
        }
        appendLine("  %-16s %-10s %-10s %s".format(
            "erfundene Wörter",
            erfunden[0]?.let { "%.2f".format(it) } ?: "-",
            erfunden[1]?.let { "%.2f".format(it) } ?: "-",
            if (erfunden[0] != null && erfunden[1] != null)
                "%+.2f".format(erfunden[1]!! - erfunden[0]!!) else "-"))
        appendLine()

        // **Je Aufnahme, auf dem vollen Text.**
        //
        // Ein Mittelwert über alle Aufnahmen sagt nicht, ob eine Seite
        // überall etwas besser ist oder ob wenige Aufnahmen den Ausschlag
        // geben. Für die Entscheidung ist das ein Unterschied: eine
        // Verbesserung, die aus drei von achtzig Aufnahmen stammt, ist
        // etwas anderes als eine, die überall greift.
        //
        // Gerechnet wird hier auf dem **vollen** Text. Der Abschnitt
        // „einzelne Läufe" oben kürzt für die Lesbarkeit -- wer darauf
        // rechnet, misst die Kürzung mit.
        appendLine("JE AUFNAHME -- wer gewinnt?")
        var besser = 0; var gleich = 0; var schlechter = 0
        val auffaellig = mutableListOf<Triple<String, Double, Double>>()
        bezuege.keys.sorted().forEach { name ->
            val a = laeufe.firstOrNull { it.satzname == name && it.seite == seiten[0] }
            val b = laeufe.firstOrNull { it.satzname == name && it.seite == seiten[1] }
            if (a == null || b == null) return@forEach
            val bezug = bezuege.getValue(name)
            val ra = Guetemasse.beurteile(bezug, a.text).bereinigteWortfehlerrate
            val rb = Guetemasse.beurteile(bezug, b.text).bereinigteWortfehlerrate
            when {
                rb < ra - 0.0001 -> { besser++; auffaellig += Triple(name, ra, rb) }
                rb > ra + 0.0001 -> { schlechter++; auffaellig += Triple(name, ra, rb) }
                else -> gleich++
            }
        }
        appendLine("  ${seiten[1]} besser:      $besser")
        appendLine("  Gleichstand:      $gleich")
        appendLine("  ${seiten[1]} schlechter:  $schlechter")
        if (auffaellig.isNotEmpty()) {
            appendLine()
            appendLine("  Aufnahmen mit Unterschied, ungekürzt:")
            auffaellig.sortedBy { it.third - it.second }.forEach { (name, ra, rb) ->
                appendLine("    $name: ${seiten[0]} %.0f %% -> ${seiten[1]} %.0f %%"
                    .format(ra * 100, rb * 100))
                appendLine("      Bezug: ${bezuege.getValue(name)}")
                laeufe.firstOrNull { it.satzname == name && it.seite == seiten[0] }
                    ?.let { appendLine("      ${seiten[0]}: ${it.text}") }
                laeufe.firstOrNull { it.satzname == name && it.seite == seiten[1] }
                    ?.let { appendLine("      ${seiten[1]}: ${it.text}") }
            }
        }
        appendLine()

        appendLine("ZEITEN UND AUSBEUTE")
        seiten.forEach { s ->
            val erster = laeufe.filter { it.seite == s }.mapNotNull { it.ersterTextMillis }
            val fertig = laeufe.filter { it.seite == s }.mapNotNull { it.bestaetigtMillis }
            appendLine("  $s: erster Text P50 ${Kennzahlen.perzentil(erster, 0.5) ?: "-"} ms, " +
                "bestätigt P50 ${Kennzahlen.perzentil(fertig, 0.5) ?: "-"} ms, " +
                "${laeufe.count { it.seite == s && it.text.isNotBlank() }} von " +
                "${laeufe.count { it.seite == s }} mit Text")
        }
        appendLine()

        appendLine("URTEIL")
        val raten = seiten.map { s ->
            guete.getValue(s).map { it.bereinigteWortfehlerrate }
        }
        if (raten.any { it.isEmpty() }) {
            appendLine("  Eine Seite lieferte keinen Text. Kein Vergleich.")
            return
        }
        val a = raten[0].average()
        val b = raten[1].average()
        appendLine("  bereinigte Wortfehlerrate ${seiten[0]} %.1f %%, ${seiten[1]} %.1f %%"
            .format(a * 100, b * 100))
        appendLine("  " + when {
            b < a * 0.95 -> "**${seiten[1]} erkennt besser.**"
            b <= a * 1.05 -> "**Gleichstand** -- der Unterschied liegt unter fünf Prozent der Rate."
            else -> "**${seiten[1]} erkennt schlechter.**"
        })
        appendLine()
        appendLine("  Gemessen wurde die Einstellung des Erkenners bei eingespeistem Ton.")
        appendLine("  Über den Mikrofonweg sagt das nichts.")
    }

    /**
     * Liest die Abtastwerte aus einer WAV-Datei.
     *
     * Sucht den `data`-Abschnitt, statt feste 44 Bytes zu überspringen: der
     * Kopf ist nicht immer gleich lang, und ein fester Versatz schnitte
     * sonst entweder Ton ab oder nähme Kopfbytes als Ton -- beides fiele
     * als schlechtere Erkennung auf, ohne dass jemand die Ursache sähe.
     */
    fun pcmAus(datei: File): ByteArray = runCatching {
        val alles = datei.readBytes()
        var stelle = 12
        while (stelle + 8 < alles.size) {
            val kennung = String(alles, stelle, 4, Charsets.US_ASCII)
            val laenge = (alles[stelle + 4].toInt() and 0xFF) or
                ((alles[stelle + 5].toInt() and 0xFF) shl 8) or
                ((alles[stelle + 6].toInt() and 0xFF) shl 16) or
                ((alles[stelle + 7].toInt() and 0xFF) shl 24)
            if (kennung == "data") {
                val bis = minOf(alles.size, stelle + 8 + laenge)
                return alles.copyOfRange(stelle + 8, bis)
            }
            stelle += 8 + laenge + (laenge and 1)
        }
        ByteArray(0)
    }.getOrElse { ByteArray(0) }

    private fun lauf(
        satzname: String,
        seite: Seite,
        pcm: ByteArray,
        vorgabeWorte: List<String>
    ): Lauf {
        var ersterText: Long? = null
        var bestaetigt: Long? = null
        var fehler: Int? = null
        val segmente = mutableListOf<String>()
        val lesarten = mutableListOf<String>()
        val teiltexte = mutableListOf<String>()
        val fertig = CountDownLatch(1)
        var erkenner: SpeechRecognizer? = null
        val (lesen, schreiben) = ParcelFileDescriptor.createPipe()
        val gestartet = CountDownLatch(1)
        var tonBeginn = 0L
        fun seitTon(): Long? =
            if (tonBeginn == 0L) null else SystemClock.elapsedRealtime() - tonBeginn

        hauptfaden.post {
            val neuer = runCatching {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(zusammenhang)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(zusammenhang)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(zusammenhang)
                }
            }.getOrNull()
            if (neuer == null) {
                gestartet.countDown(); fertig.countDown(); return@post
            }
            erkenner = neuer
            neuer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rms: Float) = Unit
                override fun onBufferReceived(b: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(code: Int) {
                    if (fehler == null) fehler = code
                    fertig.countDown()
                }
                override fun onResults(werte: Bundle?) {
                    val t = lies(werte)
                    if (t.isNotEmpty()) {
                        lesarten += t
                        bestaetigt = seitTon()
                        if (ersterText == null) ersterText = seitTon()
                    }
                    fertig.countDown()
                }
                override fun onPartialResults(werte: Bundle?) {
                    lies(werte).firstOrNull()?.let {
                        teiltexte += it
                        if (ersterText == null) ersterText = seitTon()
                    }
                }
                override fun onSegmentResults(werte: Bundle) {
                    lies(werte).firstOrNull()?.let {
                        segmente += it
                        bestaetigt = seitTon()
                        if (ersterText == null) ersterText = seitTon()
                    }
                }
                override fun onEndOfSegmentedSession() = fertig.countDown()
                override fun onEvent(art: Int, p: Bundle?) = Unit
            })
            runCatching { neuer.startListening(absicht(seite, lesen, vorgabeWorte)) }
                .onFailure { fertig.countDown() }
            gestartet.countDown()
        }
        gestartet.await(5, TimeUnit.SECONDS)

        val schreiber = thread(name = "still-$satzname") {
            runCatching {
                FileOutputStream(schreiben.fileDescriptor).use { strom ->
                    var stelle = 0
                    while (stelle < pcm.size) {
                        val menge = minOf(2048, pcm.size - stelle)
                        strom.write(pcm, stelle, menge)
                        if (tonBeginn == 0L) tonBeginn = SystemClock.elapsedRealtime()
                        stelle += menge
                        Thread.sleep(menge.toLong() * 1000 / (ABTASTRATE * 2))
                    }
                }
            }
            runCatching { schreiben.close() }
        }
        schreiber.join(60_000)
        fertig.await(20, TimeUnit.SECONDS)
        val ab = CountDownLatch(1)
        hauptfaden.post { runCatching { erkenner?.destroy() }; ab.countDown() }
        ab.await(5, TimeUnit.SECONDS)
        runCatching { lesen.close() }
        Thread.sleep(PAUSE_MILLIS)

        val wahl = Ergebniswahl.waehle(
            segmente = Ergebniswahl.ohneWiederholung(segmente),
            endergebnis = lesarten,
            zwischenstaende = teiltexte
        )
        return Lauf(satzname, seite.name, wahl.text, ersterText, bestaetigt, fehler)
    }

    private fun absicht(
        seite: Seite,
        lesen: ParcelFileDescriptor,
        vorgabeWorte: List<String>
    ) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, sprache)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sprache)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, lesen)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, ABTASTRATE)
        if (seite.segment) {
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        }
        if (seite.vorgabe && vorgabeWorte.isNotEmpty()) {
            putExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, vorgabeWorte.toTypedArray())
        }
    }

    private fun lies(werte: Bundle?): List<String> =
        werte?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty().filter { it.isNotBlank() }

    companion object {
        const val ABTASTRATE = 16_000
        const val PAUSE_MILLIS = 1_200L
        const val PAARE = 2
    }
}
