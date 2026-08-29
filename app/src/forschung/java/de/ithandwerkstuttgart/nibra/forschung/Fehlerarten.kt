package de.ithandwerkstuttgart.nibra.forschung

/**
 * Trennt Erkennungsfehler nach **Art**, nicht nur nach Anzahl.
 *
 * Eine Gesamtfehlerrate beantwortet die Produktfrage nicht. Zwei Läufe mit
 * je 10 % Fehlern können völlig verschieden sein: einmal fehlen zehn
 * Füllwörter, einmal ist jeder Eigenname falsch. Für den Nutzer ist das
 * erste ein Achselzucken und das zweite ein unbrauchbarer Text.
 *
 * Deshalb wird jedes Bezugswort einer Klasse zugeordnet und die Trefferquote
 * je Klasse ausgewiesen. Erst damit lässt sich die Frage beantworten, die
 * über die Vorgabeliste entscheidet: **werden Eigennamen besser, ohne dass
 * gewöhnliche Sprache schlechter wird?**
 */
object Fehlerarten {

    enum class Klasse {
        /** Gewöhnliche Wörter. */
        NORMAL,

        /** Personennamen. */
        EIGENNAME,

        /** Firmen und Fachbegriffe. */
        FACHBEGRIFF,

        /** Zahlen, Beträge, Uhrzeiten. */
        ZAHL
    }

    data class Klassenbefund(
        val klasse: Klasse,
        val bezugsworte: Int,
        val getroffen: Int
    ) {
        /** `null` statt 0, wenn die Klasse im Bezugstext gar nicht vorkommt. */
        val quote: Double?
            get() = if (bezugsworte == 0) null else getroffen.toDouble() / bezugsworte
    }

    data class Befund(
        val jeKlasse: List<Klassenbefund>,
        val fehlend: Int,
        val zusaetzlich: Int,
        val ersetzt: Int,
        /**
         * Zusätzliche Wörter, die im Bezugstext **nirgends** vorkommen.
         *
         * Ein eingefügtes Wort, das anderswo im Satz steht, ist meist eine
         * Verschiebung in der Ausrichtung. Eines, das im ganzen Bezugstext
         * nicht vorkommt, hat der Erkenner erfunden -- und das ist die
         * Sorte Fehler, die einen Text unbrauchbar macht, weil sie sich
         * nicht als Fehler zu erkennen gibt.
         */
        val erfunden: Int,
        /**
         * Ersetzungen, die nur die Schreibweise betreffen.
         *
         * „14:30" gegen „vierzehn Uhr dreißig" ist kein Hörfehler, sondern
         * eine andere Darstellung desselben. Zusammen mit dem Rest gezählt
         * würde ein Erkenner bestraft, der **richtig** verstanden hat.
         */
        val nurSchreibweise: Int,
        val satzanfangGetroffen: Boolean,
        val satzendeGetroffen: Boolean
    )

    /**
     * @param klassen Zuordnung Wort (klein geschrieben) zu Klasse. Was nicht
     *        darin steht, gilt als [Klasse.NORMAL].
     */
    fun beurteile(
        befund: Wortvergleich.Befund,
        bezug: String,
        klassen: Map<String, Klasse>
    ): Befund {
        val bezugsworte = Wortvergleich.zerlege(bezug)
        val bezugsmenge = bezugsworte.toSet()

        val zaehler = Klasse.entries.associateWith { intArrayOf(0, 0) }
        befund.schritte.forEach { schritt ->
            val wort = schritt.bezug ?: return@forEach
            val klasse = klassen[wort] ?: Klasse.NORMAL
            zaehler.getValue(klasse)[0] += 1
            if (schritt.art == Wortvergleich.Art.GLEICH) zaehler.getValue(klasse)[1] += 1
        }

        val erfunden = befund.schritte.count {
            it.art == Wortvergleich.Art.ZUSAETZLICH && it.erkannt !in bezugsmenge
        }
        val nurSchreibweise = befund.schritte.count {
            it.art == Wortvergleich.Art.ERSETZT &&
                schreibweiseGleich(it.bezug.orEmpty(), it.erkannt.orEmpty())
        }

        // Anfang und Ende sind die Stellen, an denen ein Fehler am meisten
        // stört: der Anfang, weil er den Satz eröffnet, das Ende, weil dort
        // der Erkenner abbricht.
        val erste = bezugsworte.firstOrNull()
        val letzte = bezugsworte.lastOrNull()
        val getroffeneBezuege = befund.schritte
            .filter { it.art == Wortvergleich.Art.GLEICH }
            .mapNotNull { it.bezug }

        return Befund(
            jeKlasse = Klasse.entries.map { k ->
                Klassenbefund(k, zaehler.getValue(k)[0], zaehler.getValue(k)[1])
            },
            fehlend = befund.fehlt,
            zusaetzlich = befund.zusätzlich,
            ersetzt = befund.ersetzt,
            erfunden = erfunden,
            nurSchreibweise = nurSchreibweise,
            satzanfangGetroffen = erste != null && getroffeneBezuege.firstOrNull() == erste,
            satzendeGetroffen = letzte != null && getroffeneBezuege.lastOrNull() == letzte
        )
    }

    /**
     * Ob zwei Wörter dasselbe meinen und sich nur in der Schreibweise
     * unterscheiden -- also eine Zahl gegen ihr Zahlwort.
     *
     * Bewusst eng: nur Ziffern gegen ausgeschriebene Zahl, in beide
     * Richtungen. Alles Weitere wäre Auslegung, und eine großzügige
     * Auslegung rechnet echte Fehler weg.
     */
    fun schreibweiseGleich(a: String, b: String): Boolean {
        if (a == b) return true
        val zahlA = a.filter { it.isDigit() }.toLongOrNull()
        val zahlB = b.filter { it.isDigit() }.toLongOrNull()
        return when {
            zahlA != null && a.all { it.isDigit() } ->
                Wortvergleich.zahlwort(zahlA).replace(" ", "") == b.replace(" ", "")
            zahlB != null && b.all { it.isDigit() } ->
                Wortvergleich.zahlwort(zahlB).replace(" ", "") == a.replace(" ", "")
            else -> false
        }
    }

    /** Baut die Zuordnung aus Wortlisten je Klasse. */
    fun klassenAus(
        eigennamen: List<String> = emptyList(),
        fachbegriffe: List<String> = emptyList(),
        zahlen: List<String> = emptyList()
    ): Map<String, Klasse> = buildMap {
        eigennamen.flatMap { Wortvergleich.zerlege(it) }.forEach { put(it, Klasse.EIGENNAME) }
        fachbegriffe.flatMap { Wortvergleich.zerlege(it) }.forEach { put(it, Klasse.FACHBEGRIFF) }
        zahlen.flatMap { Wortvergleich.zerlege(it) }.forEach { put(it, Klasse.ZAHL) }
    }
}
