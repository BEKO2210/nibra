package de.ithandwerkstuttgart.loqui

import de.ithandwerkstuttgart.loqui.erkennung.setzeSatzzeichen
import org.junit.Assert.assertEquals
import org.junit.Test

class SatzzeichenTest {

    // Regeln koennen Satzanfaenge gross machen, aber keine deutschen
    // Substantive erkennen -- das leistet der Erkenner selbst (Formatierung
    // ab API 33) oder spaeter ein lokales Sprachmodell.
    @Test
    fun `gesprochener punkt wird zum zeichen`() {
        assertEquals("Hallo welt.", setzeSatzzeichen("hallo welt punkt", "de-DE"))
    }

    @Test
    fun `komma haengt ohne leerzeichen am wort`() {
        assertEquals(
            "Guten tag, wie geht es?",
            setzeSatzzeichen("guten tag komma wie geht es fragezeichen", "de-DE")
        )
    }

    @Test
    fun `nach dem punkt geht es gross weiter`() {
        assertEquals(
            "Erster satz. Zweiter satz.",
            setzeSatzzeichen("erster satz punkt zweiter satz punkt", "de-DE")
        )
    }

    @Test
    fun `neue zeile wird zum umbruch`() {
        assertEquals("Oben\nUnten", setzeSatzzeichen("oben neue zeile unten", "de-DE"))
    }

    @Test
    fun `wort mit punkt im namen bleibt unberuehrt`() {
        assertEquals("Punktlandung", setzeSatzzeichen("punktlandung", "de-DE"))
    }

    @Test
    fun `englische befehle greifen bei englischer sprache`() {
        assertEquals("Hello world.", setzeSatzzeichen("hello world full stop", "en-US"))
    }

    @Test
    fun `unbekannte sprache laesst den text stehen und setzt nur gross`() {
        assertEquals("Bok svijete", setzeSatzzeichen("bok svijete", "hr-HR"))
    }

    @Test
    fun `leerer text bleibt leer`() {
        assertEquals("", setzeSatzzeichen("", "de-DE"))
    }
}
