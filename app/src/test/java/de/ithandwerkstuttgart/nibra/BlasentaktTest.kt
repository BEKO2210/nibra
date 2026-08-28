package de.ithandwerkstuttgart.nibra

import de.ithandwerkstuttgart.nibra.dienst.Blasentakt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * Prueft die Systemfaelle der Blasenzeichnung als Rechnung.
 *
 * Am Geraet waeren das mehrere Minuten Handarbeit je Fall -- Bildschirm aus,
 * Bildschirm an, Animationen abschalten, wieder anschalten -- und man saehe
 * am Ende nur, dass es "irgendwie geht".
 */
class BlasentaktTest {

    // ------------------------------------------------------------ Der Takt

    /**
     * Der Fall, der am ehesten uebersehen wird: waehrend eines Diktats geht
     * der Bildschirm aus. Das Diktat laeuft weiter, aber es sieht niemand
     * hin -- dann darf nichts gerechnet werden.
     */
    @Test
    fun `unsichtbar wird nicht gerechnet obwohl das diktat laeuft`() {
        assertFalse(
            Blasentakt.sollLaufen(laeuft = true, sichtbar = false, bewegungErlaubt = true)
        )
    }

    @Test
    fun `nur diktierend und sichtbar und mit erlaubter bewegung laeuft der takt`() {
        assertTrue(Blasentakt.sollLaufen(true, sichtbar = true, bewegungErlaubt = true))
        assertFalse(Blasentakt.sollLaufen(false, sichtbar = true, bewegungErlaubt = true))
        assertFalse(Blasentakt.sollLaufen(true, sichtbar = true, bewegungErlaubt = false))
        assertFalse(Blasentakt.sollLaufen(false, sichtbar = false, bewegungErlaubt = false))
    }

    // --------------------------------------------------------- Der Umlauf

    @Test
    fun `der umlauf bleibt in seiner periode`() {
        val periode = (200.0 * PI).toFloat()
        var zeit = 0f
        repeat(20_000) {
            zeit = Blasentakt.naechsteZeit(zeit, 0.033f, 0.7f, periode)
            assertTrue("Zeit $zeit ausserhalb der Periode", zeit in 0f..periode)
        }
    }

    /**
     * Der eigentliche Grund fuer die krumme Periode: beim Ueberlauf muessen
     * alle drei Wolken dort stehen, wo sie am Anfang standen. Sonst springt
     * das Bild -- genau der Fehler, der frueher alle neun Sekunden auftrat.
     */
    @Test
    fun `beim ueberlauf stehen alle drei wolken wieder am anfang`() {
        val periode = (200.0 * PI).toFloat()
        val tempi = floatArrayOf(0.55f, 0.37f, 0.48f, 0.42f, 0.61f, 0.33f)
        tempi.forEach { tempo ->
            val amAnfang = cos(0f * tempo)
            val amEnde = cos(periode * tempo)
            assertEquals(
                "Tempo $tempo springt beim Ueberlauf",
                amAnfang.toDouble(), amEnde.toDouble(), 0.001
            )
        }
    }

    // ------------------------------------------------------- Die Glaettung

    @Test
    fun `die glaettung naehert sich dem ziel und schiesst nicht darueber`() {
        var wert = 0f
        repeat(200) {
            wert = Blasentakt.geglaettet(wert, ziel = 1f, abstandSekunden = 0.033f, zeitkonstante = 0.15f)
            assertTrue("ueberschossen: $wert", wert <= 1.0001f)
        }
        assertTrue("nicht angekommen: $wert", wert > 0.99f)
    }

    /**
     * Der Sinn der zeitbasierten Rechnung: bei 30 und bei 60 Bildern je
     * Sekunde muss nach derselben **Zeit** derselbe Wert stehen. Ein fester
     * Anteil je Bild haenge dagegen an der Bildrate.
     */
    @Test
    fun `die glaettung haengt an der zeit und nicht an der bildrate`() {
        var bei30 = 0f
        repeat(30) { bei30 = Blasentakt.geglaettet(bei30, 1f, 1f / 30f, 0.15f) }

        var bei60 = 0f
        repeat(60) { bei60 = Blasentakt.geglaettet(bei60, 1f, 1f / 60f, 0.15f) }

        assertTrue(
            "30/s ergab $bei30, 60/s ergab $bei60",
            abs(bei30 - bei60) < 0.01f
        )
    }

    /** Dauerhafte Stille laesst die Flaeche zur Ruhe kommen, nicht zittern. */
    @Test
    fun `dauerhafte stille faehrt die flaeche auf null`() {
        var wert = 1f
        repeat(200) { wert = Blasentakt.geglaettet(wert, 0f, 0.033f, 0.15f) }
        assertTrue("blieb bei $wert stehen", wert < 0.01f)
    }

    /** Dauerhaft hoher Pegel bleibt oben stehen und laeuft nicht ueber. */
    @Test
    fun `dauerhaft hoher pegel bleibt bei eins`() {
        var wert = 0f
        repeat(500) { wert = Blasentakt.geglaettet(wert, 1f, 0.033f, 0.15f) }
        assertTrue("lief auf $wert", wert in 0.99f..1.0001f)
    }

    /**
     * Stark schwankender Pegel: die Flaeche darf mitgehen, aber nie ueber
     * die Grenzen hinaus. Sonst wuerde der Blob aus dem Kreis wachsen.
     */
    @Test
    fun `stark schwankender pegel bleibt zwischen null und eins`() {
        var wert = 0f
        repeat(600) { schritt ->
            val ziel = if (schritt % 2 == 0) 1f else 0f
            wert = Blasentakt.geglaettet(wert, ziel, 0.033f, 0.15f)
            assertTrue("ausserhalb: $wert", wert in -0.0001f..1.0001f)
        }
    }

    /**
     * Ein ausgefallenes Bild -- etwa nach einer Pause -- darf keinen Sprung
     * erzeugen, der ueber das Ziel hinausschiesst.
     */
    @Test
    fun `ein langer zeitsprung schiesst nicht ueber das ziel`() {
        val wert = Blasentakt.geglaettet(0f, ziel = 1f, abstandSekunden = 10f, zeitkonstante = 0.15f)
        assertTrue("sprang auf $wert", wert in 0f..1.0001f)
    }
}
