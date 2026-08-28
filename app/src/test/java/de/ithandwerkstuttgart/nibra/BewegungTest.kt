package de.ithandwerkstuttgart.nibra

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import de.ithandwerkstuttgart.nibra.ui.gestalt.Stufe
import de.ithandwerkstuttgart.nibra.ui.gestalt.spezifikation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueft die Bewegungssprache als Rechnung, nicht als Augenschein.
 *
 * Die Werte sind nicht beliebig: sie tragen die Aussage, dass Nibra sich
 * ruhig und teuer bewegt und nicht wie eine Vorfuehrung. Wer sie aendert,
 * soll hier stolpern und sich fragen, ob er es wirklich will.
 */
class BewegungTest {

    @Test
    fun `jede stufe liefert eine feder`() {
        Stufe.entries.forEach { stufe ->
            val spez = spezifikation<Float>(stufe, bewegungAus = false)
            assertTrue(
                "${stufe.name} muss eine Feder sein, kein Zeitverlauf",
                spez is SpringSpec<Float>
            )
        }
    }

    /**
     * Der Kern der Haltung: nachschwingen darf **nur** der Auftritt. Eine
     * Daempfung unter 1 bedeutet Ueberschwingen; an einer Kachel oder einem
     * Symbol wirkt das billig.
     */
    @Test
    fun `nur der auftritt schwingt nach`() {
        assertEquals(1f, Stufe.WIRKUNG.daempfung, 0f)
        assertEquals(1f, Stufe.RUHE.daempfung, 0f)

        assertTrue(
            "Der Raum darf hoechstens an der Wahrnehmungsschwelle schwingen",
            Stufe.RAUM.daempfung >= 0.85f
        )
        assertTrue(
            "Der Auftritt soll spuerbar ueberschwingen",
            Stufe.AUFTRITT.daempfung < 0.85f
        )
        assertTrue(
            "aber nicht verspielt wirken",
            Stufe.AUFTRITT.daempfung >= 0.7f
        )
    }

    /** Je groesser das Bewegte, desto weicher zieht es. */
    @Test
    fun `die haerte nimmt von der kleinen reaktion zur grossen flaeche ab`() {
        assertTrue(Stufe.WIRKUNG.haerte > Stufe.RAUM.haerte)
        assertTrue(Stufe.RAUM.haerte > Stufe.AUFTRITT.haerte)
        assertTrue(Stufe.AUFTRITT.haerte > Stufe.RUHE.haerte)
    }

    /**
     * Wer Animationen abschaltet, will kein langsameres Wackeln, sondern
     * gar keins. Dann springt jede Stufe sofort ans Ziel.
     */
    @Test
    fun `abgeschaltete bewegung springt sofort`() {
        Stufe.entries.forEach { stufe ->
            val spez = spezifikation<Float>(stufe, bewegungAus = true)
            assertTrue(
                "${stufe.name} muss bei abgeschalteter Bewegung springen",
                spez is SnapSpec<Float>
            )
        }
    }
}
