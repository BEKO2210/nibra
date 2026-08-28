package de.ithandwerkstuttgart.nibra

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.ithandwerkstuttgart.nibra.erkennung.Erkennerhalter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Der Halter trägt die Reihenfolge der Ausleihen -- und damit genau den
 * Wettlauf, der auf dem Gerät ein funktionierendes Diktat abgeschossen hat:
 *
 * ```
 * <- onResults  lesarten=2
 * -> Vorrang  Diktat verdrängt Diktat
 * <- onError  code=11
 * ```
 *
 * Der nächste Satz lieh, bevor der vorige zurückgab; die verspätete
 * Rückgabe räumte die neue Ausleihe ab. Diese Tests stellen die Abfolge
 * deterministisch nach.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ErkennerhalterTest {

    private val zusammenhang get() =
        ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `zweite leihe ohne vorrang wird abgelehnt`() {
        val halter = Erkennerhalter(zusammenhang)
        assertNotNull(halter.leihe("Diktat"))
        assertNull("Belegt muss belegt heißen", halter.leihe("Sprachliste"))
    }

    @Test
    fun `nach der rueckgabe ist wieder frei`() {
        val halter = Erkennerhalter(zusammenhang)
        val schein = halter.leihe("Sprachliste")!!
        halter.gibZurueck(schein)
        assertFalse(halter.istVerliehen())
        assertNotNull(halter.leihe("Diktat"))
    }

    /** Der Gerätefehler, deterministisch: verspätete Rückgabe ist ein Nichts. */
    @Test
    fun `eine verspaetete rueckgabe raeumt die neue ausleihe nicht ab`() {
        val halter = Erkennerhalter(zusammenhang)
        val alterSatz = halter.leihe("Diktat", vorrang = true)!!
        // Der nächste Satz übernimmt, bevor der alte zurückgegeben hat.
        val neuerSatz = halter.leihe("Diktat", vorrang = true)!!
        // Jetzt kommt die verspätete Rückgabe des alten Satzes.
        halter.gibZurueck(alterSatz, wegwerfen = true)
        assertTrue(
            "Die neue Ausleihe muss die verspätete Rückgabe überleben",
            halter.istVerliehen()
        )
        // Und die echte Rückgabe des neuen Satzes funktioniert weiter.
        halter.gibZurueck(neuerSatz)
        assertFalse(halter.istVerliehen())
    }

    /** Dasselbe Diktat übernimmt seinen warmen Erkenner statt ihn zu zerstören. */
    @Test
    fun `uebernahme durch denselben zweck behaelt den erkenner`() {
        val halter = Erkennerhalter(zusammenhang)
        val erster = halter.leihe("Diktat", vorrang = true)!!
        val zweiter = halter.leihe("Diktat", vorrang = true)!!
        assertTrue(
            "Der warme Erkenner muss übernommen werden, nicht ersetzt",
            erster.erkenner === zweiter.erkenner
        )
    }

    /** Das Diktat verdrängt eine hängende Sprachabfrage -- mit frischem Erkenner. */
    @Test
    fun `vorrang verdraengt fremden zweck mit frischem erkenner`() {
        val halter = Erkennerhalter(zusammenhang)
        val liste = halter.leihe("Sprachliste")!!
        val diktat = halter.leihe("Diktat", vorrang = true)
        assertNotNull("Das Diktat darf nicht warten", diktat)
        assertFalse(
            "Nach einer Verdrängung ist der alte Erkenner unbrauchbar",
            liste.erkenner === diktat!!.erkenner
        )
        // Die verspätete Rückgabe der verdrängten Liste ist ein Nichts.
        halter.gibZurueck(liste, wegwerfen = true)
        assertTrue(halter.istVerliehen())
    }

    @Test
    fun `doppelte rueckgabe desselben scheins ist harmlos`() {
        val halter = Erkennerhalter(zusammenhang)
        val schein = halter.leihe("Diktat")!!
        halter.gibZurueck(schein)
        halter.gibZurueck(schein)
        assertFalse(halter.istVerliehen())
        assertNotNull(halter.leihe("Diktat"))
    }
}
