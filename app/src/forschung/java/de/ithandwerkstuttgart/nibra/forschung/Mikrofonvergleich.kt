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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Der letzte Beweis: **echte Stimme, echtes Mikrofon, beide Wege.**
 *
 * ```
 * ALT  Mikrofon ──▶ SpeechRecognizer
 * NEU  Mikrofon ──▶ AudioRecord ──▶ Vorlauf ──▶ Rohr ──▶ SpeechRecognizer
 * ```
 *
 * Alle bisherigen Zahlen stammen aus eingespeistem Ton. Sie beantworten,
 * ob die neue **Einstellung** dieselbe Aufnahme besser erkennt -- ja,
 * deutlich. Sie beantworten nicht, ob die vollständige Strecke am echten
 * Mikrofon besser ist. Genau dafür ist dieser Lauf da.
 *
 * **Geführt statt getippt.** Der Bildschirm zeigt den Satz, zählt herunter
 * und nimmt dann selbst auf. Wer zwischen 72 Diktaten jedes Mal tippen
 * müsste, hielte das Gerät jedes Mal anders -- und der Abstand zum Mund ist
 * eine der Bedingungen, die gleich bleiben sollen.
 *
 * **Die Reihenfolge wechselt.** Immer ALT zuerst hiesse, dem zweiten Weg
 * eine schon eingelesene Stimme und einen geladenen Dienst zu geben.
 */
class Mikrofonvergleich(
    private val zusammenhang: Context,
    private val sprache: String = "de-DE",
    private val aufStand: (Sprachlauf.Stand) -> Unit,
    /**
     * Was der Bildschirm **gerade wirklich** zeigt.
     *
     * Die Activity liest das aus ihrem eigenen Zustand zurück -- nicht aus
     * dem, was hier gesetzt wurde. Nur so ist der Abgleich einer: er geht
     * durch die Anzeige hindurch und kommt zurück.
     */
    private val gibAngezeigt: () -> Pair<String?, String?> = { null to null }
) {

    enum class Weg { ALT, NEU }

    data class Lauf(
        val testfall: Testfall,
        val satznummer: Int,
        val durchgang: Int,
        val weg: Weg,
        val text: String,
        val ersterTextMillis: Long?,
        val bestaetigtMillis: Long?,
        val fehler: Int?,
        val streckenbefund: Tonstrecke.Befund?
    )

    private val hauptfaden = Handler(Looper.getMainLooper())

    /** Wird geworfen, wenn Anzeige und Auswertung auseinanderlaufen. */
    class Auseinandergelaufen(meldung: String) : IllegalStateException(meldung)

    /**
     * Ein technischer Fehler -- Rohr, Aufnahme, Erkenner.
     *
     * Eigene Art, damit der Bericht nicht behauptet, Anzeige und Auswertung
     * seien auseinandergelaufen. Eine falsche Ursache ist schlimmer als
     * keine Ursache.
     */
    class Technischgescheitert(grund: Throwable) : IllegalStateException(
        "${grund.javaClass.simpleName}: ${grund.message}", grund
    )

    fun fuehreDurch(saetze: List<Testfall>, durchgaenge: Int): String = buildString {
        appendLine("MIKROFONVERGLEICH -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("Echte Stimme über das echte Mikrofon. ${saetze.size} Sätze,")
        appendLine("$durchgaenge Durchgänge je Satz, beide Wege = " +
            "${saetze.size * durchgaenge * 2} Diktate.")
        appendLine()
        appendLine("PRÜFSÄTZE -- Kennung und Fingerabdruck")
        saetze.forEach {
            appendLine("  ${it.id}  ${it.kategorie}  ${it.abdruck.take(16)}")
            appendLine("    ${it.text}")
        }
        appendLine()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appendLine("EXTRA_AUDIO_SOURCE gibt es erst ab Android 13. NICHT PRÜFBAR.")
            return@buildString
        }

        val laeufe = mutableListOf<Lauf>()
        saetze.forEachIndexed { stelle, satz ->
            (1..durchgaenge).forEach { durchgang ->
                // Reihenfolge wechselt über Satz **und** Durchgang, damit
                // kein Weg systematisch die frischere oder die müdere
                // Stimme bekommt.
                val reihe = if ((stelle + durchgang) % 2 == 0) {
                    listOf(Weg.ALT, Weg.NEU)
                } else {
                    listOf(Weg.NEU, Weg.ALT)
                }
                reihe.forEach { weg ->
                    // **Nur den Abgleichfehler fangen.** Ein runCatching um
                    // alles fing auch eine Ausnahme aus dem Rohr oder der
                    // Aufnahme und meldete sie als „Abgleich" -- eine falsche
                    // Auskunft über die Ursache.
                    val ergebnis = try {
                        Result.success(
                            lauf(stelle + 1, durchgang, weg, satz, saetze.size, durchgaenge))
                    } catch (grund: Auseinandergelaufen) {
                        Result.failure<Lauf>(grund)
                    } catch (grund: Exception) {
                        // Als das melden, was es ist -- und den Prozess
                        // nicht mitreissen. Die Freigabe hat im finally von
                        // lauf() bereits stattgefunden.
                        Result.failure<Lauf>(Technischgescheitert(grund))
                    }
                    ergebnis.exceptionOrNull()?.let { grund ->
                        appendLine()
                        val art = if (grund is Auseinandergelaufen) {
                            "Anzeige und Auswertung liefen auseinander"
                        } else {
                            "technischer Fehler"
                        }
                        appendLine("**ABGEBROCHEN** bei ${satz.id}, Durchgang " +
                            "$durchgang, ${weg.name} -- $art:")
                        appendLine("  ${grund.message}")
                        appendLine()
                        appendLine("Kein Diktat wurde aufgenommen. Erst den Aufbau prüfen.")
                        appendLine()
                        appendLine("**UNGÜLTIG -- Lauf abgebrochen.** ${laeufe.size} von " +
                            "${saetze.size * durchgaenge * 2} Diktaten. Ein Urteil aus einem")
                        appendLine("Bruchstück wäre genau die Sorte Zahl, die dieses Projekt")
                        appendLine("zehnmal in die Irre geführt hat -- deshalb steht hier keines.")
                        appendLine()
                        schreibeEinzeln(laeufe)
                        return@buildString
                    }
                    laeufe += ergebnis.getOrThrow()
                }
            }
        }

        aufStand(Sprachlauf.Stand("Mikrofonvergleich", "Fertig. Danke.", false, 0))
        schreibe(laeufe)
    }

    /**
     * Die einzelnen Diktate, ungekürzt. Eigene Funktion, weil auch der
     * abgebrochene Lauf sie braucht -- er aber kein Urteil bekommen darf.
     */
    private fun StringBuilder.schreibeEinzeln(laeufe: List<Lauf>) {
        appendLine("EINZELNE DIKTATE -- mit Kennung und Fingerabdruck des Bezugstextes")
        laeufe.forEach {
            appendLine("  ${it.testfall.id} D${it.durchgang} ${it.weg.name} " +
                "sha ${it.testfall.abdruck.take(16)}")
            appendLine("    Bezug:   ${it.testfall.text}")
            appendLine("    Erkannt: " +
                it.text.ifBlank { it.fehler?.let { f -> "(Fehler $f)" } ?: "(kein Text)" })
        }
        appendLine()
    }

    private fun StringBuilder.schreibe(laeufe: List<Lauf>) {
        schreibeEinzeln(laeufe)

        val guete = Weg.entries.associateWith { weg ->
            laeufe.filter { it.weg == weg && it.text.isNotBlank() }
                .map { Guetemasse.beurteile(it.testfall.text, it.text) }
        }
        appendLine("GÜTE (niedriger ist besser)")
        appendLine("  %-30s %-10s %-10s %s".format("Mass", "ALT", "NEU", "Unterschied"))
        listOf<Pair<String, (Guetemasse.Befund) -> Double>>(
            "rohe Wortfehlerrate" to { it.roheWortfehlerrate },
            "bereinigte Wortfehlerrate" to { it.bereinigteWortfehlerrate },
            "Zeichenfehlerrate" to { it.zeichenfehlerrate },
            "Auslassungen" to { it.auslassungsrate },
            "Einfügungen" to { it.einfuegungsrate }
        ).forEach { (name, holen) ->
            val w = Weg.entries.map { weg ->
                guete.getValue(weg).map(holen).let { if (it.isEmpty()) null else it.average() }
            }
            appendLine("  %-30s %-10s %-10s %s".format(
                name,
                w[0]?.let { "%.1f %%".format(it * 100) } ?: "-",
                w[1]?.let { "%.1f %%".format(it * 100) } ?: "-",
                if (w[0] != null && w[1] != null)
                    "%+.1f Punkte".format((w[1]!! - w[0]!!) * 100) else "-"))
        }
        listOf<Pair<String, (Guetemasse.Befund) -> Int>>(
            "Verlust am Satzanfang" to { it.verlustAmAnfang },
            "Verlust am Satzende" to { it.verlustAmEnde }
        ).forEach { (name, holen) ->
            val w = Weg.entries.map { weg ->
                guete.getValue(weg).map { holen(it).toDouble() }
                    .let { if (it.isEmpty()) null else it.average() }
            }
            appendLine("  %-30s %-10s %-10s %s".format(
                name,
                w[0]?.let { "%.2f".format(it) } ?: "-",
                w[1]?.let { "%.2f".format(it) } ?: "-",
                if (w[0] != null && w[1] != null) "%+.2f".format(w[1]!! - w[0]!!) else "-"))
        }
        appendLine()

        val klassen = Wortklassen.FUER_MIKROFON
        val kb = Weg.entries.associateWith { weg ->
            laeufe.filter { it.weg == weg && it.text.isNotBlank() }.map {
                Fehlerarten.beurteile(
                    Wortvergleich.vergleiche(it.testfall.text, it.text), it.testfall.text, klassen)
            }
        }
        appendLine("TREFFERQUOTE JE WORTKLASSE (höher ist besser)")
        appendLine("  %-16s %-10s %-10s %s".format("Klasse", "ALT", "NEU", "Unterschied"))
        Fehlerarten.Klasse.entries.forEach { klasse ->
            val w = Weg.entries.map { weg ->
                kb.getValue(weg).mapNotNull { b -> b.jeKlasse.first { it.klasse == klasse }.quote }
                    .let { if (it.isEmpty()) null else it.average() }
            }
            appendLine("  %-16s %-10s %-10s %s".format(
                klasse.name,
                w[0]?.let { "%.0f %%".format(it * 100) } ?: "-",
                w[1]?.let { "%.0f %%".format(it * 100) } ?: "-",
                if (w[0] != null && w[1] != null)
                    "%+.0f Punkte".format((w[1]!! - w[0]!!) * 100) else "-"))
        }
        val erf = Weg.entries.map { weg ->
            kb.getValue(weg).map { it.erfunden.toDouble() }
                .let { if (it.isEmpty()) null else it.average() }
        }
        appendLine("  %-16s %-10s %-10s %s".format("erfundene Wörter",
            erf[0]?.let { "%.2f".format(it) } ?: "-",
            erf[1]?.let { "%.2f".format(it) } ?: "-",
            if (erf[0] != null && erf[1] != null) "%+.2f".format(erf[1]!! - erf[0]!!) else "-"))
        appendLine()

        // Je Diktatpaar: derselbe Satz, derselbe Durchgang, beide Wege.
        appendLine("JE DIKTAT -- wer gewinnt?")
        var besser = 0; var gleich = 0; var schlechter = 0
        val auffaellig = mutableListOf<String>()
        laeufe.map { it.satznummer to it.durchgang }.distinct().forEach { (satz, durchgang) ->
            val a = laeufe.firstOrNull {
                it.satznummer == satz && it.durchgang == durchgang && it.weg == Weg.ALT }
            val n = laeufe.firstOrNull {
                it.satznummer == satz && it.durchgang == durchgang && it.weg == Weg.NEU }
            if (a == null || n == null) return@forEach
            val ra = Guetemasse.beurteile(a.testfall.text, a.text).bereinigteWortfehlerrate
            val rn = Guetemasse.beurteile(n.testfall.text, n.text).bereinigteWortfehlerrate
            when {
                rn < ra - 0.0001 -> { besser++
                    auffaellig += "    Satz $satz D$durchgang: ALT %.0f %% -> NEU %.0f %%"
                        .format(ra * 100, rn * 100) + "\n      Bezug: ${a.testfall.text}" +
                        "\n      ALT: ${a.text}\n      NEU: ${n.text}" }
                rn > ra + 0.0001 -> { schlechter++
                    auffaellig += "    Satz $satz D$durchgang: ALT %.0f %% -> NEU %.0f %%"
                        .format(ra * 100, rn * 100) + "\n      Bezug: ${a.testfall.text}" +
                        "\n      ALT: ${a.text}\n      NEU: ${n.text}" }
                else -> gleich++
            }
        }
        appendLine("  NEU besser:      $besser")
        appendLine("  Gleichstand:     $gleich")
        appendLine("  NEU schlechter:  $schlechter")
        if (auffaellig.isNotEmpty()) {
            appendLine()
            appendLine("  Diktate mit Unterschied, ungekürzt:")
            auffaellig.forEach { appendLine(it) }
        }
        appendLine()

        appendLine("ZEITEN, AUSBEUTE UND FEHLER")
        Weg.entries.forEach { weg ->
            val alle = laeufe.filter { it.weg == weg }
            val erster = alle.mapNotNull { it.ersterTextMillis }
            val fertig = alle.mapNotNull { it.bestaetigtMillis }
            appendLine("  ${weg.name}: erster Text P50 ${Kennzahlen.perzentil(erster, 0.5) ?: "-"} ms, " +
                "bestätigt P50 ${Kennzahlen.perzentil(fertig, 0.5) ?: "-"} ms")
            appendLine("    mit Text ${alle.count { it.text.isNotBlank() }} von ${alle.size}, " +
                "leer ${alle.count { it.text.isBlank() }}, " +
                "Erkennerfehler ${alle.count { it.fehler != null }}")
        }
        val befunde = laeufe.mapNotNull { it.streckenbefund }
        if (befunde.isNotEmpty()) {
            appendLine("  NEU, Ton: verworfene Blöcke ${befunde.sumOf { it.verworfeneBloecke }}, " +
                "größte Warteschlange ${befunde.maxOf { it.groessteWarteschlange }}, " +
                "Lesefehler ${befunde.sumOf { it.leseFehler }}")
        }
        appendLine()

        appendLine("URTEIL")
        val raten = Weg.entries.map { weg ->
            guete.getValue(weg).map { it.bereinigteWortfehlerrate }
        }
        if (raten.any { it.isEmpty() }) {
            appendLine("  Ein Weg lieferte keinen Text. Kein Vergleich.")
            return
        }
        val a = raten[0].average(); val n = raten[1].average()
        appendLine("  bereinigte Wortfehlerrate ALT %.1f %%, NEU %.1f %%".format(a * 100, n * 100))
        appendLine("  " + when {
            n < a * 0.95 -> "**NEU erkennt besser.**"
            n <= a * 1.05 -> "**Gleichstand** -- unter fünf Prozent der Rate."
            else -> "**NEU erkennt schlechter.**"
        })
        appendLine()
        appendLine("  Gilt für dieses Gerät, diese Stimme, diesen Raum. Für andere")
        appendLine("  Geräte sagt dieser Lauf nichts.")
    }

    private fun lauf(
        satznummer: Int,
        durchgang: Int,
        weg: Weg,
        testfall: Testfall,
        saetzeGesamt: Int,
        durchgaenge: Int
    ): Lauf {
        val kopf = "Satz $satznummer/$saetzeGesamt · Durchgang $durchgang/$durchgaenge · ${weg.name}"
        // Zeit zum Lesen und Luftholen, bevor aufgenommen wird.
        (VORLAUF_SEKUNDEN downTo 1).forEach { rest ->
            aufStand(Sprachlauf.Stand(kopf, "Gleich vorlesen …", false, rest, testfall))
            Thread.sleep(1_000)
        }

        // **Abgleich, bevor irgendetwas aufnimmt.**
        //
        // Gefragt wird die Anzeige, nicht die eigene Vorlage: was steht
        // wirklich auf dem Bildschirm? Stimmen Kennung und Fingerabdruck
        // nicht mit dem überein, was gleich bewertet wird, bricht der Lauf
        // ab -- vor der Aufnahme, vor dem Erkenner, vor jedem gesprochenen
        // Wort. Genau daran ist der letzte Lauf gescheitert: der Bildschirm
        // zeigte etwas anderes, als die Auswertung erwartete, und niemand
        // hat es gemerkt.
        val (kennung, text) = gibAngezeigt()
        Testfall.abgleich(kennung, text, testfall)?.let { throw Auseinandergelaufen(it) }

        var ersterText: Long? = null
        var bestaetigt: Long? = null
        var fehler: Int? = null
        val segmente = mutableListOf<String>()
        val lesarten = mutableListOf<String>()
        val teiltexte = mutableListOf<String>()
        val fertig = CountDownLatch(1)
        var erkenner: SpeechRecognizer? = null
        var strecke: Tonstrecke? = null
        var befund: Tonstrecke.Befund? = null
        val nullpunkt = SystemClock.elapsedRealtime()
        fun jetzt() = SystemClock.elapsedRealtime() - nullpunkt

        var lesen: ParcelFileDescriptor? = null
        var schreiben: ParcelFileDescriptor? = null
        // **Freigabe unabhängig davon, wie der Lauf endet.**
        //
        // Negativkontrolle 3 hat gezeigt: eine technische Ausnahme riss den
        // Prozess mit, ohne Aufnahme und Rohr zu schliessen und ohne einen
        // Bericht zu hinterlassen. Der nächste Lauf hätte einen Neustart der
        // App gebraucht -- genau das, was diese Strecke nie verlangen soll.
        try {
        if (weg == Weg.NEU) {
            val rohr = ParcelFileDescriptor.createPipe()
            lesen = rohr[0]; schreiben = rohr[1]
            strecke = Tonstrecke(ABTASTRATE, VORLAUF_MILLIS).also { it.starte() }
        } else {
            lesen = null; schreiben = null
        }

        val gestartet = CountDownLatch(1)
        hauptfaden.post {
            val neuer = runCatching {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(zusammenhang)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(zusammenhang)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(zusammenhang)
                }
            }.getOrNull()
            if (neuer == null) { gestartet.countDown(); fertig.countDown(); return@post }
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
                        lesarten += t; bestaetigt = jetzt()
                        if (ersterText == null) ersterText = jetzt()
                    }
                    fertig.countDown()
                }
                override fun onPartialResults(werte: Bundle?) {
                    lies(werte).firstOrNull()?.let {
                        teiltexte += it
                        if (ersterText == null) ersterText = jetzt()
                    }
                }
                override fun onSegmentResults(werte: Bundle) {
                    lies(werte).firstOrNull()?.let {
                        segmente += it; bestaetigt = jetzt()
                        if (ersterText == null) ersterText = jetzt()
                    }
                }
                override fun onEndOfSegmentedSession() = fertig.countDown()
                override fun onEvent(art: Int, p: Bundle?) = Unit
            })
            runCatching { neuer.startListening(absicht(weg, lesen)) }
                .onFailure { fertig.countDown() }
            gestartet.countDown()
        }
        gestartet.await(5, TimeUnit.SECONDS)
        schreiben?.let { strecke?.speiseIn(it, mitVorlauf = true) }

        // Sprechzeit nach Satzlänge, mit Zugabe. Zu knapp bemessen schnitte
        // das Ende ab und belastete beide Wege gleich -- aber es wäre ein
        // Fehler, der wie ein Erkennungsfehler aussieht.
        val worte = testfall.text.split(" ").size
        val sekunden = (worte * MILLIS_JE_WORT / 1000 + ZUGABE_SEKUNDEN).toInt()
        (sekunden downTo 1).forEach { rest ->
            aufStand(Sprachlauf.Stand(kopf, "JETZT vorlesen", true, rest, testfall))
            Thread.sleep(1_000)
        }
        aufStand(Sprachlauf.Stand(kopf, "Danke.", false, 0, testfall))

        strecke?.beendeEinspeisung()
        befund = strecke?.halteAn()
        runCatching { schreiben?.close() }
        if (weg == Weg.ALT) hauptfaden.post { runCatching { erkenner?.stopListening() } }
        fertig.await(20, TimeUnit.SECONDS)
        val ab = CountDownLatch(1)
        hauptfaden.post { runCatching { erkenner?.destroy() }; ab.countDown() }
        ab.await(5, TimeUnit.SECONDS)
        runCatching { lesen?.close() }
        Thread.sleep(PAUSE_MILLIS)

        val wahl = Ergebniswahl.waehle(
            segmente = Ergebniswahl.ohneWiederholung(segmente),
            endergebnis = lesarten,
            zwischenstaende = teiltexte
        )
        return Lauf(testfall, satznummer, durchgang, weg, wahl.text,
            ersterText, bestaetigt, fehler, befund)
        } finally {
            runCatching { strecke?.beendeEinspeisung() }
            runCatching { strecke?.halteAn() }
            runCatching { schreiben?.close() }
            runCatching { lesen?.close() }
            val abgeraeumt = CountDownLatch(1)
            hauptfaden.post {
                runCatching { erkenner?.destroy() }
                abgeraeumt.countDown()
            }
            abgeraeumt.await(5, TimeUnit.SECONDS)
        }
    }

    /** ALT bekommt genau die Absicht, die Nibra heute stellt. */
    private fun absicht(weg: Weg, lesen: ParcelFileDescriptor?) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sprache)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sprache)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
            if (weg == Weg.NEU && lesen != null) {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, lesen)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                    AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, ABTASTRATE)
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_AUDIO_SOURCE)
            }
        }

    private fun lies(werte: Bundle?): List<String> =
        werte?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty().filter { it.isNotBlank() }

    companion object {
        const val ABTASTRATE = 16_000
        const val VORLAUF_MILLIS = 1_500
        const val VORLAUF_SEKUNDEN = 3
        const val ZUGABE_SEKUNDEN = 4
        const val MILLIS_JE_WORT = 550L
        const val PAUSE_MILLIS = 1_200L
        const val DURCHGAENGE = 3
    }
}
