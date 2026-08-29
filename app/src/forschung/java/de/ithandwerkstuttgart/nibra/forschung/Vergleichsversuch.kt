package de.ithandwerkstuttgart.nibra.forschung

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaPlayer
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Der eigentliche Vergleich: **alter Weg gegen neuen Weg**, am selben Ton.
 *
 * ```
 * ALT   Mikrofon ──▶ SpeechRecognizer
 * NEU   Mikrofon ──▶ AudioRecord ──▶ Vorlauf ──▶ Rohr ──▶ SpeechRecognizer
 * ```
 *
 * Beide hören dieselbe Aufnahme, über den Lautsprecher abgespielt. Das ist
 * schlechter als eingespeister Ton -- der Raum hört mit -- aber es ist für
 * **beide Wege gleich schlecht**, und nur darauf kommt es an. Eingespeister
 * Ton wäre hier unmöglich: der alte Weg kann gar nichts anderes als das
 * Mikrofon.
 *
 * Die Durchgänge wechseln sich ab. Nacheinander in Blöcken gemessen läge ein
 * Unterschied womöglich daran, dass das Gerät im zweiten Block wärmer oder
 * der Dienst inzwischen geladen war.
 *
 * Verglichen wird nicht nur die Gesamtfehlerrate, sondern nach Klassen --
 * gewöhnliche Wörter, Eigennamen, Fachbegriffe, Zahlen -- dazu fehlende und
 * erfundene Wörter, Satzanfang, Satzende und die Zeit bis zum ersten
 * sichtbaren Text. Eine Gesamtrate beantwortet die Produktfrage nicht: zehn
 * fehlende Füllwörter und jeder Eigenname falsch ergeben dieselbe Zahl und
 * sind für den Nutzer zwei völlig verschiedene Dinge.
 */
class Vergleichsversuch(
    private val zusammenhang: Context,
    private val sprache: String = "de-DE",
    private val aufStand: (String) -> Unit
) {

    enum class Weg { ALT, NEU }

    data class Lauf(
        val nummer: Int,
        val satzname: String,
        val weg: Weg,
        val text: String,
        val ersterTextMillis: Long?,
        val bestaetigtMillis: Long?,
        val fehler: Int?,
        val streckenbefund: Tonstrecke.Befund?
    )

    private val hauptfaden = Handler(Looper.getMainLooper())

    /** Ein Prüfsatz: Aufnahme und der Text, der darin gesprochen wird. */
    data class Pruefsatz(val name: String, val aufnahme: File, val bezugstext: String)

    fun fuehreDurch(
        saetze: List<Pruefsatz>,
        klassen: Map<String, Fehlerarten.Klasse>,
        paare: Int,
        /** Nur mit ausdrücklicher Freigabe wird hörbar abgespielt. */
        tonErlaubt: Boolean,
        mitVorgabe: Boolean = false,
        vorgabeWorte: List<String> = emptyList()
    ): String = buildString {
        appendLine("VERGLEICH ALT GEGEN NEU -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("Beide Wege hören dieselbe Aufnahme über den Lautsprecher.")
        appendLine("${saetze.size} Prüfsätze, $paare Paare je Satz.")
        if (mitVorgabe) appendLine("Vorgabeliste an: ${vorgabeWorte.joinToString(", ")}")
        appendLine()
        saetze.forEach { appendLine("  ${it.name}: ${it.bezugstext}") }
        appendLine()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appendLine("EXTRA_AUDIO_SOURCE gibt es erst ab Android 13. NICHT PRÜFBAR.")
            return@buildString
        }

        // **Ohne ausdrückliche Freigabe wird nichts abgespielt.**
        //
        // Dieser Versuch macht Lärm -- er muss, weil der alte Weg nichts
        // anderes als das Mikrofon kennt. Um fünf Uhr morgens hat er ein
        // stumm gestelltes Gerät zum Sprechen gebracht, weil er einfach
        // loslegte. Ein Versuch, der hörbar wird, darf nicht die Vorgabe
        // sein; er braucht ein ausdrückliches Ja.
        if (!tonErlaubt) {
            appendLine("**Nicht gestartet.** Dieser Versuch spielt die Prüfsätze über den")
            appendLine("Lautsprecher ab. Er läuft nur mit ausdrücklicher Freigabe")
            appendLine("(`--ez tonErlaubt true`).")
            appendLine()
            appendLine("Ohne Lärm messbar ist der Stillvergleich: derselbe Ton, direkt")
            appendLine("eingespeist, ohne Lautsprecher und ohne Mikrofon.")
            return@buildString
        }

        // **Die Lautstärke wird gelesen, nicht gesetzt.**
        //
        // Die erste Fassung stellte sie selbst auf vier Fünftel des
        // Höchstwertes. Das hat um fünf Uhr morgens ein stumm gestelltes
        // Gerät wieder laut gedreht -- eine Einstellung des Nutzers
        // überschrieben, für eine Messung. Das darf ein Versuch nicht.
        //
        // Ist es zu leise, wird nicht gemessen. Eine Messung bei Stille
        // liefert kein „gleich gut", sondern gar kein Ergebnis, und beide
        // Wege sähen falsch aus.
        val toene = zusammenhang.getSystemService(AudioManager::class.java)
        val lautstaerke = toene?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val hoechste = toene?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
        val anteil = lautstaerke.toDouble() / hoechste
        appendLine("Lautstärke am Gerät: $lautstaerke von $hoechste")
        if (anteil < MINDESTLAUTSTAERKE) {
            appendLine()
            appendLine("**Zu leise für diese Messung.** Sie spielt die Prüfsätze über den")
            appendLine("Lautsprecher ab, weil der alte Weg nichts anderes als das Mikrofon")
            appendLine("kennt. Unter ${(MINDESTLAUTSTAERKE * 100).toInt()} Prozent hört das Mikrofon zu wenig, und das")
            appendLine("Ergebnis wäre keine Aussage über die Wege, sondern über die")
            appendLine("Lautstärke.")
            appendLine()
            appendLine("Die Lautstärke wird **nicht** von selbst verstellt. Wer messen will,")
            appendLine("stellt sie ein.")
            return@buildString
        }
        appendLine()

        val laeufe = mutableListOf<Lauf>()
        saetze.forEach { satz ->
                (1..paare).forEach { paar ->
                    // **Reihenfolge wechselt.** Immer denselben Weg zuerst
                    // zu fahren hiesse, dem zweiten ein wärmeres Gerät und
                    // einen geladenen Dienst zu geben. Der Unterschied läge
                    // dann teilweise in der Reihenfolge statt im Weg.
                    val reihe = if (paar % 2 == 1) {
                        listOf(Weg.ALT, Weg.NEU)
                    } else {
                        listOf(Weg.NEU, Weg.ALT)
                    }
                    reihe.forEach { weg ->
                        aufStand("${satz.name} $paar/$paare, ${weg.name}")
                        laeufe += lauf(
                            laeufe.size + 1, satz.name, weg, satz.aufnahme,
                            mitVorgabe, vorgabeWorte
                        )
                    }
                }
        }

        val bezuege = saetze.associate { it.name to it.bezugstext }
        schreibeEinzeln(laeufe)
        schreibeGuete(laeufe, bezuege)
        schreibeKlassen(laeufe, bezuege, klassen)
        schreibeJeSatz(laeufe, bezuege)
        schreibeUrteil(laeufe, bezuege)
    }

    private fun StringBuilder.schreibeEinzeln(laeufe: List<Lauf>) {
        appendLine("EINZELNE LÄUFE")
        laeufe.forEach { l ->
            appendLine("  %-3d %-11s %-4s %s".format(l.nummer, l.satzname, l.weg.name,
                l.text.ifBlank { l.fehler?.let { "(Fehler $it)" } ?: "(kein Text)" }.take(80)))
        }
        appendLine()
    }

    private fun StringBuilder.schreibeGuete(
        laeufe: List<Lauf>,
        bezuege: Map<String, String>
    ) {
        appendLine("GÜTE -- Mittel über alle Prüfsätze (niedriger ist besser)")
        appendLine("  %-30s %-10s %-10s %s".format("Mass", "ALT", "NEU", "Unterschied"))
        val befunde = Weg.entries.associateWith { weg ->
            laeufe.filter { it.weg == weg && it.text.isNotBlank() }
                .map { Guetemasse.beurteile(bezuege.getValue(it.satzname), it.text) }
        }
        listOf<Pair<String, (Guetemasse.Befund) -> Double>>(
            "rohe Wortfehlerrate" to { it.roheWortfehlerrate },
            "bereinigte Wortfehlerrate" to { it.bereinigteWortfehlerrate },
            "Zeichenfehlerrate" to { it.zeichenfehlerrate },
            "Auslassungen" to { it.auslassungsrate },
            "Einfügungen" to { it.einfuegungsrate }
        ).forEach { (name, holen) ->
            val werte = Weg.entries.map { weg ->
                befunde.getValue(weg).map(holen).let { if (it.isEmpty()) null else it.average() }
            }
            appendLine("  %-30s %-10s %-10s %s".format(
                name,
                werte[0]?.let { "%.1f %%".format(it * 100) } ?: "-",
                werte[1]?.let { "%.1f %%".format(it * 100) } ?: "-",
                if (werte[0] != null && werte[1] != null)
                    "%+.1f Punkte".format((werte[1]!! - werte[0]!!) * 100) else "-"
            ))
        }
        listOf<Pair<String, (Guetemasse.Befund) -> Int>>(
            "Verlust am Satzanfang (Wörter)" to { it.verlustAmAnfang },
            "Verlust am Satzende (Wörter)" to { it.verlustAmEnde }
        ).forEach { (name, holen) ->
            val werte = Weg.entries.map { weg ->
                befunde.getValue(weg).map { holen(it).toDouble() }
                    .let { if (it.isEmpty()) null else it.average() }
            }
            appendLine("  %-30s %-10s %-10s %s".format(
                name,
                werte[0]?.let { "%.2f".format(it) } ?: "-",
                werte[1]?.let { "%.2f".format(it) } ?: "-",
                if (werte[0] != null && werte[1] != null)
                    "%+.2f".format(werte[1]!! - werte[0]!!) else "-"
            ))
        }
        appendLine()
    }

    private fun StringBuilder.schreibeKlassen(
        laeufe: List<Lauf>,
        bezuege: Map<String, String>,
        klassen: Map<String, Fehlerarten.Klasse>
    ) {
        val je = Weg.entries.associateWith { weg ->
            laeufe.filter { it.weg == weg && it.text.isNotBlank() }.map {
                val bezug = bezuege.getValue(it.satzname)
                Fehlerarten.beurteile(Wortvergleich.vergleiche(bezug, it.text), bezug, klassen)
            }
        }
        appendLine("TREFFERQUOTE JE WORTKLASSE (höher ist besser)")
        appendLine("  %-16s %-10s %-10s %s".format("Klasse", "ALT", "NEU", "Unterschied"))
        Fehlerarten.Klasse.entries.forEach { klasse ->
            val werte = Weg.entries.map { weg ->
                je.getValue(weg).mapNotNull { b ->
                    b.jeKlasse.first { it.klasse == klasse }.quote
                }.let { if (it.isEmpty()) null else it.average() }
            }
            appendLine("  %-16s %-10s %-10s %s".format(
                klasse.name,
                werte[0]?.let { "%.0f %%".format(it * 100) } ?: "-",
                werte[1]?.let { "%.0f %%".format(it * 100) } ?: "-",
                if (werte[0] != null && werte[1] != null)
                    "%+.0f Punkte".format((werte[1]!! - werte[0]!!) * 100) else "-"
            ))
        }
        val erfunden = Weg.entries.map { weg ->
            je.getValue(weg).map { it.erfunden.toDouble() }
                .let { if (it.isEmpty()) null else it.average() }
        }
        appendLine("  %-16s %-10s %-10s %s".format(
            "erfundene Wörter",
            erfunden[0]?.let { "%.2f".format(it) } ?: "-",
            erfunden[1]?.let { "%.2f".format(it) } ?: "-",
            if (erfunden[0] != null && erfunden[1] != null)
                "%+.2f".format(erfunden[1]!! - erfunden[0]!!) else "-"))
        appendLine()
    }

    private fun StringBuilder.schreibeJeSatz(laeufe: List<Lauf>, bezuege: Map<String, String>) {
        appendLine("JE PRÜFSATZ -- bereinigte Wortfehlerrate")
        appendLine("  %-12s %-10s %-10s %s".format("Satz", "ALT", "NEU", "Läufe mit Text"))
        bezuege.keys.forEach { name ->
            val werte = Weg.entries.map { weg ->
                laeufe.filter { it.satzname == name && it.weg == weg && it.text.isNotBlank() }
                    .map { Guetemasse.beurteile(bezuege.getValue(name), it.text)
                        .bereinigteWortfehlerrate }
            }
            appendLine("  %-12s %-10s %-10s %d / %d".format(
                name,
                werte[0].takeIf { it.isNotEmpty() }?.let { "%.1f %%".format(it.average() * 100) } ?: "-",
                werte[1].takeIf { it.isNotEmpty() }?.let { "%.1f %%".format(it.average() * 100) } ?: "-",
                werte[0].size, werte[1].size
            ))
        }
        appendLine()
    }

    private fun StringBuilder.schreibeUrteil(laeufe: List<Lauf>, bezuege: Map<String, String>) {
        appendLine("ZEITEN")
        Weg.entries.forEach { weg ->
            val ersterText = laeufe.filter { it.weg == weg }.mapNotNull { it.ersterTextMillis }
            val bestaetigt = laeufe.filter { it.weg == weg }.mapNotNull { it.bestaetigtMillis }
            appendLine("  ${weg.name}: erster Text P50 ${Kennzahlen.perzentil(ersterText, 0.5) ?: "-"} ms, " +
                "bestätigt P50 ${Kennzahlen.perzentil(bestaetigt, 0.5) ?: "-"} ms, " +
                "${laeufe.count { it.weg == weg && it.text.isNotBlank() }} von " +
                "${laeufe.count { it.weg == weg }} mit Text")
        }
        appendLine()

        appendLine("TON DER NEUEN STRECKE")
        val befunde = laeufe.mapNotNull { it.streckenbefund }
        if (befunde.isEmpty()) appendLine("  keiner erhoben") else {
            appendLine("  verworfene Blöcke gesamt ${befunde.sumOf { it.verworfeneBloecke }}, " +
                "größte Warteschlange ${befunde.maxOf { it.groessteWarteschlange }}, " +
                "Lesefehler ${befunde.sumOf { it.leseFehler }}")
        }
        appendLine()

        appendLine("URTEIL")
        val raten = Weg.entries.map { weg ->
            laeufe.filter { it.weg == weg && it.text.isNotBlank() }
                .map { Guetemasse.beurteile(bezuege.getValue(it.satzname), it.text)
                    .bereinigteWortfehlerrate }
        }
        if (raten.any { it.isEmpty() }) {
            appendLine("  Ein Weg lieferte keinen Text. Kein Vergleich.")
            return
        }
        val alt = raten[0].average()
        val neu = raten[1].average()
        appendLine("  bereinigte Wortfehlerrate ALT %.1f %%, NEU %.1f %%".format(alt * 100, neu * 100))
        appendLine("  " + when {
            neu < alt * 0.95 -> "**Der neue Weg erkennt besser.**"
            neu <= alt * 1.05 -> "**Gleichstand.** Der Unterschied liegt unter fünf Prozent " +
                "der Rate -- damit behält der neue Weg seine belegten Vorteile, ohne " +
                "Qualität zu kosten."
            else -> "**Der neue Weg erkennt schlechter.** Die Vorteile beim Transport " +
                "wiegen das nicht auf."
        })
        appendLine()
        appendLine("  Gilt für diese Stimme, diese Sätze, dieses Gerät und den Weg über")
        appendLine("  den Lautsprecher. Nichts davon sagt etwas über andere Sprecher.")
    }

    private fun lauf(
        nummer: Int,
        satzname: String,
        weg: Weg,
        aufnahme: File,
        mitVorgabe: Boolean,
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
        var strecke: Tonstrecke? = null
        var befund: Tonstrecke.Befund? = null
        val nullpunkt = SystemClock.elapsedRealtime()
        fun jetzt() = SystemClock.elapsedRealtime() - nullpunkt

        val lesen: ParcelFileDescriptor?
        val schreiben: ParcelFileDescriptor?
        if (weg == Weg.NEU) {
            val rohr = ParcelFileDescriptor.createPipe()
            lesen = rohr[0]
            schreiben = rohr[1]
            strecke = Tonstrecke(ABTASTRATE, VORLAUF_MILLIS).also { it.starte() }
        } else {
            lesen = null
            schreiben = null
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
            if (neuer == null) {
                gestartet.countDown()
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
                    if (fehler == null) fehler = code
                    fertig.countDown()
                }
                override fun onResults(werte: Bundle?) {
                    val t = lies(werte)
                    if (t.isNotEmpty()) {
                        lesarten += t
                        bestaetigt = jetzt()
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
                        segmente += it
                        bestaetigt = jetzt()
                        if (ersterText == null) ersterText = jetzt()
                    }
                }
                override fun onEndOfSegmentedSession() = fertig.countDown()
                override fun onEvent(art: Int, p: Bundle?) = Unit
            })
            runCatching { neuer.startListening(absicht(weg, lesen, mitVorgabe, vorgabeWorte)) }
                .onFailure { fertig.countDown() }
            gestartet.countDown()
        }
        gestartet.await(5, TimeUnit.SECONDS)

        // Erst die Erkennung anlaufen lassen, dann abspielen -- sonst
        // beginnt der Ton, bevor jemand zuhört, und beide Wege verlören
        // ihren Anfang. Beim neuen Weg fängt der Vorlauf das ohnehin ab;
        // dem alten stünde diese Hilfe nicht zu, und der Vergleich wäre
        // zugunsten des neuen Wegs verzerrt.
        Thread.sleep(600)
        schreiben?.let { strecke?.speiseIn(it, mitVorlauf = true) }

        val spieler = MediaPlayer()
        runCatching {
            spieler.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            spieler.setDataSource(aufnahme.absolutePath)
            spieler.prepare()
            spieler.start()
            while (spieler.isPlaying) Thread.sleep(100)
        }
        runCatching { spieler.release() }

        // Nachlauf, damit der Erkenner den letzten Satz abschließen kann.
        Thread.sleep(NACHLAUF_MILLIS)
        strecke?.beendeEinspeisung()
        befund = strecke?.halteAn()
        runCatching { schreiben?.close() }
        if (weg == Weg.ALT) hauptfaden.post { runCatching { erkenner?.stopListening() } }
        fertig.await(20, TimeUnit.SECONDS)

        val abgeraeumt = CountDownLatch(1)
        hauptfaden.post {
            runCatching { erkenner?.destroy() }
            abgeraeumt.countDown()
        }
        abgeraeumt.await(5, TimeUnit.SECONDS)
        runCatching { lesen?.close() }
        Thread.sleep(PAUSE_MILLIS)

        val wahl = Ergebniswahl.waehle(
            segmente = Ergebniswahl.ohneWiederholung(segmente),
            endergebnis = lesarten,
            zwischenstaende = teiltexte
        )
        return Lauf(nummer, satzname, weg, wahl.text, ersterText, bestaetigt, fehler, befund)
    }

    /**
     * Der alte Weg bekommt **genau die Absicht, die Nibra heute stellt** --
     * ohne Tonquelle, ohne Segmentsitzung. Ihm den neuen Aufbau zu geben
     * hiesse, den alten Weg nicht zu messen.
     */
    private fun absicht(
        weg: Weg,
        lesen: ParcelFileDescriptor?,
        mitVorgabe: Boolean = false,
        vorgabeWorte: List<String> = emptyList()
    ) =
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
            if (mitVorgabe && vorgabeWorte.isNotEmpty()) {
                putExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, vorgabeWorte.toTypedArray())
            }
            if (weg == Weg.NEU && lesen != null) {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, lesen)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, ABTASTRATE)
                putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_AUDIO_SOURCE
                )
            }
        }

    private fun lies(werte: Bundle?): List<String> =
        werte?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty().filter { it.isNotBlank() }

    companion object {
        const val ABTASTRATE = 16_000
        const val VORLAUF_MILLIS = 1_500
        const val NACHLAUF_MILLIS = 2_500L
        const val PAUSE_MILLIS = 1_500L
        const val PAARE = 5

        /** Darunter wird nicht gemessen, sondern abgebrochen. */
        const val MINDESTLAUTSTAERKE = 0.4
    }
}
