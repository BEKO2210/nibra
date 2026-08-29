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
        val weg: Weg,
        val text: String,
        val ersterTextMillis: Long?,
        val bestaetigtMillis: Long?,
        val fehler: Int?,
        val streckenbefund: Tonstrecke.Befund?
    )

    private val hauptfaden = Handler(Looper.getMainLooper())

    fun fuehreDurch(
        aufnahme: File,
        bezugstext: String,
        klassen: Map<String, Fehlerarten.Klasse>,
        paare: Int
    ): String = buildString {
        appendLine("VERGLEICH ALT GEGEN NEU -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("Beide Wege hören dieselbe Aufnahme über den Lautsprecher.")
        appendLine("$paare Paare, abwechselnd alt und neu.")
        appendLine()
        appendLine("Bezugstext: $bezugstext")
        appendLine()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appendLine("EXTRA_AUDIO_SOURCE gibt es erst ab Android 13. NICHT PRÜFBAR.")
            return@buildString
        }

        val toene = zusammenhang.getSystemService(AudioManager::class.java)
        val vorherigeLautstaerke = toene?.getStreamVolume(AudioManager.STREAM_MUSIC)
        val hoechste = toene?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
        // Ohne feste Lautstärke misst der Vergleich mit, wie laut das Gerät
        // gerade stand. Sie wird am Ende zurückgestellt.
        runCatching {
            toene?.setStreamVolume(AudioManager.STREAM_MUSIC, (hoechste * 4) / 5, 0)
        }
        appendLine("Lautstärke für die Messung: ${(hoechste * 4) / 5} von $hoechste " +
            "(vorher $vorherigeLautstaerke, wird zurückgestellt)")
        appendLine()

        val laeufe = mutableListOf<Lauf>()
        try {
            (1..paare).forEach { paar ->
                Weg.entries.forEach { weg ->
                    aufStand("Paar $paar von $paare, Weg ${weg.name}")
                    laeufe += lauf(laeufe.size + 1, weg, aufnahme)
                }
            }
        } finally {
            vorherigeLautstaerke?.let {
                runCatching { toene?.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) }
            }
        }

        schreibeEinzeln(laeufe)
        schreibeVergleich(laeufe, bezugstext, klassen)
    }

    private fun StringBuilder.schreibeEinzeln(laeufe: List<Lauf>) {
        appendLine("EINZELNE LÄUFE")
        laeufe.forEach { l ->
            appendLine("  %-3d %-4s %s".format(l.nummer, l.weg.name,
                l.text.ifBlank { l.fehler?.let { "(Fehler $it)" } ?: "(kein Text)" }.take(80)))
        }
        appendLine()
    }

    private fun StringBuilder.schreibeVergleich(
        laeufe: List<Lauf>,
        bezugstext: String,
        klassen: Map<String, Fehlerarten.Klasse>
    ) {
        val gruppen = Weg.entries.associateWith { weg ->
            laeufe.filter { it.weg == weg && it.text.isNotBlank() }
                .map { lauf ->
                    Fehlerarten.beurteile(
                        Wortvergleich.vergleiche(bezugstext, lauf.text), bezugstext, klassen
                    ) to Wortvergleich.vergleiche(bezugstext, lauf.text)
                }
        }

        appendLine("TREFFERQUOTE JE WORTKLASSE  (höher ist besser)")
        appendLine("  %-16s %-12s %-12s %s".format("Klasse", "ALT", "NEU", "Unterschied"))
        Fehlerarten.Klasse.entries.forEach { klasse ->
            val werte = Weg.entries.map { weg ->
                val quoten = gruppen.getValue(weg).mapNotNull { (b, _) ->
                    b.jeKlasse.first { it.klasse == klasse }.quote
                }
                if (quoten.isEmpty()) null else quoten.average()
            }
            val alt = werte[0]
            val neu = werte[1]
            appendLine("  %-16s %-12s %-12s %s".format(
                klasse.name,
                alt?.let { "%.0f %%".format(it * 100) } ?: "-",
                neu?.let { "%.0f %%".format(it * 100) } ?: "-",
                if (alt != null && neu != null) "%+.0f Punkte".format((neu - alt) * 100) else "-"
            ))
        }
        appendLine()

        appendLine("FEHLERARTEN  (Mittel je Lauf, niedriger ist besser)")
        appendLine("  %-22s %-12s %-12s %s".format("Art", "ALT", "NEU", "Unterschied"))
        listOf<Triple<String, (Fehlerarten.Befund) -> Int, Boolean>>(
            Triple("fehlende Wörter", { it.fehlend }, true),
            Triple("zusätzliche Wörter", { it.zusaetzlich }, true),
            Triple("ersetzte Wörter", { it.ersetzt }, true),
            Triple("davon nur Schreibweise", { it.nurSchreibweise }, false),
            Triple("erfundene Wörter", { it.erfunden }, true)
        ).forEach { (name, holen, _) ->
            val werte = Weg.entries.map { weg ->
                val zahlen = gruppen.getValue(weg).map { (b, _) -> holen(b) }
                if (zahlen.isEmpty()) null else zahlen.average()
            }
            appendLine("  %-22s %-12s %-12s %s".format(
                name,
                werte[0]?.let { "%.1f".format(it) } ?: "-",
                werte[1]?.let { "%.1f".format(it) } ?: "-",
                if (werte[0] != null && werte[1] != null)
                    "%+.1f".format(werte[1]!! - werte[0]!!) else "-"
            ))
        }
        appendLine()

        appendLine("SATZRÄNDER UND ZEITEN")
        Weg.entries.forEach { weg ->
            val g = gruppen.getValue(weg)
            val anfaenge = g.count { (b, _) -> b.satzanfangGetroffen }
            val enden = g.count { (b, _) -> b.satzendeGetroffen }
            val raten = g.map { (_, v) -> (v.fehlerrate * 1000).toLong() }
            val ersterText = laeufe.filter { it.weg == weg }.mapNotNull { it.ersterTextMillis }
            val bestaetigt = laeufe.filter { it.weg == weg }.mapNotNull { it.bestaetigtMillis }
            appendLine("  ${weg.name}")
            appendLine("    Satzanfang getroffen   $anfaenge von ${g.size}")
            appendLine("    Satzende getroffen     $enden von ${g.size}")
            appendLine("    Wortfehlerrate P50     ${Kennzahlen.perzentil(raten, 0.5) ?: "-"} ‰")
            appendLine("    erster Text P50        ${Kennzahlen.perzentil(ersterText, 0.5) ?: "-"} ms")
            appendLine("    bestätigt P50          ${Kennzahlen.perzentil(bestaetigt, 0.5) ?: "-"} ms")
            appendLine("    Läufe mit Text         ${g.size} von ${laeufe.count { it.weg == weg }}")
        }
        appendLine()

        appendLine("TON DER NEUEN STRECKE")
        val befunde = laeufe.mapNotNull { it.streckenbefund }
        if (befunde.isEmpty()) {
            appendLine("  keiner erhoben")
        } else {
            appendLine("  verworfene Blöcke gesamt   ${befunde.sumOf { it.verworfeneBloecke }}")
            appendLine("  größte Warteschlange       ${befunde.maxOf { it.groessteWarteschlange }}")
            appendLine("  Lesefehler gesamt          ${befunde.sumOf { it.leseFehler }}")
        }
        appendLine()

        appendLine("URTEIL")
        val altText = gruppen.getValue(Weg.ALT).size
        val neuText = gruppen.getValue(Weg.NEU).size
        when {
            altText == 0 || neuText == 0 ->
                appendLine("  Ein Weg lieferte gar keinen Text (ALT $altText, NEU $neuText). " +
                    "Der Aufbau trägt nicht -- kein Vergleich.")
            else -> {
                val altRate = gruppen.getValue(Weg.ALT).map { (_, v) -> v.fehlerrate }.average()
                val neuRate = gruppen.getValue(Weg.NEU).map { (_, v) -> v.fehlerrate }.average()
                appendLine("  Wortfehlerrate ALT %.1f %%, NEU %.1f %%"
                    .format(altRate * 100, neuRate * 100))
                appendLine("  " + when {
                    neuRate <= altRate * 1.05 ->
                        "**Der neue Weg erkennt mindestens so gut wie der alte.** " +
                            "Damit behält er seine belegten Vorteile, ohne Qualität zu kosten."
                    else ->
                        "**Der neue Weg erkennt schlechter.** Die Vorteile beim Transport " +
                            "wiegen das nicht auf -- erst die Ursache klären."
                })
                appendLine()
                appendLine("  Diese Messung gilt für eine Stimme, einen Text, ein Gerät und")
                appendLine("  den Weg über den Lautsprecher. Sie sagt nichts über andere")
                appendLine("  Sprecher, Umgebungen oder Texte.")
            }
        }
    }

    private fun lauf(nummer: Int, weg: Weg, aufnahme: File): Lauf {
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
            runCatching { neuer.startListening(absicht(weg, lesen)) }
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
        return Lauf(nummer, weg, wahl.text, ersterText, bestaetigt, fehler, befund)
    }

    /**
     * Der alte Weg bekommt **genau die Absicht, die Nibra heute stellt** --
     * ohne Tonquelle, ohne Segmentsitzung. Ihm den neuen Aufbau zu geben
     * hiesse, den alten Weg nicht zu messen.
     */
    private fun absicht(weg: Weg, lesen: ParcelFileDescriptor?) =
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
    }
}
