package de.ithandwerkstuttgart.nibra.forschung

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regeln am Quelltext für die beiden Fehlmessungen, die sich mit einem
 * gewöhnlichen Test nicht einfangen lassen.
 *
 * Fehlmessung 6 und 7 lagen nicht in einer Rechnung, sondern **an einer
 * Stelle**: an der Zeile, in der die Uhr gestartet wurde, und an dem
 * Zeitraum, über den gerechnet wurde. Beides ist an einer Zahl nicht zu
 * erkennen -- die kam ja heraus, sie war nur falsch. Deshalb hier eine
 * Regel über den Quelltext.
 */
class BauartMessungTest {

    private fun tonstrecke(): String =
        File("src/forschung/java/de/ithandwerkstuttgart/nibra/forschung/Tonstrecke.kt")
            .readText()

    /**
     * Die Uhr muss laufen, bevor der erste Block gelesen wird.
     *
     * Stand sie danach, war der Ton des ersten Blocks gezählt, seine Zeit
     * aber nicht -- ein fester Versatz von rund 76 ms, der auf fünf
     * Sekunden wie 15 000 ppm Taktabweichung aussah.
     */
    @Test
    fun `die uhr startet vor der ersten lesung`() {
        val quelle = tonstrecke()
        val startZeile = quelle.indexOf("uhrStart = SystemClock.elapsedRealtime()")
        val ersteLesung = quelle.indexOf("aufnahme.read(")
        assertTrue("uhrStart muss im Quelltext vor der Leseschleife stehen",
            startZeile in 1 until ersteLesung)
        assertTrue("Die Uhr gehört an den Start der Aufnahme",
            quelle.substring(0, startZeile).contains("aufnahme.startRecording()"))
    }

    /**
     * Takt und Verlust dürfen nur über den eingeschwungenen Teil gerechnet
     * werden.
     *
     * Über den ganzen Lauf gerechnet enthalten beide die Anlaufzeit des
     * Mikrofons. Das A15 meldete so 149 ms fehlenden Ton bei **null**
     * verworfenen Blöcken.
     */
    @Test
    fun `takt und verlust lassen die raender aus`() {
        val quelle = tonstrecke()
        val abschnitt = quelle.substringAfter("verlustMillis =").take(300)
        assertTrue("Der Verlust muss vom Einschwungpunkt aus gerechnet werden",
            abschnitt.contains("einschwungUhr") && abschnitt.contains("einschwungRahmen"))
        assertTrue("Der Takt braucht eigene Felder für den eingeschwungenen Teil",
            quelle.contains("taktRahmen") && quelle.contains("taktMillis"))
    }

    /**
     * Gegenprobe zu beiden Regeln: die alten, falschen Fassungen müssen
     * durchfallen. Ohne sie wüsste niemand, ob die Regeln überhaupt etwas
     * prüfen.
     */
    @Test
    fun `gegenprobe -- die alten fassungen fallen durch`() {
        val alteUhr = """
            aufnahme.startRecording()
            while (true) {
                val gelesen = aufnahme.read(block, 0, block.size)
                if (erster) { uhrStart = SystemClock.elapsedRealtime() }
            }
        """.trimIndent()
        assertFalse(
            "Die alte Fassung startet die Uhr nach der ersten Lesung",
            alteUhr.indexOf("uhrStart =") < alteUhr.indexOf("aufnahme.read(")
        )
        val alterVerlust = "verlustMillis = laufzeit - nachAbtastwerten,"
        assertFalse(
            "Die alte Fassung rechnet über den ganzen Lauf",
            alterVerlust.contains("einschwungUhr")
        )
    }

    private fun vergleich(): String =
        File("src/forschung/java/de/ithandwerkstuttgart/nibra/forschung/Vergleichsversuch.kt")
            .readText()

    /**
     * Ein Versuch darf die Lautstaerke des Geraets nicht verstellen.
     *
     * Die erste Fassung setzte sie auf vier Fuenftel des Hoechstwertes und
     * hat damit um fuenf Uhr morgens ein stumm gestelltes Geraet zum
     * Sprechen gebracht. Eine Messung darf eine Einstellung des Nutzers
     * nicht ueberschreiben -- auch nicht, wenn sie sie hinterher
     * zuruecksetzt.
     */
    @Test
    fun `kein versuch verstellt die lautstaerke`() {
        val quellen = File("src/forschung/java/de/ithandwerkstuttgart/nibra/forschung")
            .listFiles { _, n -> n.endsWith(".kt") }.orEmpty()
        quellen.forEach { datei ->
            val text = datei.readText()
            assertFalse(
                "${datei.name} verstellt die Lautstaerke",
                text.contains("setStreamVolume")
            )
        }
    }

    /**
     * Der hoerbare Versuch laeuft nur mit ausdruecklicher Freigabe.
     *
     * Die Pruefung muss **vor** dem Abspielen stehen, sonst hat das Geraet
     * schon gesprochen, wenn sie zuschlaegt.
     */
    @Test
    fun `hoerbares abspielen braucht eine freigabe`() {
        val quelle = vergleich()
        val pruefung = quelle.indexOf("if (!tonErlaubt)")
        val abspielen = quelle.indexOf("spieler.start()")
        assertTrue("Die Freigabepruefung fehlt", pruefung >= 0)
        assertTrue("Die Pruefung muss vor dem Abspielen stehen", pruefung < abspielen)
    }

    /** Gegenprobe: eine Fassung, die erst spielt und dann fragt, faellt durch. */
    @Test
    fun `gegenprobe -- freigabe nach dem abspielen faellt durch`() {
        val erfunden = "spieler.start()\nif (!tonErlaubt) return"
        assertFalse(
            "Diese Reihenfolge darf die Regel nicht bestehen",
            erfunden.indexOf("if (!tonErlaubt)") < erfunden.indexOf("spieler.start()")
        )
    }

}
