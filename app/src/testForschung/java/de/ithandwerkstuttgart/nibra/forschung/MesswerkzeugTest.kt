package de.ithandwerkstuttgart.nibra.forschung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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


    // ---- Namenstreffer ----------------------------------------------

    @Test
    fun `der name zaehlt auch mit satzzeichen und grossschreibung`() {
        assertTrue(Namenstreffer.steckt("das war Aslani.", "aslani"))
        assertTrue(Namenstreffer.steckt("guten morgen belkis", "Belkis"))
    }

    @Test
    fun `ein mehrwortiger name muss zusammenhaengen`() {
        assertTrue(Namenstreffer.steckt(
            "er arbeitet bei d und b audiotechnik heute", "d und b audiotechnik"))
        // Gegenprobe: dieselben Woerter, aber auseinandergerissen. Ohne
        // diese Probe wuerde „und" irgendwo im Satz plus „audiotechnik"
        // zehn Woerter spaeter als Treffer durchgehen.
        assertFalse(Namenstreffer.steckt(
            "d und der rest kam von b audiotechnik", "d und b audiotechnik"))
    }

    @Test
    fun `ein teil des wortes ist kein treffer`() {
        assertFalse(Namenstreffer.steckt("aslanische woerter", "aslani"))
        assertFalse(Namenstreffer.steckt("", "belkis"))
        assertFalse(Namenstreffer.steckt("belkis", ""))
    }


    // ---- Prozessbefund ----------------------------------------------
    //
    // Kontrollfall mit bekanntem Ausgang, bevor die Zahl in einen Bericht
    // kommt. utime = 120 Takte, stime = 30 Takte, 100 Takte je Sekunde
    // -> (120 + 30) * 10 ms = 1500 ms. Von Hand gerechnet.

    private val statZeile =
        "1234 (nibra) S 1 1234 1234 0 -1 4194304 900 0 0 0 120 30 0 0 20 0 14 0 999 0 0"

    @Test
    fun `die rechenzeit kommt aus utime plus stime`() {
        assertEquals(1500L, Prozessbefund.rechenzeitAus(statZeile))
    }

    /**
     * Der Fall, für den die Zerlegung ab der letzten Klammer gebaut ist:
     * ein Prozessname mit Leerzeichen und eigener Klammer. Wer am ersten
     * Leerzeichen trennt, bekommt hier eine Zahl heraus -- nur die falsche.
     */
    @Test
    fun `ein prozessname mit leerzeichen verschiebt die felder nicht`() {
        val heikel =
            "1234 (nibra (forschung) lauf) S 1 1234 1234 0 -1 4194304 900 0 0 0 120 30 0 0 20 0 14 0 999 0 0"
        assertEquals(1500L, Prozessbefund.rechenzeitAus(heikel))
    }

    /** Unlesbares gibt null, nie 0 -- 0 hiesse „keine Rechenzeit verbraucht". */
    @Test
    fun `unlesbares gibt keine rechenzeit, auch keine null`() {
        assertNull(Prozessbefund.rechenzeitAus(""))
        assertNull(Prozessbefund.rechenzeitAus("voelliger unsinn ohne klammer"))
        assertNull(Prozessbefund.rechenzeitAus("1234 (nibra) S 1 2"))
    }


    // ---- Ratenverlauf ------------------------------------------------
    //
    // Von Hand gerechnet. Bei 16 000 Hz und 10 s Abstand erwartet man
    // 160 000 Rahmen je Fenster. Genau so viele -> 0 ppm.

    @Test
    fun `genau die nennrate ergibt keine abweichung`() {
        val zeiten = listOf(10_000L, 20_000L, 30_000L)
        val rahmen = listOf(160_000L, 320_000L, 480_000L)
        val fenster = Ratenverlauf.jeFenster(zeiten, rahmen, 16_000)
        assertEquals(listOf(20_000L to 0L, 30_000L to 0L), fenster)
    }

    /**
     * Ein Prozent mehr Rahmen in einem Fenster sind 10 000 ppm -- und
     * **nur in diesem einen**. Genau dafuer wird zwischen zwei Punkten
     * gerechnet und nicht vom Anfang aus: sonst faerbte der Ausrutscher
     * jedes folgende Fenster mit, und eine ruhige Strecke saehe aus, als
     * erhole sie sich langsam.
     */
    @Test
    fun `ein ausrutscher faerbt die folgenden fenster nicht`() {
        val zeiten = listOf(10_000L, 20_000L, 30_000L, 40_000L)
        val rahmen = listOf(160_000L, 321_600L, 481_600L, 641_600L)
        val ppm = Ratenverlauf.jeFenster(zeiten, rahmen, 16_000).map { it.second }
        assertEquals(listOf(10_000L, 0L, 0L), ppm)
    }

    @Test
    fun `eine ruhige reihe wandert nicht, eine steigende schon`() {
        val ruhig = List(4) { 100L } + List(4) { 105L }
        assertEquals(false, Ratenverlauf.wandert(ruhig, 500.0))
        // Erste Haelfte glatt 100, zweite glatt 1100: Unterschied 1000 bei
        // winziger Streuung -- das ist ein Verlauf.
        val steigend = List(4) { 100L } + List(4) { 1100L }
        assertEquals(true, Ratenverlauf.wandert(steigend, 500.0))
    }

    /**
     * Der Fall, der die erste Fassung hereingelegt hat: gemessene Fenster
     * eines Sechzig-Sekunden-Laufs. Der Unterschied der Haelften ist
     * groesser als die feste Schwelle, aber kleiner als das Rauschen --
     * hier darf **kein** Verlauf gemeldet werden.
     */
    @Test
    fun `ein unterschied im rauschen ist kein verlauf`() {
        val gemessen = listOf(598L, 300L, 996L, -1799L, 501L, 400L, 697L, -1600L)
        assertEquals(false, Ratenverlauf.wandert(gemessen, 500.0))
    }

    /** „Zu wenig gemessen" ist nicht „bleibt ruhig". */
    @Test
    fun `zu wenige fenster ergeben keine aussage`() {
        assertNull(Ratenverlauf.wandert(listOf(1L, 2L, 3L), 500.0))
        assertNull(Ratenverlauf.wandert(List(7) { 100L }, 500.0))
        assertNull(Ratenverlauf.wandert(emptyList(), 500.0))
        assertEquals(emptyList<Pair<Long, Long>>(),
            Ratenverlauf.jeFenster(listOf(1_000L), listOf(16_000L), 16_000))
    }


    // ---- Zeigerbefund ------------------------------------------------
    //
    // Kontrollfall mit Zielen, wie sie in /proc/self/fd wirklich stehen.

    @Test
    fun `die wichtigen arten werden auseinandergehalten`() {
        assertEquals("Rohr", Zeigerbefund.einteilen("pipe:[12345]"))
        assertEquals("Steckdose", Zeigerbefund.einteilen("socket:[67890]"))
        assertEquals("Ereigniszaehler".replace("ae", "ä"),
            Zeigerbefund.einteilen("anon_inode:[eventfd]"))
        assertEquals("Binder", Zeigerbefund.einteilen("/dev/binderfs/binder"))
        assertEquals("geteilter Speicher", Zeigerbefund.einteilen("/dev/ashmem/abc"))
    }

    /**
     * Gegenprobe zur Reihenfolge der Pruefungen: ein Ereigniszaehler
     * beginnt wie jedes andere namenlose Ziel. Stuende die allgemeine
     * Pruefung zuerst, verschwaende die aussagekraeftige Art im
     * Sammelbecken -- und genau die brauchen wir, um ein Leck zu deuten.
     */
    @Test
    fun `das genauere ziel gewinnt vor dem allgemeinen`() {
        assertEquals("namenlos [perf_event]",
            Zeigerbefund.einteilen("anon_inode:[perf_event]"))
        assertNotEquals(
            Zeigerbefund.einteilen("anon_inode:[eventfd]"),
            Zeigerbefund.einteilen("anon_inode:[perf_event]")
        )
    }

    /**
     * Ein Tausch gleicher Anzahl darf nicht wie „keine Aenderung"
     * aussehen. Zwanzig Rohre gegen zwanzig Steckdosen ist ein Befund.
     */
    @Test
    fun `ein tausch gleicher anzahl bleibt sichtbar`() {
        val vorher = mapOf("Rohr" to 20, "Steckdose" to 5)
        val nachher = mapOf("Rohr" to 0, "Steckdose" to 25)
        val d = Zeigerbefund.unterschied(vorher, nachher)
        assertEquals(20, d["Steckdose"])
        assertEquals(-20, d["Rohr"])
    }

    @Test
    fun `unveraenderte arten stehen nicht im unterschied`() {
        val gleich = mapOf("Rohr" to 3, "Binder" to 7)
        assertTrue(Zeigerbefund.unterschied(gleich, gleich).isEmpty())
    }


    // ---- Verlaufsurteil ----------------------------------------------
    //
    // Kontrollfaelle mit gebauten Reihen. Der Saegezahn schwingt jeweils
    // um 5000 auf und ab; entscheidend ist allein, wo sein Boden liegt.

    private fun saegezahn(boden: Long, anzahl: Int) =
        (0 until anzahl).map { boden + (it % 50) * 100L }

    /** Boden bleibt gleich: kein Leck, so hoch die Spitzen auch gehen. */
    @Test
    fun `ein saegezahn mit festem boden ist kein leck`() {
        val reihe = saegezahn(4000, 100) + saegezahn(4000, 100) + saegezahn(4000, 100)
        assertEquals(Verlaufsurteil.Art.RUHIG, Verlaufsurteil.beurteile(reihe).art)
    }

    /** Boden steigt gleichmaessig: genau das ist ein Leck. */
    @Test
    fun `ein gleichmaessig steigender boden gilt als leck`() {
        val reihe = saegezahn(4000, 100) + saegezahn(9000, 100) + saegezahn(14000, 100)
        assertEquals(Verlaufsurteil.Art.WAECHST_WEITER, Verlaufsurteil.beurteile(reihe).art)
    }

    /**
     * Der gemessene Fall: Boden 4427 -> 5143 -> 5391. Die Zuwaechse
     * schrumpfen von 716 auf 248. Das laeuft auf einen festen Stand zu und
     * darf **nicht** als Leck gemeldet werden -- die erste Fassung tat
     * genau das.
     */
    @Test
    fun `schrumpfende zuwaechse sind ein einschwingen, kein leck`() {
        val reihe = saegezahn(4427, 100) + saegezahn(5143, 100) + saegezahn(5391, 100)
        val befund = Verlaufsurteil.beurteile(reihe)
        assertEquals(Verlaufsurteil.Art.PENDELT_SICH_EIN, befund.art)
        assertEquals(listOf(4427L, 5143L, 5391L), befund.boeden)
    }

    /** Ein fallender Boden ist erst recht kein Leck. */
    @Test
    fun `ein fallender boden ist ruhig`() {
        val reihe = saegezahn(110868, 100) + saegezahn(105800, 100) + saegezahn(102244, 100)
        assertEquals(Verlaufsurteil.Art.RUHIG, Verlaufsurteil.beurteile(reihe).art)
    }

    /** „Zu wenig gemessen" ist nicht „ruhig". */
    @Test
    fun `zu wenige sitzungen ergeben kein urteil`() {
        assertEquals(Verlaufsurteil.Art.UNBEKANNT,
            Verlaufsurteil.beurteile(saegezahn(4000, 250)).art)
        assertEquals(Verlaufsurteil.Art.UNBEKANNT, Verlaufsurteil.beurteile(emptyList()).art)
    }


    // ---- Fehlerarten -------------------------------------------------
    //
    // Von Hand gerechnet an einem Satz mit je einem Wort aus jeder Klasse.

    private val klassen = Fehlerarten.klassenAus(
        eigennamen = listOf("Aslani"),
        fachbegriffe = listOf("audiotechnik"),
        zahlen = listOf("240")
    )

    @Test
    fun `jede klasse wird einzeln gezaehlt`() {
        val bezug = "herr aslani bestellt 240 teile bei audiotechnik"
        val erkannt = "herr aslani bestellt 240 teile bei audiotechnik"
        val b = Fehlerarten.beurteile(Wortvergleich.vergleiche(bezug, erkannt), bezug, klassen)
        val je = b.jeKlasse.associateBy { it.klasse }
        assertEquals(1.0, je.getValue(Fehlerarten.Klasse.EIGENNAME).quote!!, 0.001)
        assertEquals(1.0, je.getValue(Fehlerarten.Klasse.FACHBEGRIFF).quote!!, 0.001)
        assertEquals(1.0, je.getValue(Fehlerarten.Klasse.ZAHL).quote!!, 0.001)
        assertEquals(4, je.getValue(Fehlerarten.Klasse.NORMAL).bezugsworte)
    }

    /**
     * Der Fall, um den es beim Woerterbuch geht: der Eigenname faellt aus,
     * gewoehnliche Sprache bleibt richtig. Eine Gesamtfehlerrate von einem
     * Siebtel saehe harmlos aus -- die Aufteilung zeigt, dass die Klasse,
     * auf die es ankommt, bei null steht.
     */
    @Test
    fun `ein falscher eigenname senkt nur seine eigene quote`() {
        val bezug = "herr aslani bestellt 240 teile bei audiotechnik"
        val erkannt = "herr asland bestellt 240 teile bei audiotechnik"
        val b = Fehlerarten.beurteile(Wortvergleich.vergleiche(bezug, erkannt), bezug, klassen)
        val je = b.jeKlasse.associateBy { it.klasse }
        assertEquals(0.0, je.getValue(Fehlerarten.Klasse.EIGENNAME).quote!!, 0.001)
        assertEquals(1.0, je.getValue(Fehlerarten.Klasse.NORMAL).quote!!, 0.001)
        assertEquals(1.0, je.getValue(Fehlerarten.Klasse.ZAHL).quote!!, 0.001)
    }

    /** Kommt eine Klasse im Bezugstext nicht vor, gibt es keine Quote -- nicht null Prozent. */
    @Test
    fun `eine fehlende klasse hat keine quote`() {
        val bezug = "guten morgen"
        val b = Fehlerarten.beurteile(Wortvergleich.vergleiche(bezug, bezug), bezug, klassen)
        assertNull(b.jeKlasse.first { it.klasse == Fehlerarten.Klasse.EIGENNAME }.quote)
    }

    /**
     * Erfundene Woerter sind etwas anderes als verschobene. Ein
     * eingefuegtes Wort, das anderswo im Satz steht, ist meist eine
     * Verschiebung; eines, das im ganzen Bezugstext nicht vorkommt, hat der
     * Erkenner erfunden.
     */
    @Test
    fun `erfundene worte werden von verschobenen getrennt`() {
        val bezug = "guten morgen herr aslani"
        val mitErfindung = Fehlerarten.beurteile(
            Wortvergleich.vergleiche(bezug, "guten morgen herr aslani zitrone"), bezug, klassen)
        assertEquals(1, mitErfindung.erfunden)
        val mitWiederholung = Fehlerarten.beurteile(
            Wortvergleich.vergleiche(bezug, "guten guten morgen herr aslani"), bezug, klassen)
        assertEquals(0, mitWiederholung.erfunden)
    }

    /**
     * „240" gegen „zweihundertvierzig" ist kein Hoerfehler, sondern eine
     * andere Darstellung. Zusammen mit dem Rest gezaehlt wuerde ein
     * Erkenner bestraft, der richtig verstanden hat.
     */
    @Test
    fun `blosse schreibweise zaehlt eigens`() {
        assertTrue(Fehlerarten.schreibweiseGleich("240", "zweihundertvierzig"))
        assertTrue(Fehlerarten.schreibweiseGleich("zweihundertvierzig", "240"))
        // Gegenprobe: eine andere Zahl ist ein echter Fehler.
        assertFalse(Fehlerarten.schreibweiseGleich("240", "zweihundertvierzehn"))
        assertFalse(Fehlerarten.schreibweiseGleich("aslani", "asland"))
    }

    @Test
    fun `anfang und ende werden eigens gemeldet`() {
        val bezug = "guten morgen herr aslani"
        val ohneAnfang = Fehlerarten.beurteile(
            Wortvergleich.vergleiche(bezug, "morgen herr aslani"), bezug, klassen)
        assertFalse(ohneAnfang.satzanfangGetroffen)
        assertTrue(ohneAnfang.satzendeGetroffen)
        val ohneEnde = Fehlerarten.beurteile(
            Wortvergleich.vergleiche(bezug, "guten morgen herr"), bezug, klassen)
        assertTrue(ohneEnde.satzanfangGetroffen)
        assertFalse(ohneEnde.satzendeGetroffen)
    }


    // ---- Messspur: Negativtests gegen Pfadausbruch --------------------
    //
    // Die Absicht bestimmt nur eine Kennung. Keine dieser Eingaben darf zu
    // einem Dateinamen führen, der das vorgesehene Verzeichnis verlässt.

    @Test
    fun `keine eingabe fuehrt aus dem verzeichnis heraus`() {
        val boesartig = listOf(
            "../vorlauf.pcm",
            "../../../../data/data/de.ithandwerkstuttgart.nibra.forschung/databases/nibra.db",
            "/etc/passwd",
            "/sdcard/Download/fremd.pcm",
            "",
            "   ",
            "..",
            ".",
            "de/../../x",
            "de\\..\\x",
            "vorlauf.pcm\u0000/etc/passwd",
            "a".repeat(5000),
            "\n../x",
            "%2e%2e%2fx"
        )
        boesartig.forEach { eingabe ->
            val spur = Messspur.ausKennung(eingabe)
            assertEquals(
                "Eingabe \"${eingabe.take(30)}\" darf auf die Vorgabe fallen",
                Messspur.STANDARD, spur
            )
            assertFalse(
                "Der Dateiname darf keinen Pfadanteil haben: ${spur.dateiname}",
                spur.dateiname.contains('/') || spur.dateiname.contains('\\') ||
                    spur.dateiname.contains("..")
            )
        }
    }

    /** Gegenprobe: die vorgesehenen Kennungen müssen weiterhin greifen. */
    @Test
    fun `die vorgesehenen kennungen liefern ihre aufnahme`() {
        assertEquals("vorlauf.pcm", Messspur.ausKennung("de").dateiname)
        assertEquals("vorlauf-en.pcm", Messspur.ausKennung("en").dateiname)
        assertEquals("biasing.pcm", Messspur.ausKennung("vorgabe").dateiname)
        assertEquals("vergleich.wav", Messspur.ausKennung("vergleich").dateiname)
        assertEquals(Messspur.STANDARD, Messspur.ausKennung(null))
    }

    /** Kein Dateiname der Liste darf selbst einen Pfadanteil tragen. */
    @Test
    fun `alle hinterlegten dateinamen sind einfache namen`() {
        Messspur.entries.forEach { spur ->
            assertFalse(spur.dateiname.contains('/'))
            assertFalse(spur.dateiname.contains("\\"))
            assertFalse(spur.dateiname.startsWith("."))
        }
    }

}
