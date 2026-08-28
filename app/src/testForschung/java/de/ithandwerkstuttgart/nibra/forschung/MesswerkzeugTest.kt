package de.ithandwerkstuttgart.nibra.forschung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Handgerechnete Prüfungen für alles, worauf eine Architekturentscheidung
 * beruht.
 *
 * An einem einzigen Tag haben vier Fehlmessungen etwas Funktionierendes
 * als kaputt gemeldet:
 *
 * 1. `EXTRA_AUDIO_SOURCE` galt als „nicht unterstützt", weil nur auf
 *    `onResults` gesehen wurde -- der Erkenner hatte alles verstanden.
 * 2. Alternativen wurden verkettet, das ergab 133 % Wortfehlerrate.
 * 3. Eine Abweichung von -70 ms galt als fehlender Ton -- dabei kamen
 *    **mehr** Abtastwerte an als Zeit verging.
 * 4. Der Vorlauf galt als belegt, obwohl der Versuch nichts gezeigt hatte.
 *
 * Jede dieser Zahlen trägt Entscheidungen. Deshalb hat jetzt jede einen
 * Selbsttest mit von Hand nachgerechnetem Ergebnis.
 */
class MesswerkzeugTest {

    // ---------------------------------------------------------- Wortfehler

    @Test
    fun `gleicher text ergibt null prozent`() {
        val b = Wortvergleich.vergleiche("guten morgen", "guten morgen")
        assertEquals(0.0, b.fehlerrate, 0.0001)
    }

    /** Ein Wort von zweien fehlt: eine Auslassung auf zwei Bezugsworte. */
    @Test
    fun `eine auslassung von zwei worten ergibt fuenfzig prozent`() {
        val b = Wortvergleich.vergleiche("guten morgen", "guten")
        assertEquals(1, b.fehlt)
        assertEquals(0.5, b.fehlerrate, 0.0001)
    }

    /** Ein Wort zu viel: eine Einfügung auf zwei Bezugsworte. */
    @Test
    fun `eine einfuegung ergibt fuenfzig prozent`() {
        val b = Wortvergleich.vergleiche("guten morgen", "guten schoenen morgen")
        assertEquals(1, b.zusätzlich)
        assertEquals(0.5, b.fehlerrate, 0.0001)
    }

    @Test
    fun `eine ersetzung ergibt fuenfzig prozent`() {
        val b = Wortvergleich.vergleiche("guten morgen", "guten abend")
        assertEquals(1, b.ersetzt)
        assertEquals(0.5, b.fehlerrate, 0.0001)
    }

    /**
     * Der Fehler, der 133 % erzeugte: zwei Deutungen **derselben** Stelle
     * wurden aneinandergehängt. Eine Alternative ist kein zusätzlich
     * gesprochenes Wort.
     */
    @Test
    fun `verkettete alternativen treiben die fehlerrate ueber hundert prozent`() {
        // Von Hand: Bezug hat 2 Wörter. Die Verkettung liefert 4 --
        // „guten morgen" trifft, „guten abend" kommt als zwei Einfügungen
        // obendrauf. 2 Einfügungen auf 2 Bezugsworte sind genau 100 %.
        val verkettet = Wortvergleich.vergleiche(
            "guten morgen",
            "guten morgen | guten abend"
        )
        assertEquals(2, verkettet.zusätzlich)
        assertEquals(1.0, verkettet.fehlerrate, 0.0001)

        // Mit einer dritten Alternative steigt sie über 100 % -- genau das
        // war am Gerät zu sehen (133 % für einen Satz, der fast stimmte).
        val dreifach = Wortvergleich.vergleiche(
            "guten morgen",
            "guten morgen | guten abend | schoenen abend"
        )
        assertTrue(
            "Drei Alternativen müssen über 100 % treiben: ${dreifach.fehlerrate}",
            dreifach.fehlerrate > 1.0
        )
        // Und so sieht es richtig aus: nur die beste Lesart.
        val richtig = Wortvergleich.vergleiche("guten morgen", "guten morgen")
        assertEquals(0.0, richtig.fehlerrate, 0.0001)
    }

    // ------------------------------------------------------- Ergebniswahl

    @Test
    fun `segmente gehen vor endergebnis und zwischenstand`() {
        val wahl = Ergebniswahl.waehle(
            segmente = listOf("guten Morgen"),
            endergebnis = listOf("etwas anderes"),
            zwischenstaende = listOf("noch etwas anderes")
        )
        assertEquals("guten Morgen", wahl.text)
        assertEquals(Ergebniswahl.Herkunft.SEGMENT, wahl.herkunft)
    }

    @Test
    fun `ohne segmente zaehlt das endergebnis`() {
        val wahl = Ergebniswahl.waehle(
            segmente = emptyList(),
            endergebnis = listOf("guten Morgen", "guten Abend"),
            zwischenstaende = listOf("gut")
        )
        assertEquals("guten Morgen", wahl.text)
        assertEquals(Ergebniswahl.Herkunft.ENDERGEBNIS, wahl.herkunft)
    }

    /** Der Fall, der heute den ganzen Tag bestimmt hat. */
    @Test
    fun `bei leerem endergebnis rettet der letzte zwischenstand`() {
        val wahl = Ergebniswahl.waehle(
            segmente = emptyList(),
            endergebnis = emptyList(),
            zwischenstaende = listOf("guten", "guten Morgen", "guten Morgen hier")
        )
        assertEquals("guten Morgen hier", wahl.text)
        assertEquals(Ergebniswahl.Herkunft.ZWISCHENSTAND, wahl.herkunft)
    }

    /** Mehrere Segmente sind **nacheinander** Gesprochenes, keine Alternativen. */
    @Test
    fun `mehrere segmente werden aneinandergehaengt`() {
        val wahl = Ergebniswahl.waehle(
            segmente = listOf("guten Morgen", "hier spricht Belkis"),
            endergebnis = emptyList(),
            zwischenstaende = emptyList()
        )
        assertEquals("guten Morgen hier spricht Belkis", wahl.text)
    }

    @Test
    fun `ohne jeden text ist die herkunft keiner`() {
        val wahl = Ergebniswahl.waehle(emptyList(), emptyList(), emptyList())
        assertFalse(wahl.hatText)
        assertEquals(Ergebniswahl.Herkunft.KEINER, wahl.herkunft)
    }

    /**
     * Die Fehlmessung, die `EXTRA_AUDIO_SOURCE` fast verworfen hätte: ein
     * leeres Endergebnis heißt **nicht** „nicht unterstützt", wenn vorher
     * gültige Segmente kamen.
     */
    @Test
    fun `leeres endergebnis widerlegt die unterstuetzung nicht`() {
        assertTrue(
            "Segmente belegen, dass der Strom gelesen wurde",
            Ergebniswahl.wurdeUnterstuetzt(
                segmente = listOf("guten Morgen"),
                endergebnis = emptyList(),
                zwischenstaende = emptyList()
            )
        )
        assertTrue(
            "Auch Zwischenstände belegen es",
            Ergebniswahl.wurdeUnterstuetzt(
                segmente = emptyList(),
                endergebnis = emptyList(),
                zwischenstaende = listOf("guten")
            )
        )
        assertFalse(
            "Ohne jeden Text ist nichts belegt",
            Ergebniswahl.wurdeUnterstuetzt(emptyList(), emptyList(), emptyList())
        )
    }

    // --------------------------------------------------- Deduplizierung

    @Test
    fun `ein verlaengerter abschnitt ersetzt den kuerzeren`() {
        val ohne = Ergebniswahl.ohneWiederholung(
            listOf("guten Morgen", "guten Morgen hier spricht")
        )
        assertEquals(listOf("guten Morgen hier spricht"), ohne)
    }

    @Test
    fun `verschiedene abschnitte bleiben beide erhalten`() {
        val ohne = Ergebniswahl.ohneWiederholung(
            listOf("guten Morgen", "wie geht es dir")
        )
        assertEquals(listOf("guten Morgen", "wie geht es dir"), ohne)
    }

    @Test
    fun `derselbe abschnitt zweimal bleibt einmal`() {
        assertEquals(
            listOf("guten Morgen"),
            Ergebniswahl.ohneWiederholung(listOf("guten Morgen", "guten Morgen"))
        )
    }

    // ------------------------------------------- Verlust gegen die Uhr

    /**
     * Die Fehlmessung, die einen einwandfreien Lauf als „ES FEHLT TON"
     * meldete: geprüft wurde der Betrag statt der Richtung.
     */
    @Test
    fun `nur ein positiver rueckstand ist verlust`() {
        assertTrue(
            "Gleichstand ist lückenlos",
            Tonstrecke.istLueckenlos(verworfeneBloecke = 0, verlustMillis = 0)
        )
        assertTrue(
            "Mehr Abtastwerte als Zeit ist kein Verlust, sondern Randungenauigkeit",
            Tonstrecke.istLueckenlos(verworfeneBloecke = 0, verlustMillis = -70)
        )
        assertTrue(
            "Kleiner Rückstand liegt in der Toleranz",
            Tonstrecke.istLueckenlos(verworfeneBloecke = 0, verlustMillis = 50)
        )
        assertFalse(
            "Eine echte Lücke muss auffallen",
            Tonstrecke.istLueckenlos(verworfeneBloecke = 0, verlustMillis = 500)
        )
        assertFalse(
            "Ein verworfener Block ist immer ein Verlust, egal wie die Uhr steht",
            Tonstrecke.istLueckenlos(verworfeneBloecke = 1, verlustMillis = -70)
        )
    }

    // ------------------------------------------------------- Vorlaufpuffer

    /**
     * Der Kern des Vorlaufs, an einer bekannten Folge geprüft -- ohne
     * Android, ohne Erkenner. Kommt hier etwas anderes heraus als
     * `1 2 3 4 5`, ist der Vorlauf kaputt, und das wäre am Gerät erst an
     * unerklärlich schlechter Erkennung aufgefallen.
     */
    @Test
    fun `vorlauf und live ergeben genau die erwartete folge`() {
        val puffer = Vorlaufpuffer(hoechstens = 3)
        // Aufgenommen wird 1 2 3, dann startet die Erkennung, dann 4 5.
        listOf(1, 2, 3).forEach { puffer.lege(byteArrayOf(it.toByte())) }
        val gesendet = mutableListOf<Byte>()
        puffer.nimmHeraus().forEach { gesendet += it.toList() }
        listOf(4, 5).forEach { gesendet += it.toByte() }
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), gesendet)
    }

    /** Ist der Puffer voll, fällt das Älteste heraus, nicht das Jüngste. */
    @Test
    fun `ein voller puffer behaelt die juengsten bloecke`() {
        val puffer = Vorlaufpuffer(hoechstens = 3)
        listOf(1, 2, 3, 4, 5).forEach { puffer.lege(byteArrayOf(it.toByte())) }
        val heraus = puffer.nimmHeraus().map { it.first() }
        assertEquals(listOf<Byte>(3, 4, 5), heraus)
    }

    @Test
    fun `der puffer ist nach dem herausnehmen leer`() {
        val puffer = Vorlaufpuffer(hoechstens = 3)
        puffer.lege(byteArrayOf(1))
        puffer.nimmHeraus()
        assertEquals(0, puffer.anzahl)
        assertTrue(puffer.nimmHeraus().isEmpty())
    }

    @Test
    fun `ein abgeschalteter vorlauf haelt nichts`() {
        val puffer = Vorlaufpuffer(hoechstens = 0)
        puffer.lege(byteArrayOf(1))
        assertEquals(0, puffer.anzahl)
    }

    /** Von Hand nachgerechnet: 1500 ms bei 16 kHz, 16 Bit, Blöcke zu 2048 B. */
    @Test
    fun `die blockzahl fuer eine dauer wird aufgerundet`() {
        // 1500 ms * 16000 * 2 / 1000 = 48000 Bytes; 48000 / 2048 = 23,4 -> 24
        assertEquals(24, Vorlaufpuffer.bloeckeFuer(1500, 2048, 16_000))
        // 0 ms bleibt 0
        assertEquals(0, Vorlaufpuffer.bloeckeFuer(0, 2048, 16_000))
        // Genau ein Block: 128 ms * 16000 * 2 / 1000 = 4096 = 2 Blöcke
        assertEquals(2, Vorlaufpuffer.bloeckeFuer(128, 2048, 16_000))
    }

    // ---- Kennzahlen -------------------------------------------------
    //
    // Von Hand gerechnet, nächster Rang. Bei vier Werten [10,20,30,40]:
    // P50 -> ceil(0.5*4) = 2 -> zweiter Wert = 20. Nicht 25: interpoliert
    // wäre 25, aber 25 wurde nie gemessen.

    @Test
    fun `perzentil nimmt den naechsten rang, nicht den zwischenwert`() {
        val werte = listOf(40L, 10L, 30L, 20L)
        assertEquals(20L, Kennzahlen.perzentil(werte, 0.5))
        assertEquals(40L, Kennzahlen.perzentil(werte, 0.95))
        assertEquals(10L, Kennzahlen.perzentil(werte, 0.0))
        assertEquals(40L, Kennzahlen.perzentil(werte, 1.0))
    }

    @Test
    fun `perzentil aus zwanzig werten trifft den erwarteten rang`() {
        // 1..20: P50 -> Rang 10 -> 10. P95 -> ceil(0.95*20) = 19 -> 19.
        val werte = (1L..20L).toList()
        assertEquals(10L, Kennzahlen.perzentil(werte, 0.5))
        assertEquals(19L, Kennzahlen.perzentil(werte, 0.95))
    }

    /**
     * Gegenprobe: ohne Messwerte darf **keine Zahl** herauskommen. Eine 0
     * wäre hier die gefährlichste Antwort -- sie läse sich als „kein
     * Verzug", also als bestmögliches Ergebnis.
     */
    @Test
    fun `ohne messwerte gibt es keine kennzahl, auch keine null`() {
        assertNull(Kennzahlen.perzentil(emptyList(), 0.5))
        assertNull(Kennzahlen.mittel(emptyList()))
    }

    @Test
    fun `mittel rundet kaufmaennisch`() {
        assertEquals(2L, Kennzahlen.mittel(listOf(1L, 2L, 2L)))
        assertEquals(3L, Kennzahlen.mittel(listOf(2L, 3L)))
    }

    @Test
    fun `die ausbeute zaehlt die luecken mit`() {
        assertEquals(2 to 4, Kennzahlen.ausbeute(listOf(1L, null, 3L, null)))
        assertEquals(0 to 3, Kennzahlen.ausbeute(listOf(null, null, null)))
    }

}
