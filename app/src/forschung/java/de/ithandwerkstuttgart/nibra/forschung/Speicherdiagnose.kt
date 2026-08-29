package de.ithandwerkstuttgart.nibra.forschung

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * **Ursachensuche zum anhaltenden Speicherwachstum. Kein Messwert für die
 * Auslieferung.**
 *
 * Der Lauf über 900 Sitzungen hat gezeigt, dass je Sitzung Speicher hängen
 * bleibt: 3,37 KB nach RSS, rund 3,0 MB über den ganzen Lauf, ohne
 * Abflachen. Was hängen bleibt und warum, sagt diese Messung nicht -- sie
 * misst den Stand, nicht den Inhalt.
 *
 * Diese Diagnose beantwortet die nächste Frage: **welche Objekte überleben
 * ihre Sitzung?**
 *
 * ## Wie sie das tut
 *
 * Jede Sitzung hängt an ihre eigenen Objekte eine schwache Referenz --
 * Erkenner, Zuhörer, beide Enden des Rohrs, den Schreibfaden, die Absicht.
 * Eine schwache Referenz hält nichts fest: wird das Objekt sonst von
 * niemandem mehr gehalten, räumt die Bereinigung es ab und die Referenz
 * wird leer.
 *
 * Nach einer **erzwungenen** Bereinigung wird gezählt, wie viele Objekte
 * älterer Sitzungen noch leben. Bleibt etwas übrig, hält es jemand fest --
 * und dann steht auch fest, **was**, ohne dass ein einziger Abzug der Halde
 * nötig wäre.
 *
 * ## Zwei Arme, damit die Messung sich nicht selbst misst
 *
 * Der Sitzungsdauerlauf legt je Sitzung einen Datensatz ab: Nummer, Stand,
 * und eine Karte der Dateizeiger nach Art. Über 900 Sitzungen sind das 900
 * Karten -- grob eine halbe Million Byte, die **die Messung selbst**
 * festhält und nicht die Diktierstrecke.
 *
 * Deshalb läuft die Diagnose auf Wunsch mit und ohne diese Buchführung.
 * Verschwindet das Wachstum ohne sie, war ein Teil davon der Messplatz.
 *
 * ## Was hier ausdrücklich erlaubt ist -- und nirgends sonst
 *
 * Erzwungene Bereinigung. Im Sitzungsdauerlauf ist sie verboten, weil sie
 * misst, was ein Programm im Betrieb nie tut: dort zählt der Stand, den das
 * Gerät von sich aus hält.
 *
 * Hier ist genau das Gegenteil gefragt. Ein Objekt, das nach erzwungener
 * Bereinigung noch lebt, wird festgehalten. Ohne den Zwang wüsste man nur,
 * dass die Bereinigung noch nicht gelaufen ist.
 *
 * **Die Zahlen aus dieser Diagnose dürfen nicht mit denen des
 * Sitzungsdauerlaufs vermischt werden.** Sie entstehen unter anderen
 * Bedingungen und beantworten eine andere Frage.
 */
class Speicherdiagnose(
    private val zusammenhang: Context,
    private val sprache: String,
    private val buchfuehrung: Boolean,
    private val melde: (String) -> Unit
) {

    /** Eine schwache Referenz samt Herkunft. */
    private data class Zeuge(
        val sitzung: Int,
        val art: String,
        val referenz: WeakReference<Any>
    )

    /** Der Stand an einem Kontrollpunkt, nach erzwungener Bereinigung. */
    private data class Haltepunkt(
        val sitzung: Int,
        val javaKb: Long,
        val nativeKb: Long,
        val rssKb: Long?,
        val zeiger: Int?,
        val faeden: Int,
        /** Überlebende je Art, gezählt über alle bisherigen Sitzungen. */
        val ueberlebende: Map<String, Int>
    )

    private val hauptfaden = Handler(Looper.getMainLooper())
    private val zeugen = mutableListOf<Zeuge>()
    private val haltepunkte = mutableListOf<Haltepunkt>()

    /** Nur gefüllt, wenn [buchfuehrung] gesetzt ist -- der Verdächtige. */
    private val buchhaltung = mutableListOf<Pair<Prozessbefund.Stand, Map<String, Int>>>()

    /** Böden für die Ausgleichsgerade, einer je Sitzung, nach Bereinigung. */
    private val javaVerlauf = mutableListOf<Long>()
    private val nativeVerlauf = mutableListOf<Long>()
    private val rssVerlauf = mutableListOf<Long>()

    private var ohneText = 0

    fun fuehreAus(pcm: ByteArray, anzahl: Int): String = buildString {
        appendLine("SPEICHERDIAGNOSE -- ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("**Ursachensuche, kein Messwert für die Auslieferung.**")
        appendLine("Hier wird die Bereinigung erzwungen. Im Sitzungsdauerlauf ist")
        appendLine("das verboten; die Zahlen von dort und die von hier gehören")
        appendLine("nicht in dieselbe Tabelle.")
        appendLine()
        appendLine("Buchführung je Sitzung: ${if (buchfuehrung) "AN" else "AUS"}")
        appendLine("Sitzungen: $anzahl")
        appendLine()

        val kontrolle = kontrollfall(pcm)
        appendLine("KONTROLLFALL (eine Sitzung, bekannter Ausgang)")
        appendLine("  $kontrolle")
        if (kontrolle.startsWith("nicht")) {
            appendLine()
            appendLine("**ABGEBROCHEN** -- der Kontrollfall trägt nicht.")
            appendLine("Ohne ihn wäre jede folgende Zahl wertlos.")
            return@buildString
        }
        appendLine()

        // Nullpunkt: nach dem Kontrollfall, damit der einmalige Aufbau der
        // Verbindung zum Erkennerdienst nicht als Wachstum erscheint.
        haltepunkte += nimmHaltepunkt(0)
        melde("Nullpunkt genommen")

        val punkte = kontrollpunkte(anzahl)
        (1..anzahl).forEach { nummer ->
            sitzung(nummer, pcm)
            bereinige()
            Prozessbefund.nimmAuf().let {
                javaVerlauf += it.speicherKb
                nativeVerlauf += it.nativeKb
                it.rssKb?.let { rss -> rssVerlauf += rss }
            }
            if (nummer in punkte) {
                haltepunkte += nimmHaltepunkt(nummer)
            }
            if (nummer % 10 == 0) melde("Sitzung $nummer von $anzahl")
        }

        appendLine("HALTEPUNKTE -- Stand nach erzwungener Bereinigung")
        appendLine("  %-8s %9s %9s %9s %6s %6s".format(
            "Sitzung", "Java KB", "nativ KB", "RSS KB", "Zeiger", "Fäden"))
        haltepunkte.forEach { h ->
            appendLine("  %-8d %9d %9d %9s %6s %6d".format(
                h.sitzung, h.javaKb, h.nativeKb, h.rssKb?.toString() ?: "-",
                h.zeiger?.toString() ?: "-", h.faeden))
        }
        appendLine()

        appendLine("ÜBERLEBENDE -- Objekte, die ihre Sitzung überdauert haben")
        appendLine("Gezählt nach erzwungener Bereinigung. Nur Objekte aus")
        appendLine("Sitzungen, die mindestens zwei Sitzungen zurückliegen --")
        appendLine("die jüngste kann noch berechtigt gehalten werden.")
        appendLine()
        val arten = haltepunkte.flatMap { it.ueberlebende.keys }.distinct().sorted()
        appendLine("  %-8s %s".format("Sitzung", arten.joinToString("  ") { it.take(10).padStart(10) }))
        haltepunkte.forEach { h ->
            appendLine("  %-8d %s".format(
                h.sitzung,
                arten.joinToString("  ") { (h.ueberlebende[it] ?: 0).toString().padStart(10) }))
        }
        appendLine()

        val hartnaeckig = arten.filter { art ->
            (haltepunkte.lastOrNull()?.ueberlebende?.get(art) ?: 0) > 0
        }
        if (hartnaeckig.isEmpty()) {
            appendLine("  Am Ende lebt kein einziges Objekt einer alten Sitzung mehr.")
            appendLine("  Was auch immer wächst -- diese sechs Arten sind es nicht.")
        } else {
            appendLine("  **Es überleben: ${hartnaeckig.joinToString(", ")}**")
            appendLine("  Diese Arten werden festgehalten. Wovon, sagt erst ein Abzug")
            appendLine("  der Halde -- aber wo zu suchen ist, steht damit fest.")
        }
        appendLine()

        appendLine("VERLAUF NACH BEREINIGUNG (Ausgleichsgerade, 20 Fenster)")
        listOf(
            "Java-Halde" to javaVerlauf,
            "native Halde" to nativeVerlauf,
            "RSS" to rssVerlauf
        ).forEach { (name, reihe) ->
            val fenster = maxOf(5, reihe.size / 20)
            appendLine("  %-14s %s".format(
                "$name:", Verlaufsurteil.beschreibe(Verlaufsurteil.beurteile(reihe, fenster))))
        }
        appendLine()

        appendLine("BUCHFÜHRUNG DES MESSPLATZES")
        if (buchfuehrung) {
            appendLine("  ${buchhaltung.size} Datensätze festgehalten, je ein Stand und")
            appendLine("  eine Karte der Zeigerarten. Das ist Speicher, den **diese")
            appendLine("  Messung** hält und nicht die Diktierstrecke.")
            appendLine("  Der Gegenlauf mit `-e buchfuehrung false` zeigt, wie viel davon")
            appendLine("  im Sitzungsdauerlauf auf ihr Konto ging.")
        } else {
            appendLine("  aus -- kein Datensatz wurde festgehalten.")
            appendLine("  Was hier noch wächst, wächst ohne Zutun der Messung.")
        }
        appendLine()
        appendLine("Sitzungen ohne Text: $ohneText von $anzahl")
    }

    /** Bei 0, einem Fünftel, einem Drittel, zwei Dritteln und am Ende. */
    private fun kontrollpunkte(anzahl: Int): Set<Int> = setOf(
        anzahl / 9, anzahl / 3, anzahl * 2 / 3, anzahl
    ).filter { it > 0 }.toSet()

    /**
     * Bereinigung erzwingen, so gut das geht.
     *
     * Zweimal, mit einem Abschluss dazwischen: der erste Durchgang macht
     * Objekte mit Abschlussarbeiten erst bereit, eingesammelt werden sie
     * beim zweiten. Ein einzelner Aufruf ließe sie als Überlebende
     * erscheinen, die keine sind.
     */
    private fun bereinige() {
        Runtime.getRuntime().gc()
        runCatching { Runtime.getRuntime().runFinalization() }
        Thread.sleep(60)
        Runtime.getRuntime().gc()
        Thread.sleep(60)
    }

    private fun nimmHaltepunkt(sitzung: Int): Haltepunkt {
        bereinige()
        val stand = Prozessbefund.nimmAuf()
        // Nur, was mindestens zwei Sitzungen zurückliegt: die jüngste
        // Sitzung darf noch gehalten werden, ohne dass das ein Befund wäre.
        val ueberlebende = zeugen
            .filter { it.sitzung <= sitzung - 2 && it.referenz.get() != null }
            .groupingBy { it.art }
            .eachCount()
        // Leere Referenzen wegräumen, damit die Liste nicht selbst wächst
        // und zum Teil des Befunds wird.
        zeugen.removeAll { it.referenz.get() == null }
        return Haltepunkt(
            sitzung = sitzung,
            javaKb = stand.speicherKb,
            nativeKb = stand.nativeKb,
            rssKb = stand.rssKb,
            zeiger = stand.offeneZeiger,
            faeden = stand.faeden,
            ueberlebende = ueberlebende
        )
    }

    private fun bezeuge(sitzung: Int, art: String, gegenstand: Any?) {
        if (gegenstand != null) zeugen += Zeuge(sitzung, art, WeakReference(gegenstand))
    }

    private fun kontrollfall(pcm: ByteArray): String {
        val vorher = Prozessbefund.nimmAuf()
        val text = sitzung(0, pcm)
        bereinige()
        val nachher = Prozessbefund.nimmAuf()
        return if (text) {
            "Text erkannt, Zeiger ${vorher.offeneZeiger} -> ${nachher.offeneZeiger} -- in Ordnung"
        } else {
            "nicht bestanden: die Kontrollsitzung lieferte keinen Text"
        }
    }

    /**
     * Eine Sitzung, Schritt für Schritt wie im Sitzungsdauerlauf.
     *
     * **Absichtlich nachgebaut statt aufgerufen.** Der Sitzungsdauerlauf ist
     * eingefroren; jede Änderung an ihm änderte die Vergleichbarkeit aller
     * bisherigen Läufe. Was hier anders ist, ist ausschließlich das
     * Bezeugen der Objekte -- und das hält nichts fest.
     */
    private fun sitzung(nummer: Int, pcm: ByteArray): Boolean {
        var hatText = false
        val fertig = CountDownLatch(1)
        var erkenner: SpeechRecognizer? = null
        val (lesen, schreiben) = ParcelFileDescriptor.createPipe()
        val gestartet = CountDownLatch(1)
        val absicht = absicht(lesen)

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
            val zuhoerer = object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rms: Float) = Unit
                override fun onBufferReceived(b: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(code: Int) = fertig.countDown()
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
            }
            neuer.setRecognitionListener(zuhoerer)
            bezeuge(nummer, "Erkenner", neuer)
            bezeuge(nummer, "Zuhörer", zuhoerer)
            runCatching { neuer.startListening(absicht) }
                .onFailure { fertig.countDown() }
            gestartet.countDown()
        }
        gestartet.await(5, TimeUnit.SECONDS)

        val schreiber = thread(name = "diagnose-$nummer") {
            runCatching {
                FileOutputStream(schreiben.fileDescriptor).use { strom ->
                    var stelle = 0
                    val hoechstens = minOf(
                        pcm.size,
                        Sitzungsdauerlauf.ABTASTRATE * 2 * Sitzungsdauerlauf.SPRECHDAUER_SEKUNDEN
                    )
                    while (stelle < hoechstens) {
                        val menge = minOf(2048, hoechstens - stelle)
                        strom.write(pcm, stelle, menge)
                        stelle += menge
                        Thread.sleep(menge.toLong() * 1000 / (Sitzungsdauerlauf.ABTASTRATE * 2))
                    }
                }
            }
            runCatching { schreiben.close() }
        }
        bezeuge(nummer, "Rohr lesen", lesen)
        bezeuge(nummer, "Rohr schreiben", schreiben)
        bezeuge(nummer, "Schreibfaden", schreiber)
        bezeuge(nummer, "Absicht", absicht)

        schreiber.join(20_000)
        fertig.await(10, TimeUnit.SECONDS)
        hauptfaden.post { runCatching { erkenner?.destroy() } }
        runCatching { lesen.close() }
        Thread.sleep(Sitzungsdauerlauf.RUHE_MILLIS)

        if (!hatText && nummer > 0) ohneText++
        if (buchfuehrung) {
            buchhaltung += Prozessbefund.nimmAuf() to Zeigerbefund.nachArt()
        }
        return hatText
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
            putExtra(
                RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                AudioFormat.ENCODING_PCM_16BIT
            )
            putExtra(
                RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,
                Sitzungsdauerlauf.ABTASTRATE
            )
            putExtra(
                RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                RecognizerIntent.EXTRA_AUDIO_SOURCE
            )
        }

    private fun lies(werte: Bundle?): List<String> =
        werte?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty().filter { it.isNotBlank() }

    companion object {
        /** Wo der Abzug der Halde landet, falls einer genommen wird. */
        fun abzugsort(zusammenhang: Context, name: String): File =
            File(zusammenhang.getExternalFilesDir(null), "halde-$name.hprof")
    }
}
