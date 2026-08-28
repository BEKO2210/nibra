package de.ithandwerkstuttgart.nibra.forschung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Wortvergleich trägt später die Entscheidung, ob Nebenlauf die
 * Erkennung verschlechtert. Rechnet er falsch, ist die Entscheidung falsch --
 * und man merkt es nicht, weil die Zahl plausibel aussieht.
 */
class WortvergleichTest {

    @Test
    fun `gleicher text ergibt keine fehler`() {
        val befund = Wortvergleich.vergleiche("Guten Morgen Belkis", "Guten Morgen Belkis")
        assertEquals(0.0, befund.fehlerrate, 0.0001)
        assertEquals(3, befund.gleich)
    }

    /** Schreibweisen sind keine Hörfehler und dürfen nicht als solche zählen. */
    @Test
    fun `umlaute satzzeichen und großschreibung zählen nicht als fehler`() {
        val befund = Wortvergleich.vergleiche(
            "Zwei Geräten, vollständig geprüft.",
            "zwei geraeten vollstaendig geprueft"
        )
        assertEquals(0.0, befund.fehlerrate, 0.0001)
    }

    @Test
    fun `ausgelassenes wort zählt als auslassung`() {
        val befund = Wortvergleich.vergleiche("eins zwei drei vier", "eins drei vier")
        assertEquals(1, befund.fehlt)
        assertEquals(0, befund.ersetzt)
        assertEquals(0, befund.zusätzlich)
        assertEquals(0.25, befund.fehlerrate, 0.0001)
    }

    @Test
    fun `zusätzliches wort zählt als einfügung`() {
        val befund = Wortvergleich.vergleiche("eins zwei", "eins und zwei")
        assertEquals(1, befund.zusätzlich)
        assertEquals(2, befund.gleich)
    }

    @Test
    fun `falsch gehörtes wort zählt als ersetzung`() {
        val befund = Wortvergleich.vergleiche("Herrn Doktor Weinreich", "Herrn Doktor Weinrich")
        assertEquals(1, befund.ersetzt)
        assertEquals(2, befund.gleich)
    }

    /**
     * Der Fall, an dem ein naiver Vergleich zerbricht: ein fehlendes Wort ganz
     * vorn verschiebt alles danach. Die Ausrichtung muss das auffangen, sonst
     * meldet sie einen Totalausfall, wo ein einziges Wort fehlt.
     */
    @Test
    fun `ein fehlendes wort am anfang verschiebt nicht den ganzen rest`() {
        val befund = Wortvergleich.vergleiche(
            "guten morgen hier spricht belkis aslani",
            "morgen hier spricht belkis aslani"
        )
        assertEquals(1, befund.fehlt)
        assertEquals(5, befund.gleich)
        assertEquals(0, befund.ersetzt)
    }

    @Test
    fun `ziffern werden zu zahlwörtern und gelten dann als treffer`() {
        val befund = Wortvergleich.vergleiche(
            "zweihundertvierzig Bauteile",
            "240 Bauteile"
        )
        assertEquals(0.0, befund.fehlerrate, 0.0001)
    }

    @Test
    fun `zahlwörter decken die fälle des bezugstexts ab`() {
        assertEquals("vierzehn", Wortvergleich.zahlwort(14))
        assertEquals("dreissig", Wortvergleich.zahlwort(30))
        assertEquals("drei", Wortvergleich.zahlwort(3))
        assertEquals("zweihundertvierzig", Wortvergleich.zahlwort(240))
        assertEquals("achthundert", Wortvergleich.zahlwort(800))
        assertEquals("einundzwanzig", Wortvergleich.zahlwort(21))
        assertEquals("null", Wortvergleich.zahlwort(0))
    }

    /**
     * Zu große Zahlen bleiben als Ziffernfolge stehen, statt still falsch
     * übersetzt zu werden -- dann fällt es im Unterschied auf.
     */
    @Test
    fun `sehr große zahlen bleiben unverändert`() {
        assertEquals("1000000", Wortvergleich.zahlwort(1_000_000))
    }

    @Test
    fun `leeres transkript ergibt lauter auslassungen`() {
        val befund = Wortvergleich.vergleiche("eins zwei drei", "")
        assertEquals(3, befund.fehlt)
        assertEquals(1.0, befund.fehlerrate, 0.0001)
        assertEquals(0.0, befund.trefferquote, 0.0001)
    }

    @Test
    fun `unterschiede werden lesbar ausgegeben`() {
        val befund = Wortvergleich.vergleiche("eins zwei drei", "eins vier drei fuenf")
        val text = befund.unterschiede()
        assertTrue(text, text.contains("~ zwei -> vier"))
        assertTrue(text, text.contains("+ fuenf"))
    }

    /** Der Bezugstext muss sich sauber zerlegen lassen, sonst misst nichts. */
    @Test
    fun `der bezugstext zerfällt in die erwartete wortzahl`() {
        val worte = Wortvergleich.zerlege(Sprachlauf.BEZUGSTEXT)
        assertEquals(66, worte.size)
        assertTrue(worte.contains("zweihundertvierzig"))
        assertTrue(worte.contains("weinreich"))
    }
}
