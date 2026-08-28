package de.ithandwerkstuttgart.loqui

import de.ithandwerkstuttgart.loqui.erkennung.wendeBausteineAn
import de.ithandwerkstuttgart.loqui.ui.modell.Textbaustein
import org.junit.Assert.assertEquals
import org.junit.Test

class TextbausteinsatzTest {

    private fun baustein(kuerzel: String, ersatz: String) =
        Textbaustein(id = kuerzel, kuerzel = kuerzel, ersatz = ersatz)

    @Test
    fun `kuerzel wird als ganzes wort ersetzt`() {
        val text = wendeBausteineAn(
            "Bis dann, mfg",
            listOf(baustein("mfg", "mit freundlichen Grüßen"))
        )
        assertEquals("Bis dann, mit freundlichen Grüßen", text)
    }

    @Test
    fun `teil eines wortes bleibt unberuehrt`() {
        val text = wendeBausteineAn("mfgx bleibt", listOf(baustein("mfg", "voll")))
        assertEquals("mfgx bleibt", text)
    }

    @Test
    fun `grossschreibung spielt keine rolle`() {
        val text = wendeBausteineAn("MFG", listOf(baustein("mfg", "Gruß")))
        assertEquals("Gruß", text)
    }

    @Test
    fun `laengeres kuerzel gewinnt`() {
        val text = wendeBausteineAn(
            "mfg dr",
            listOf(baustein("mfg", "Gruß"), baustein("mfg dr", "Grüße, Doktor"))
        )
        assertEquals("Grüße, Doktor", text)
    }

    @Test
    fun `ersatz mit dollarzeichen bleibt woertlich`() {
        val text = wendeBausteineAn("preis", listOf(baustein("preis", "100 $ netto")))
        assertEquals("100 $ netto", text)
    }

    @Test
    fun `ohne bausteine bleibt der text gleich`() {
        assertEquals("unveraendert", wendeBausteineAn("unveraendert", emptyList()))
    }
}
