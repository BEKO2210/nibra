package de.ithandwerkstuttgart.nibra

import android.text.InputType
import de.ithandwerkstuttgart.nibra.dienst.Feldschutz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prueft die Zusage aus AUFTRAG.md (Antwort 9) und aus dem Einrichtungstext
 * der App: "Der Dienst liest keine Inhalte mit, sendet nichts und uebergeht
 * Passwortfelder."
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FeldschutzTest {

    private fun textfeld(spielart: Int) = InputType.TYPE_CLASS_TEXT or spielart

    @Test
    fun `als passwort gemeldetes feld bleibt unberuehrt`() {
        assertTrue(Feldschutz.istGeschuetzt(alsPasswortGemeldet = true, eingabeart = 0))
    }

    /**
     * Der eigentliche Grund fuer diese Pruefung: WebView-Felder und manche
     * selbst gebauten Eingaben melden `isPassword` nicht, tragen die Absicht
     * aber im `inputType`. Ohne diese Faelle waere die Zusage der App falsch.
     */
    @Test
    fun `passwortarten ohne kennzeichnung bleiben unberuehrt`() {
        val geschuetzt = listOf(
            "verdecktes Passwort" to textfeld(InputType.TYPE_TEXT_VARIATION_PASSWORD),
            "sichtbares Passwort" to textfeld(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
            "Passwort im WebView" to textfeld(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD),
            "PIN-Feld" to (InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        )
        geschuetzt.forEach { (was, art) ->
            assertTrue(
                "$was muss unberuehrt bleiben",
                Feldschutz.istGeschuetzt(alsPasswortGemeldet = false, eingabeart = art)
            )
        }
    }

    @Test
    fun `gewoehnliche felder darf nibra beschreiben`() {
        val erlaubt = listOf(
            "einfacher Text" to textfeld(InputType.TYPE_TEXT_VARIATION_NORMAL),
            "mehrzeiliger Text" to (InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE),
            "E-Mail" to textfeld(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            "Nachricht" to textfeld(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE),
            "Suchfeld im WebView" to textfeld(InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT),
            "gewoehnliche Zahl" to (InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_NORMAL),
            "unbekannt" to 0
        )
        erlaubt.forEach { (was, art) ->
            assertFalse(
                "$was darf beschrieben werden",
                Feldschutz.istGeschuetzt(alsPasswortGemeldet = false, eingabeart = art)
            )
        }
    }

    /**
     * Die E-Mail-Spielart traegt denselben Zahlenwert wie die
     * Passwort-Spielart einer anderen Klasse. Ohne die Klassenmaske wuerde
     * die Regel hier falsch anschlagen -- dieser Test haelt das fest.
     */
    @Test
    fun `spielart wird nur innerhalb ihrer klasse gelesen`() {
        val emailArt = textfeld(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        assertFalse(Feldschutz.istGeschuetzt(false, emailArt))

        val pinArt = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        assertTrue(Feldschutz.istGeschuetzt(false, pinArt))
    }

    /**
     * Aus dem Einsatz: im Feld stand "Nachricht eingeben Schreibe das
     * Sonnensystem." und "Google fragen Sonnensystem!". Der graue Hinweis
     * wurde mitgeschrieben, weil `text` ihn zurueckgibt, solange nichts
     * eingegeben ist.
     */
    @Test
    fun `grauer hinweis zaehlt nicht als inhalt`() {
        assertEquals("", Feldschutz.inhalt(zeigtHinweis = true, text = "Nachricht eingeben"))
        assertEquals("", Feldschutz.inhalt(zeigtHinweis = true, text = "Google fragen"))
    }

    @Test
    fun `echter inhalt bleibt erhalten`() {
        assertEquals(
            "Guten Morgen",
            Feldschutz.inhalt(zeigtHinweis = false, text = "Guten Morgen")
        )
        assertEquals("", Feldschutz.inhalt(zeigtHinweis = false, text = null))
        assertEquals("", Feldschutz.inhalt(zeigtHinweis = false, text = ""))
    }
}
