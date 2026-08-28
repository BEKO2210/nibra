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
 * Bringt `EXTRA_BIASING_STRINGS` etwas -- und wenn ja, wie viel?
 *
 * Eigennamen sind die Stelle, an der Diktat für den Nutzer scheitert.
 * „Guten Morgen" bekommt jeder Erkenner hin; „Aslani" und „d&b
 * audiotechnik" sind der Grund, warum Diktat als unbrauchbar gilt. Genau
 * dafür gibt es die Vorgabeliste -- ob sie wirkt, ist damit aber noch
 * nicht gesagt.
 *
 * **Gemessen wird je Name, nicht als Gesamtfehlerrate.** Eine Fehlerrate
 * über den ganzen Satz verdünnt die eine Stelle, auf die es ankommt: fünf
 * richtige Füllwörter machen einen falschen Namen wieder wett, obwohl der
 * Satz für den Nutzer unbrauchbar ist.
 *
 * A und B laufen abwechselnd, nicht nacheinander in Blöcken. Sonst läge
 * ein Unterschied womöglich daran, dass das Gerät im zweiten Block wärmer
 * oder der Dienst inzwischen geladen war.
 *
 * Die Namensliste hier ist **Forschung**. In der App kommt sie aus dem
 * Wörterbuch des Nutzers -- fest eingebaute Namen wären für alle anderen
 * wertlos.
 */
class Biasingversuch(
    private val zusammenhang: Context,
    private val aufStand: (String) -> Unit
) {

    data class Lauf(
        val nummer: Int,
        val mitVorgabe: Boolean,
        val text: String,
        val fehler: Int?
    )

    private val hauptfaden = Handler(Looper.getMainLooper())

    fun fuehreDurch(
        pcm: ByteArray,
        bezugstext: String,
        namen: List<String>,
        wiederholungen: Int
    ): String = buildString {
        appendLine("VORGABELISTE -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("Bezugstext: $bezugstext")
        appendLine("Vorgegebene Namen: ${namen.joinToString(", ")}")
        appendLine("$wiederholungen Paare, abwechselnd ohne und mit Vorgabe.")
        appendLine()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appendLine("EXTRA_BIASING_STRINGS gibt es erst ab Android 13. NICHT PRÜFBAR.")
            return@buildString
        }

        val laeufe = mutableListOf<Lauf>()
        (1..wiederholungen).forEach { nummer ->
            listOf(false, true).forEach { mitVorgabe ->
                aufStand("Paar $nummer von $wiederholungen, " +
                    "Vorgabe ${if (mitVorgabe) "an" else "aus"}")
                laeufe += lauf(laeufe.size + 1, mitVorgabe, pcm, namen)
            }
        }

        appendLine("EINZELNE LÄUFE")
        laeufe.forEach { l ->
            appendLine("  %-4s %-8s %s".format(
                l.nummer,
                if (l.mitVorgabe) "mit" else "ohne",
                l.text.ifBlank { l.fehler?.let { "(Fehler $it)" } ?: "(kein Text)" }.take(70)
            ))
        }
        appendLine()

        appendLine("TREFFER JE NAME")
        appendLine("  %-24s %-12s %-12s %s".format("Name", "ohne", "mit", "Unterschied"))
        val ohne = laeufe.filter { !it.mitVorgabe }
        val mit = laeufe.filter { it.mitVorgabe }
        val ergebnisse = namen.map { name ->
            val o = ohne.count { Namenstreffer.steckt(it.text, name) }
            val m = mit.count { Namenstreffer.steckt(it.text, name) }
            appendLine("  %-24s %-12s %-12s %s".format(
                name, "$o von ${ohne.size}", "$m von ${mit.size}",
                if (m > o) "+${m - o}" else if (m < o) "${m - o}" else "0"
            ))
            Triple(name, o, m)
        }
        appendLine()

        appendLine("WORTFEHLERRATE ÜBER DEN GANZEN SATZ")
        listOf("ohne Vorgabe" to ohne, "mit Vorgabe" to mit).forEach { (was, gruppe) ->
            val raten = gruppe.filter { it.text.isNotBlank() }
                .map { (Wortvergleich.vergleiche(bezugstext, it.text).fehlerrate * 1000).toLong() }
            appendLine("  %-16s P50 %s ‰  Mittel %s ‰  (%d von %d mit Text)".format(
                was,
                Kennzahlen.perzentil(raten, 0.5)?.toString() ?: "-",
                Kennzahlen.mittel(raten)?.toString() ?: "-",
                raten.size, gruppe.size
            ))
        }
        appendLine("  (‰ statt %, damit kleine Unterschiede nicht wegrunden.)")
        appendLine()

        appendLine("URTEIL")
        val besser = ergebnisse.count { (_, o, m) -> m > o }
        val schlechter = ergebnisse.count { (_, o, m) -> m < o }
        val ohneText = laeufe.count { it.text.isBlank() }
        when {
            ohneText > laeufe.size / 3 ->
                appendLine("  $ohneText von ${laeufe.size} Läufen lieferten keinen Text. " +
                    "Der Aufbau trägt nicht -- kein Urteil über die Vorgabeliste.")
            besser > schlechter ->
                appendLine("  **Die Vorgabeliste hilft.** $besser von ${namen.size} Namen " +
                    "kommen mit Vorgabe häufiger an, $schlechter seltener. " +
                    "Damit lohnt ein Wörterbuch im Programm.")
            besser == 0 && schlechter == 0 ->
                appendLine("  **Kein Unterschied.** Die Vorgabeliste ändert auf diesem Gerät " +
                    "nichts an den geprüften Namen. Das ist ein Ergebnis, kein Fehlschlag: " +
                    "ein Wörterbuch wäre dann Arbeit ohne Wirkung, und der Nutzer bekäme " +
                    "ein Versprechen, das die Technik nicht hält.")
            else ->
                appendLine("  **Die Vorgabeliste schadet eher.** $schlechter Namen kommen " +
                    "seltener an, $besser häufiger. Nicht einbauen, bevor das erklärt ist.")
        }
        appendLine()
        appendLine("  Geprüft wurde eine Stimme, ein Satz, ein Gerät. Ein Ergebnis hier")
        appendLine("  gilt **nicht** für beliebige Namen -- es sagt nur, ob der Weg")
        appendLine("  überhaupt wirkt.")
    }

    private fun lauf(
        nummer: Int,
        mitVorgabe: Boolean,
        pcm: ByteArray,
        namen: List<String>
    ): Lauf {
        val segmente = mutableListOf<String>()
        val lesarten = mutableListOf<String>()
        val teiltexte = mutableListOf<String>()
        var fehler: Int? = null
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
            runCatching { neuer.startListening(absicht(lesen, mitVorgabe, namen)) }
                .onFailure { fertig.countDown() }
            gestartet.countDown()
        }
        gestartet.await(5, TimeUnit.SECONDS)

        val schreiber = thread(name = "vorgabe-$nummer") {
            runCatching {
                FileOutputStream(schreiben.fileDescriptor).use { strom ->
                    var stelle = 0
                    while (stelle < pcm.size) {
                        val menge = minOf(2048, pcm.size - stelle)
                        strom.write(pcm, stelle, menge)
                        stelle += menge
                        Thread.sleep(menge.toLong() * 1000 / (ABTASTRATE * 2))
                    }
                }
            }
            runCatching { schreiben.close() }
        }
        schreiber.join(30_000)
        fertig.await(15, TimeUnit.SECONDS)
        hauptfaden.post { runCatching { erkenner?.destroy() } }
        runCatching { lesen.close() }
        Thread.sleep(1_500)

        val wahl = Ergebniswahl.waehle(
            segmente = Ergebniswahl.ohneWiederholung(segmente),
            endergebnis = lesarten,
            zwischenstaende = teiltexte
        )
        return Lauf(nummer, mitVorgabe, wahl.text, fehler)
    }

    private fun absicht(
        lesen: ParcelFileDescriptor,
        mitVorgabe: Boolean,
        namen: List<String>
    ) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, zusammenhang.packageName)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, lesen)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, ABTASTRATE)
        putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        if (mitVorgabe) {
            putExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, namen.toTypedArray())
        }
    }

    private fun lies(werte: Bundle?): List<String> =
        werte?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty().filter { it.isNotBlank() }

    companion object {
        const val ABTASTRATE = 16_000
        const val PAARE = 5

        /** Nur für den Versuch. In der App kommen die Namen vom Nutzer. */
        val NAMEN = listOf("Belkis", "Aslani", "Nibra", "Weinreich", "audiotechnik")

        const val SATZ = "Belkis Aslani spricht mit Nibra. " +
            "Herr Weinreich arbeitet bei d und b audiotechnik in Backnang."
    }
}
