package de.ithandwerkstuttgart.nibra

import de.ithandwerkstuttgart.nibra.erkennung.Erkennungsergebnis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueft, dass die n-beste Liste und die Sicherheiten erhalten bleiben --
 * und vor allem, dass **keine Sicherheit erfunden** wird.
 *
 * Der Auftrag ist dort ausdruecklich: „Wenn ein Geraet keine brauchbare
 * Confidence liefert, sauber als unavailable behandeln. Keine erfundene
 * Confidence." Eine geschaetzte Sicherheit waere schlimmer als gar keine,
 * weil spaeter Entscheidungen darauf beruhen sollen.
 */
class ErkennungsergebnisTest {

    @Test
    fun `die n-beste liste bleibt vollstaendig erhalten`() {
        val ergebnis = Erkennungsergebnis.aus(
            texte = listOf("d&b audiotechnik", "DNB Audio Technik", "de be audio technik"),
            sicherheiten = floatArrayOf(0.82f, 0.61f, 0.4f)
        )
        assertEquals(3, ergebnis.lesarten.size)
        assertEquals("d&b audiotechnik", ergebnis.text)
        assertEquals(0.82f, ergebnis.konfidenz!!, 0.001f)
        assertEquals("DNB Audio Technik", ergebnis.lesarten[1].text)
    }

    /** Der Regelfall auf den meisten Geraeten: das Feld fehlt ganz. */
    @Test
    fun `ohne sicherheiten bleibt die konfidenz unbekannt`() {
        val ergebnis = Erkennungsergebnis.aus(
            texte = listOf("Guten Morgen"),
            sicherheiten = null
        )
        assertEquals("Guten Morgen", ergebnis.text)
        assertNull("Es darf keine Sicherheit erfunden werden", ergebnis.konfidenz)
        assertTrue(ergebnis.konfidenzUnbekannt)
    }

    /**
     * Liefert das Geraet weniger Sicherheiten als Texte, gilt der Rest als
     * unbekannt -- nicht als null Prozent und nicht als hundert.
     */
    @Test
    fun `fehlende sicherheiten werden nicht aufgefuellt`() {
        val ergebnis = Erkennungsergebnis.aus(
            texte = listOf("erster", "zweiter", "dritter"),
            sicherheiten = floatArrayOf(0.9f)
        )
        assertEquals(0.9f, ergebnis.lesarten[0].konfidenz!!, 0.001f)
        assertNull(ergebnis.lesarten[1].konfidenz)
        assertNull(ergebnis.lesarten[2].konfidenz)
        assertFalse(ergebnis.konfidenzUnbekannt)
    }

    /** Werte ausserhalb von 0 bis 1 sind unbrauchbar und gelten als fehlend. */
    @Test
    fun `unsinnige sicherheiten gelten als unbekannt`() {
        val ergebnis = Erkennungsergebnis.aus(
            texte = listOf("a", "b", "c"),
            sicherheiten = floatArrayOf(-1f, 42f, 0.5f)
        )
        assertNull(ergebnis.lesarten[0].konfidenz)
        assertNull(ergebnis.lesarten[1].konfidenz)
        assertEquals(0.5f, ergebnis.lesarten[2].konfidenz!!, 0.001f)
    }

    @Test
    fun `leere texte fallen heraus`() {
        val ergebnis = Erkennungsergebnis.aus(
            texte = listOf("", "   ", "Guten Morgen"),
            sicherheiten = null
        )
        assertEquals(1, ergebnis.lesarten.size)
        assertEquals("Guten Morgen", ergebnis.text)
    }

    @Test
    fun `gar kein ergebnis ergibt leeren text`() {
        val leer = Erkennungsergebnis.aus(texte = null, sicherheiten = null)
        assertEquals("", leer.text)
        assertNull(leer.konfidenz)
        assertTrue(leer.lesarten.isEmpty())
    }
}
