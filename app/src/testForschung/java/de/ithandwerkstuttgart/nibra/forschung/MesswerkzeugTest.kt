package de.ithandwerkstuttgart.nibra.forschung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

    /**
     * Ein Sägezahn um einen Boden.
     *
     * **Die Periode teilt das Fenster.** Beim ersten Wurf war sie 50 und
     * das Fenster 30; damit wandert die Phase, der Boden je Fenster liegt
     * mal früher mal später im Zahn, und die Reihe rauscht um Tausende --
     * die Prüfung maß dann das Rauschen statt die Steigung.
     *
     * @param steigung wie viel der Boden je Sitzung zulegt.
     */
    private fun sägezahn(boden: Long, anzahl: Int, steigung: Double = 0.0, periode: Int = 30) =
        (0 until anzahl).map { boden + (it * steigung).toLong() + (it % periode) * 100L }

    /**
     * Eine Reihe, deren Boden je Fenster **genau** vorgegeben ist.
     * Damit lassen sich gemessene Böden unverändert nachspielen.
     */
    private fun mitBoeden(boeden: List<Long>, fenster: Int = 30) =
        boeden.flatMap { boden -> (0 until fenster).map { boden + (it % fenster) * 10L } }

    /** Boden bleibt gleich: kein Leck, so hoch die Spitzen auch gehen. */
    @Test
    fun `ein saegezahn mit festem boden ist kein leck`() {
        assertEquals(Verlaufsurteil.Art.RUHIG,
            Verlaufsurteil.beurteile(sägezahn(4000, 300)).art)
    }

    /** Boden steigt gleichmaessig: genau das ist ein Leck. */
    @Test
    fun `ein gleichmaessig steigender boden gilt als leck`() {
        val befund = Verlaufsurteil.beurteile(sägezahn(4000, 300, steigung = 33.0))
        assertEquals(Verlaufsurteil.Art.WAECHST_WEITER, befund.art)
        assertEquals(33.0, befund.steigung, 1.0)
    }

    /**
     * Einpendeln heißt, dass die Zuwächse gegen null gehen -- nicht, dass
     * ein Schritt kleiner ausfällt als der davor.
     */
    @Test
    fun `abflachende zuwaechse sind ein einschwingen, kein leck`() {
        // Wurzelförmig: steigt anfangs deutlich, wird immer flacher.
        val reihe = (0 until 300).map {
            4400L + (900 * kotlin.math.sqrt(it / 300.0)).toLong() + (it % 30) * 10L
        }
        assertEquals(Verlaufsurteil.Art.PENDELT_SICH_EIN, Verlaufsurteil.beurteile(reihe).art)
    }

    /**
     * **Der Fall, an dem die alte Fassung scheiterte -- mit den echten
     * Zahlen des Laufs vom 29.08.**
     *
     * Über 900 Sitzungen las das alte Urteil drei Böden der Java-Halde,
     * 4788 -> 5335 -> 5601, verglich die Zuwächse 547 und 266 und sprach
     * „pendelt sich ein". Dieselbe Messung über dreißig Fenster steigt
     * durchgehend bis 6671.
     *
     * Drei Stützpunkte können das nicht sehen: fällt der erste Zuwachs
     * zufällig größer aus, geht ein Leck als gesund durch. Genau das ist
     * hier passiert.
     */
    @Test
    fun `gegenprobe -- der fall, den drei stuetzpunkte falsch lasen`() {
        // Die tatsächlich gemessenen Böden, dreißig Fenster zu je dreißig
        // Sitzungen, aus messungen/sitzungen900-emu.txt.
        val gemessen = listOf(
            4788L, 5028L, 5145L, 5052L, 5217L, 5489L, 5143L, 5382L, 5577L, 5199L,
            5744L, 5484L, 5363L, 5640L, 6514L, 5335L, 5606L, 5547L, 6782L, 5769L,
            5601L, 5807L, 6594L, 5693L, 5944L, 6115L, 6388L, 6294L, 6469L, 6671L
        )
        val befund = Verlaufsurteil.beurteile(mitBoeden(gemessen), 30)
        assertEquals("Über dreißig Fenster ist das ein Leck",
            Verlaufsurteil.Art.WAECHST_WEITER, befund.art)
        assertTrue("Die Steigung muss die Schwelle deutlich reißen: ${befund.sicherheit}",
            befund.sicherheit >= 4.0)

        // Und so las es die alte Fassung: drei Böden aus denselben Daten.
        val grob = listOf(4788L, 5335L, 5601L)
        val zuwächse = grob.zipWithNext { a, b -> b - a }
        assertTrue(
            "Die alte Regel hätte hier „pendelt sich ein\" gesagt: Zuwächse $zuwächse",
            zuwächse.last() <= zuwächse.first() / 2
        )
    }

    /** Ein fallender Boden ist erst recht kein Leck. */
    @Test
    fun `ein fallender boden ist ruhig`() {
        assertEquals(Verlaufsurteil.Art.RUHIG,
            Verlaufsurteil.beurteile(sägezahn(110868, 300, steigung = -29.0)).art)
    }

    /**
     * „Zu wenig gemessen" ist nicht „ruhig". Acht Fenster sind das
     * Mindeste; darunter gibt es kein Urteil.
     */
    @Test
    fun `zu wenige sitzungen ergeben kein urteil`() {
        assertEquals(Verlaufsurteil.Art.UNBEKANNT,
            Verlaufsurteil.beurteile(sägezahn(4000, 200), fenster = 30).art)
        assertEquals(Verlaufsurteil.Art.UNBEKANNT, Verlaufsurteil.beurteile(emptyList()).art)
    }

    /**
     * Rauschen ohne Anstieg darf kein Leck werden.
     *
     * Der Boden je Fenster ist das Kleinste aus dreißig Werten und
     * schwankt deshalb viel weniger als die Reihe selbst. Das ist der
     * Grund, warum diese Prüfung überhaupt kleine Anstiege findet -- und
     * warum sie bei **keinem** Anstieg trotzdem still bleiben muss.
     */
    @Test
    fun `rauschen ohne anstieg ist kein leck`() {
        val zufall = java.util.Random(20260829)
        val reihe = (0 until 300).map { 4000L + zufall.nextInt(400).toLong() }
        assertEquals(Verlaufsurteil.Art.RUHIG, Verlaufsurteil.beurteile(reihe).art)
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


    // ---- Guetemasse: Kontrollfaelle -----------------------------------
    //
    // Von Hand gerechnet. Bezugssatz mit sechs Woertern.

    private val sechs = "die lieferung kommt am dritten oktober"

    @Test
    fun `gleicher text ergibt ueberall null`() {
        val b = Guetemasse.beurteile(sechs, sechs)
        assertEquals(0.0, b.roheWortfehlerrate, 0.001)
        assertEquals(0.0, b.bereinigteWortfehlerrate, 0.001)
        assertEquals(0.0, b.zeichenfehlerrate, 0.001)
        assertEquals(0, b.verlustAmAnfang)
        assertEquals(0, b.verlustAmEnde)
    }

    /**
     * Der Kern der Unterscheidung: „240" gegen „zweihundertvierzig" ist ein
     * **roher** Fehler und **kein** bereinigter. Faellt der Unterschied
     * weg, vermischt die Auswertung Schreibweise mit Hoerfehlern -- und ein
     * Erkenner, der richtig verstanden hat, saehe schlechter aus.
     */
    @Test
    fun `schreibweise faellt nur bei der bereinigten rate weg`() {
        val bezug = "wir liefern zweihundertvierzig teile"
        val erkannt = "wir liefern 240 teile"
        val b = Guetemasse.beurteile(bezug, erkannt)
        assertEquals("ein Wort von vieren", 0.25, b.roheWortfehlerrate, 0.001)
        assertEquals("nach dem Vereinheitlichen keiner", 0.0, b.bereinigteWortfehlerrate, 0.001)
    }

    /** Gegenprobe: eine **andere** Zahl bleibt in beiden Raten ein Fehler. */
    @Test
    fun `eine falsche zahl bleibt auch bereinigt ein fehler`() {
        val b = Guetemasse.beurteile("wir liefern zweihundertvierzig teile",
                                     "wir liefern zweihundertvierzehn teile")
        assertEquals(0.25, b.roheWortfehlerrate, 0.001)
        assertEquals(0.25, b.bereinigteWortfehlerrate, 0.001)
    }

    /**
     * Zeichenfehlerrate: „oktober" gegen „oktoba" sind zwei Ersetzungen bei
     * 37 Zeichen Bezug. Von Hand: 2/37 = 0,054.
     */
    @Test
    fun `die zeichenfehlerrate faengt kleine verhoerer`() {
        val b = Guetemasse.beurteile(sechs, "die lieferung kommt am dritten oktoba")
        assertEquals(2.0 / 37.0, b.zeichenfehlerrate, 0.005)
        // Auf Wortebene ist es ein ganzer Fehler von sechs.
        assertEquals(1.0 / 6.0, b.roheWortfehlerrate, 0.001)
    }

    /** Satzzeichen im Bezug duerfen die Zeichenrate nicht aufblaehen. */
    @Test
    fun `satzzeichen zaehlen bei der zeichenrate nicht mit`() {
        val mit = Guetemasse.zeichenfehlerrate("Die Lieferung, kommt!", "die lieferung kommt")
        assertEquals(0.0, mit, 0.001)
    }

    @Test
    fun `auslassung und einfuegung werden getrennt gezaehlt`() {
        val fehlt = Guetemasse.beurteile(sechs, "die lieferung kommt am oktober")
        assertEquals(1.0 / 6.0, fehlt.auslassungsrate, 0.001)
        assertEquals(0.0, fehlt.einfuegungsrate, 0.001)
        val zuviel = Guetemasse.beurteile(sechs, "die lieferung kommt am dritten oktober bitte")
        assertEquals(0.0, zuviel.auslassungsrate, 0.001)
        assertEquals(1.0 / 6.0, zuviel.einfuegungsrate, 0.001)
    }

    /**
     * Verlust am Anfang und am Ende: **wie viele** Woerter fehlen, nicht
     * ob eines fehlt. Eines am Anfang ist aergerlich, fuenf sind ein
     * anderes Problem.
     */
    @Test
    fun `verlust an den raendern wird gezaehlt, nicht nur gemeldet`() {
        val vorn = Guetemasse.beurteile(sechs, "kommt am dritten oktober")
        assertEquals(2, vorn.verlustAmAnfang)
        assertEquals(0, vorn.verlustAmEnde)
        val hinten = Guetemasse.beurteile(sechs, "die lieferung kommt am")
        assertEquals(0, hinten.verlustAmAnfang)
        assertEquals(2, hinten.verlustAmEnde)
    }

    /**
     * Gegenprobe: ein **erfundenes** erstes Wort ist kein Verlust am
     * Anfang. Sonst wuerde eine Halluzination als fehlender Satzanfang
     * gezaehlt -- zwei verschiedene Fehler in einer Zahl.
     */
    @Test
    fun `ein erfundenes erstes wort ist kein verlust am anfang`() {
        val b = Guetemasse.beurteile(sechs, "zitrone die lieferung kommt am dritten oktober")
        assertEquals(0, b.verlustAmAnfang)
    }


    // ---- Testfall-Abgleich --------------------------------------------
    //
    // Der Riegel, der verhindert, dass Anzeige und Auswertung wieder
    // auseinanderlaufen. Ein Riegel, von dem niemand weiss, ob er
    // zuschlaegt, ist kein Riegel.

    private val a = Testfall("A", "Guten Morgen.", Testfall.Kategorie.EINFACH)
    private val b = Testfall("B", "Ganz anderer Satz.", Testfall.Kategorie.EINFACH)

    @Test
    fun `derselbe testfall geht durch`() {
        assertNull(Testfall.abgleich(a.id, a.text, a))
    }

    @Test
    fun `eine andere kennung schlaegt an`() {
        assertNotNull(Testfall.abgleich(b.id, b.text, a))
    }

    /**
     * Der heimtueckische Fall: gleiche Kennung, anderer Text. Genau so
     * liefe eine zweite Kopie des Korpus auseinander -- die Kennung passt,
     * der Inhalt nicht.
     */
    @Test
    fun `gleiche kennung mit anderem text schlaegt an`() {
        assertNotNull(Testfall.abgleich("A", "Guten Abend.", a))
    }

    @Test
    fun `keine anzeige schlaegt an`() {
        assertNotNull(Testfall.abgleich(null, null, a))
    }

    @Test
    fun `verschiedene texte haben verschiedene abdruecke`() {
        assertNotEquals(a.abdruck, b.abdruck)
        assertEquals(a.abdruck, Testfall("X", a.text, Testfall.Kategorie.ZAHL).abdruck)
    }

    /** Jede Kennung im Korpus darf es nur einmal geben. */
    @Test
    fun `die kennungen im korpus sind eindeutig`() {
        listOf(Testfall.PILOT, Testfall.VOLL).forEach { satz ->
            assertEquals(satz.size, satz.map { it.id }.toSet().size)
            assertEquals(satz.size, satz.map { it.abdruck }.toSet().size)
        }
    }


    // ---- Lebensdauer ------------------------------------------------
    //
    // Die Zahl gleichzeitig lebender Objekte beantwortet die Frage nicht.
    // Hundert Lebende, von denen keiner älter als vierzig Sitzungen ist,
    // sind verzögerte Freigabe. Hundert Lebende, darunter einer aus
    // Sitzung 5, sind etwas völlig anderes -- bei gleicher Zahl.

    @Test
    fun `die verteilung rechnet alter, median und perzentil`() {
        val v = Lebensdauer.verteilung("Erkenner", listOf(1, 5, 12, 30, 60, 120, 250))
        assertEquals(7, v.lebende)
        assertEquals(250, v.aeltester)
        assertEquals(30, v.median)
        assertEquals(250, v.perzentil95)
        // 12, 30, 60, 120, 250 sind grösser als 10 -- fünf Werte
        assertEquals(5, v.aelterAls[10])
        assertEquals(4, v.aelterAls[25])
        assertEquals(3, v.aelterAls[50])
        assertEquals(2, v.aelterAls[100])
        assertEquals(1, v.aelterAls[200])
    }

    /**
     * Aus fünf Zahlen lässt sich kein 95er-Perzentil schätzen, das mehr
     * wüsste als das Maximum. Es soll deshalb das Maximum liefern und
     * nicht so tun, als wüsste es mehr.
     */
    @Test
    fun `das perzentil erfindet bei wenigen werten nichts`() {
        val v = Lebensdauer.verteilung("Erkenner", listOf(3, 4, 5))
        assertEquals(5, v.perzentil95)
        assertEquals(5, v.aeltester)
    }

    @Test
    fun `ohne ueberlebende ist alles null`() {
        val v = Lebensdauer.verteilung("Erkenner", emptyList())
        assertEquals(0, v.lebende)
        assertEquals(0, v.aeltester)
        assertEquals(0, v.aelterAls[10])
    }

    /**
     * **Der Fall, den wir fürchten.** Das Alter des ältesten Überlebenden
     * wächst im Takt der Sitzungen -- dasselbe Objekt lebt die ganze Zeit
     * mit -- und der Bestand wächst mit.
     */
    @Test
    fun `mitwachsendes alter und mitwachsender bestand heissen unbegrenztes leck`() {
        val alter = listOf(20, 60, 120, 240, 480, 700)
        val bestand = listOf(15, 40, 80, 150, 300, 500)
        assertEquals(Lebensdauer.Einordnung.UNBEGRENZTES_LECK,
            Lebensdauer.ordneEin(alter, bestand))
    }

    /**
     * **Der harmlose Fall mit denselben Bestandszahlen am Ende.** Der
     * Bestand steht, das Alter auch: es staut sich verzögert, aber
     * begrenzt. Ohne die Altersmessung sähe das genauso aus wie ein Leck.
     */
    @Test
    fun `stehendes alter bei stehendem bestand heisst begrenzter rueckstau`() {
        val alter = listOf(35, 41, 38, 40, 37, 42)
        val bestand = listOf(95, 102, 98, 101, 99, 100)
        assertEquals(Lebensdauer.Einordnung.BEGRENZTER_RUECKSTAU,
            Lebensdauer.ordneEin(alter, bestand))
    }

    @Test
    fun `zu wenige haltepunkte ergeben keine einordnung`() {
        assertEquals(Lebensdauer.Einordnung.UNGEKLAERT,
            Lebensdauer.ordneEin(listOf(10, 20, 30), listOf(1, 2, 3)))
        assertEquals(Lebensdauer.Einordnung.UNGEKLAERT,
            Lebensdauer.ordneEin(listOf(10, 20, 30, 40), listOf(1, 2)))
    }

    /**
     * Ein Zusammenhang ist keine Ursache. Die Zahl sagt nur, ob es sich
     * lohnt, dort weiterzusuchen -- deshalb muss sie wenigstens stimmen.
     */
    @Test
    fun `der zusammenhang erkennt gleichlauf und gegenlauf`() {
        val gleich = Lebensdauer.zusammenhang(
            listOf(1.0, 2.0, 3.0, 4.0), listOf(10.0, 20.0, 30.0, 40.0))!!
        assertEquals(1.0, gleich, 0.001)
        val gegen = Lebensdauer.zusammenhang(
            listOf(1.0, 2.0, 3.0, 4.0), listOf(40.0, 30.0, 20.0, 10.0))!!
        assertEquals(-1.0, gegen, 0.001)
        assertNull("Eine gleichbleibende Reihe hat keinen Zusammenhang",
            Lebensdauer.zusammenhang(listOf(1.0, 2.0, 3.0), listOf(5.0, 5.0, 5.0)))
        assertNull("Zu wenige Punkte",
            Lebensdauer.zusammenhang(listOf(1.0, 2.0), listOf(3.0, 4.0)))
    }

    /**
     * Schwache Referenzen halten nichts fest -- sonst würde die Messung
     * genau das erzeugen, was sie sucht.
     */
    @Test
    fun `zeugen halten ihre objekte nicht fest`() {
        var gegenstand: Any? = Any()
        val zeugen = listOf(
            Lebensdauer.Zeuge(1, "Ding", 1L, java.lang.ref.WeakReference(gegenstand!!))
        )
        assertEquals(1, Lebensdauer.verteilungen(zeugen, jetzt = 10).size)
        gegenstand = null
        Runtime.getRuntime().gc()
        Runtime.getRuntime().runFinalization()
        Runtime.getRuntime().gc()
        assertTrue("Nach der Bereinigung darf kein Zeuge mehr leben",
            Lebensdauer.verteilungen(zeugen, jetzt = 10).isEmpty())
    }
}
