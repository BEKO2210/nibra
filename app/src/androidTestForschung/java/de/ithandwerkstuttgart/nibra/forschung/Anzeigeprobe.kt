package de.ithandwerkstuttgart.nibra.forschung

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Liest den Prüfsatz **aus der Oberfläche** zurück.
 *
 * Der Rückkanal über `SideEffect` sagt, welche Zeichenkette nach einer
 * gelungenen Composition an die Anzeige übergeben wurde. Das ist mehr als
 * die Vorlage des Versuchs, aber es ist immer noch die App, die über sich
 * selbst Auskunft gibt.
 *
 * Hier fragt eine **fremde** Stelle: die Instrumentierung liest die
 * Textknoten der laufenden Oberfläche und vergleicht, was dort steht, mit
 * den Prüfsätzen. Keine Bilderkennung, kein Raten -- die Knoten selbst.
 *
 * Was das **nicht** beweist: dass die Bildpunkte auf dem Schirm so
 * aussehen. Ein Knoten kann verdeckt oder in der Farbe des Hintergrunds
 * sein. Bewiesen ist, dass der richtige Text in der Hierarchie steht.
 */
class Anzeigeprobe {

    @Test
    fun derAngezeigteTextGehoertZumPruefsatz() {
        val werkzeug = InstrumentationRegistry.getInstrumentation()
        val geraet = UiDevice.getInstance(werkzeug)
        val zusammenhang = werkzeug.targetContext

        val bericht = File(zusammenhang.getExternalFilesDir(null), "mikrofon-pilot.txt")
        bericht.delete()

        zusammenhang.startActivity(
            Intent(zusammenhang, ForschungActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("mikrofon", true)
                putExtra("pilot", true)
                putExtra("durchgaenge", 1)
            }
        )

        val erwartet = Testfall.PILOT.associateBy { it.text }
        val gesehen = mutableSetOf<String>()
        val fremde = mutableListOf<String>()

        val bis = System.currentTimeMillis() + WARTEGRENZE_MILLIS
        while (System.currentTimeMillis() < bis && !bericht.exists()) {
            geraet.waitForIdle(500)
            // **Die ganze Fensterhierarchie.** findObjects gab für die
            // Compose-Oberfläche nichts her; der Auszug enthält die
            // Textknoten samt Inhalt.
            val auszug = runCatching {
                java.io.ByteArrayOutputStream().use { strom ->
                    geraet.dumpWindowHierarchy(strom)
                    strom.toString("UTF-8")
                }
            }.getOrElse { "" }

            // **Beide Seiten gleich behandeln.** Der erste Wurf entfernte
            // die Satzzeichen nur aus dem Suchmuster, nicht aus dem Auszug.
            // Satz A hat als einziger ein Komma in den ersten vierzig
            // Zeichen und wurde deshalb nie gefunden -- die Anzeige war
            // richtig, die Suche danach war es nicht.
            val auszugKern = auszug.filter { it.isLetterOrDigit() || it == ' ' }
            erwartet.keys.forEach { satz ->
                val kern = satz.filter { it.isLetterOrDigit() || it == ' ' }.take(40)
                if (kern.isNotBlank() && auszugKern.contains(kern)) gesehen += satz
            }
            if (auszugKern.contains("KEIN PR")) fremde += "-- KEIN PRÜFSATZ --"
            if (auszugKern.contains("Ich teste heute die Spracherkennung")) {
                fremde += "der alte Festtext des Sprachlaufs"
            }
            Thread.sleep(250)
        }

        assertTrue(
            "Kein Bericht nach ${WARTEGRENZE_MILLIS / 1000} s",
            bericht.exists()
        )
        assertTrue(
            "Auf dem Bildschirm stand Text, der zu keinem Prüfsatz gehört:\n" +
                fremde.distinct().joinToString("\n") { "  \"${it.take(90)}\"" },
            fremde.isEmpty()
        )
        assertTrue(
            "Nicht alle Prüfsätze waren zu sehen. Gesehen: ${gesehen.size} von " +
                "${Testfall.PILOT.size}\n" +
                Testfall.PILOT.filter { it.text !in gesehen }
                    .joinToString("\n") { "  fehlte: ${it.id}" },
            gesehen.size == Testfall.PILOT.size
        )

        // Und der Fingerabdruck: was zu sehen war, muss zu dem passen,
        // was der Bericht als bewertet ausweist.
        val inhalt = bericht.readText()
        Testfall.PILOT.forEach { fall ->
            val abdruck = MessageDigest.getInstance("SHA-256")
                .digest(fall.text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            assertTrue(
                "Der Bericht führt für ${fall.id} nicht den Abdruck des " +
                    "angezeigten Textes",
                inhalt.contains(abdruck.take(16))
            )
        }
        assertTrue(
            "Der Lauf wurde abgebrochen",
            !inhalt.contains("**ABGEBROCHEN**")
        )
    }

    private companion object {
        const val WARTEGRENZE_MILLIS = 6L * 60 * 1000
    }
}
