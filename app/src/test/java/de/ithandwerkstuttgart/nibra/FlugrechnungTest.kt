package de.ithandwerkstuttgart.nibra

import de.ithandwerkstuttgart.nibra.dienst.Flugrechnung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueft den Blasenflug als Rechnung.
 *
 * Am Geraet liesse sich das nur mit dem Finger nachstellen -- und ein
 * "extrem schneller Wurf" ist von Hand nicht wiederholbar. Hier ist er eine
 * Zahl.
 *
 * Masse eines gewoehnlichen Telefons: 1080 breit, Blase 168 (56 dp bei
 * Dichte 3), Rand 48 (16 dp). Damit liegt die rechte Kante bei 48, die linke
 * bei 1080 - 168 - 48 = 864.
 */
class FlugrechnungTest {

    private val fensterbreite = 1080
    private val blasenbreite = 168
    private val rand = 48
    private val kanten = Flugrechnung.kanten(fensterbreite, blasenbreite, rand)

    private val abstand = Flugrechnung.SCHRITT_MILLIS / 1000f

    /** Laesst die Feder laufen und liefert jeden Zwischenstand zurueck. */
    private fun flug(
        von: Int,
        tempoImBildschirmsinn: Float,
        hoechstensSchritte: Int = 600
    ): List<Flugrechnung.Flugstand> {
        val ziel = Flugrechnung.zielkante(von, tempoImBildschirmsinn, kanten)
        var stand = Flugrechnung.Flugstand(von.toFloat(), -tempoImBildschirmsinn)
        val verlauf = mutableListOf(stand)
        repeat(hoechstensSchritte) {
            if (Flugrechnung.istAngekommen(stand, ziel)) return verlauf
            stand = Flugrechnung.schritt(stand, ziel, abstand)
            verlauf += stand
        }
        return verlauf
    }

    // ------------------------------------------------------------ Zielkante

    @Test
    fun `die kanten liegen dort wo sie sollen`() {
        assertEquals(48, kanten.rechts)
        assertEquals(864, kanten.links)
    }

    @Test
    fun `ohne wurf zieht die naehere kante`() {
        // Nahe am rechten Rand liegen -- x klein heisst rechts.
        assertEquals(kanten.rechts, Flugrechnung.zielkante(100, 0f, kanten))
        // Nahe am linken Rand.
        assertEquals(kanten.links, Flugrechnung.zielkante(800, 0f, kanten))
    }

    @Test
    fun `ein langsamer schubs aendert die richtung nicht`() {
        val langsam = Flugrechnung.WURFSCHWELLE - 1f
        // Rechts liegend, schwach nach links geschubst: bleibt rechts.
        assertEquals(kanten.rechts, Flugrechnung.zielkante(100, -langsam, kanten))
    }

    /**
     * Der eigentliche Sinn der Wurfschwelle: quer ueber den Bildschirm
     * geworfen landet die Blase drueben, auch wenn sie beim Loslassen noch
     * auf der alten Seite war.
     */
    @Test
    fun `ein harter wurf gewinnt gegen die naehe`() {
        val hart = Flugrechnung.WURFSCHWELLE + 1f
        // Ganz rechts liegend, hart nach links geworfen.
        assertEquals(kanten.links, Flugrechnung.zielkante(60, -hart, kanten))
        // Ganz links liegend, hart nach rechts geworfen.
        assertEquals(kanten.rechts, Flugrechnung.zielkante(850, hart, kanten))
    }

    // -------------------------------------------------------- Federverlauf

    @Test
    fun `die feder kommt immer an`() {
        val faelle = listOf(
            60 to 0f,
            60 to -5_000f,
            850 to 8_000f,
            432 to 0f,
            432 to -20_000f
        )
        faelle.forEach { (von, tempo) ->
            val verlauf = flug(von, tempo)
            val ziel = Flugrechnung.zielkante(von, tempo, kanten)
            assertTrue(
                "von $von mit $tempo kam nicht an (${verlauf.size} Schritte)",
                Flugrechnung.istAngekommen(verlauf.last(), ziel)
            )
        }
    }

    @Test
    fun `die feder kommt in unter zwei sekunden an`() {
        val verlauf = flug(850, -3_000f)
        val dauerMillis = verlauf.size * Flugrechnung.SCHRITT_MILLIS
        assertTrue("Flug dauerte $dauerMillis ms", dauerMillis < 2_000)
    }

    /**
     * Die Daempfung liegt unter 1 -- die Feder schwingt also ueber ihr Ziel
     * hinaus. Das ist gewollt und soll auch so bleiben; ohne Ueberschwingen
     * wirkt der Wurf tot.
     */
    @Test
    fun `die feder schwingt ueber das ziel hinaus`() {
        val von = 850
        val ziel = Flugrechnung.zielkante(von, -3_000f, kanten)
        val verlauf = flug(von, -3_000f)
        val ueberschritten = verlauf.any { it.stelle < ziel - 1f }
        assertTrue("Kein Ueberschwingen -- der Wurf wirkt tot", ueberschritten)
    }

    // -------------------------------------------------- Nie ausserhalb

    /**
     * Der wichtigste Test dieser Datei. Die Blase darf **nie** ausserhalb des
     * Bildes liegen -- auch nicht fuer ein einziges Bild waehrend des
     * Ueberschwingens.
     */
    @Test
    fun `die blase verlaesst das bild in keinem einzigen schritt`() {
        val faelle = listOf(
            60 to -50_000f,
            850 to 50_000f,
            0 to -100_000f,
            864 to 100_000f
        )
        faelle.forEach { (von, tempo) ->
            flug(von, tempo).forEach { stand ->
                val gesetzt = Flugrechnung.imBild(stand.stelle, kanten)
                assertTrue(
                    "von $von mit $tempo: $gesetzt liegt ausserhalb",
                    gesetzt in 0..kanten.links
                )
            }
        }
    }

    @Test
    fun `die senkrechte lage bleibt greifbar`() {
        val hoehe = 2340
        val blase = 168
        assertEquals(rand, Flugrechnung.senkrechtImBild(-500, hoehe, blase, rand))
        assertEquals(
            hoehe - blase - rand,
            Flugrechnung.senkrechtImBild(99_999, hoehe, blase, rand)
        )
        assertEquals(1000, Flugrechnung.senkrechtImBild(1000, hoehe, blase, rand))
    }

    // ------------------------------------------------------------ Randfaelle

    /**
     * Auf einem sehr schmalen Bildschirm passen Blase und zwei Raender nicht
     * nebeneinander. Dann faellt alles auf eine Lage zusammen -- die aber
     * noch im Bild liegt.
     */
    @Test
    fun `ein zu schmales fenster ergibt eine gueltige lage`() {
        val eng = Flugrechnung.kanten(fensterbreite = 100, blasenbreite = 168, randAbstand = 48)
        assertEquals(0, eng.links)
        assertEquals(0, eng.rechts)
        assertEquals(0, Flugrechnung.zielkante(0, 0f, eng))
        assertEquals(0, Flugrechnung.imBild(-999f, eng))
        assertEquals(0, Flugrechnung.imBild(999f, eng))
    }

    @Test
    fun `genau auf der mitte losgelassen ergibt eine der beiden kanten`() {
        val mitte = (kanten.links + kanten.rechts) / 2
        val ziel = Flugrechnung.zielkante(mitte, 0f, kanten)
        assertTrue(ziel == kanten.links || ziel == kanten.rechts)
    }

    @Test
    fun `genau auf der wurfschwelle gilt noch die naehe`() {
        // Die Schwelle ist ausschliessend: erst darueber gewinnt der Wurf.
        assertEquals(
            kanten.rechts,
            Flugrechnung.zielkante(100, -Flugrechnung.WURFSCHWELLE, kanten)
        )
    }
}
