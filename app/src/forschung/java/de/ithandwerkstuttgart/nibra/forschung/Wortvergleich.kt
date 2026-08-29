package de.ithandwerkstuttgart.nibra.forschung

/**
 * Vergleicht ein Transkript mit dem gesprochenen Bezugstext -- Wort für Wort,
 * nicht nach Gefühl.
 *
 * „Klingt gleich" ist keine Messung. Diese Klasse richtet beide Wortfolgen
 * gegeneinander aus und zählt, was ersetzt, ausgelassen und eingefügt wurde.
 * Daraus ergibt sich die Wortfehlerrate und ein lesbarer Unterschied.
 *
 * **Wichtig zur Auslegung:** Die absolute Fehlerrate gegen den Bezugstext ist
 * durch Schreibweisen verzerrt, die kein Hörfehler sind (Ziffern, Satzzeichen,
 * „vierzehn Uhr dreißig" gegen „14:30"). Die Zahl, auf die es hier ankommt,
 * ist die **Differenz zwischen zwei Läufen** -- die trifft dieselbe Verzerrung
 * in beiden Läufen gleich und hebt sie damit auf.
 */
object Wortvergleich {

    enum class Art { GLEICH, ERSETZT, FEHLT, ZUSAETZLICH }

    data class Schritt(val art: Art, val bezug: String?, val erkannt: String?)

    data class Befund(
        val bezugsworte: Int,
        val erkannteWorte: Int,
        val gleich: Int,
        val ersetzt: Int,
        val fehlt: Int,
        val zusätzlich: Int,
        val schritte: List<Schritt>
    ) {
        /** Wortfehlerrate: (Ersetzungen + Auslassungen + Einfügungen) / Bezugsworte. */
        val fehlerrate: Double
            get() = if (bezugsworte == 0) 0.0
            else (ersetzt + fehlt + zusätzlich).toDouble() / bezugsworte

        /** Anteil der Bezugsworte, die wörtlich wiedergefunden wurden. */
        val trefferquote: Double
            get() = if (bezugsworte == 0) 0.0 else gleich.toDouble() / bezugsworte

        /** Nur die Abweichungen, in Lesereihenfolge. */
        fun unterschiede(): String = schritte
            .filter { it.art != Art.GLEICH }
            .joinToString("\n") { schritt ->
                when (schritt.art) {
                    Art.ERSETZT -> "  ~ ${schritt.bezug} -> ${schritt.erkannt}"
                    Art.FEHLT -> "  - ${schritt.bezug}"
                    Art.ZUSAETZLICH -> "  + ${schritt.erkannt}"
                    Art.GLEICH -> ""
                }
            }
            .ifBlank { "  (keine)" }
    }

    fun vergleiche(bezug: String, erkannt: String): Befund =
        baue(zerlege(bezug), zerlege(erkannt))

    private fun baue(a: List<String>, b: List<String>): Befund {
        val schritte = richteAus(a, b)
        return Befund(
            bezugsworte = a.size,
            erkannteWorte = b.size,
            gleich = schritte.count { it.art == Art.GLEICH },
            ersetzt = schritte.count { it.art == Art.ERSETZT },
            fehlt = schritte.count { it.art == Art.FEHLT },
            zusätzlich = schritte.count { it.art == Art.ZUSAETZLICH },
            schritte = schritte
        )
    }

    /**
     * Bringt Text auf eine Form, in der nur noch echte Hörunterschiede
     * übrig bleiben: Kleinschreibung, aufgelöste Umlaute, Ziffern als
     * Zahlwörter, keine Satzzeichen.
     *
     * Umlaute werden aufgelöst, weil Erkenner „Geräten" und „Geräten"
     * beide liefern können -- das ist eine Schreibweise, kein Hörfehler.
     */
    /**
     * Zerlegt **ohne** Zahlen zu vereinheitlichen.
     *
     * [zerlege] wandelt Ziffern in Zahlwörter, bevor verglichen wird -- gut
     * gemeint, aber es macht jede damit gerechnete Rate zu einer
     * **bereinigten**. Eine rohe Rate braucht diesen Weg hier: sie zeigt,
     * was der Erkenner wörtlich geschrieben hat, samt „14:30" statt
     * „vierzehn Uhr dreißig".
     *
     * Umlaute und Satzzeichen werden weiterhin eingeebnet. Das ist keine
     * Nachsicht gegenüber Hörfehlern, sondern Schreibweise desselben
     * Wortes -- „für" und „fuer" sind nicht zwei Erkennungen.
     */
    fun zerlegeRoh(text: String): List<String> = text
        .lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
        .replace("ß", "ss")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(" ")
        .filter { it.isNotBlank() }

    /** Vergleicht auf dem Wortlaut, ohne Zahlen zu vereinheitlichen. */
    fun vergleicheRoh(bezug: String, erkannt: String): Befund =
        baue(zerlegeRoh(bezug), zerlegeRoh(erkannt))

    fun zerlege(text: String): List<String> = text
        .lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
        .replace("ß", "ss")
        .replace(Regex("[0-9]+")) { treffer -> " " + zahlwort(treffer.value.toLong()) + " " }
        .replace(Regex("[^a-z ]"), " ")
        .split(" ")
        .filter { it.isNotBlank() }

    /**
     * Schreibt eine Zahl als deutsches Zahlwort -- ohne Leerzeichen, so wie
     * es gesprochen und geschrieben wird („zweihundertvierzig").
     *
     * Reicht bis unter eine Million. Größeres kommt in Diktaten dieser Art
     * nicht vor; käme es doch, steht die Ziffernfolge unverändert da und
     * fällt im Unterschied auf, statt still falsch zu werden.
     */
    fun zahlwort(zahl: Long): String {
        if (zahl < 0 || zahl >= 1_000_000) return zahl.toString()
        if (zahl == 0L) return "null"
        if (zahl < 1000) return unterTausend(zahl.toInt())
        val tausender = (zahl / 1000).toInt()
        val rest = (zahl % 1000).toInt()
        val vorn = if (tausender == 1) "ein" else unterTausend(tausender)
        return vorn + "tausend" + if (rest == 0) "" else unterTausend(rest)
    }

    private fun unterTausend(zahl: Int): String {
        if (zahl >= 100) {
            val hunderter = zahl / 100
            val rest = zahl % 100
            val vorn = if (hunderter == 1) "ein" else EINER[hunderter]
            return vorn + "hundert" + if (rest == 0) "" else unterTausend(rest)
        }
        if (zahl >= 20) {
            val zehner = ZEHNER[zahl / 10]
            val einer = zahl % 10
            // Deutsche Eigenheit: die Einer stehen vorn. 21 ist
            // „einundzwanzig", und 1 wird dabei zu „ein".
            return if (einer == 0) zehner
            else (if (einer == 1) "ein" else EINER[einer]) + "und" + zehner
        }
        if (zahl >= 10) return ZEHN_BIS_NEUNZEHN[zahl - 10]
        return EINER[zahl]
    }

    private val EINER = listOf(
        "null", "eins", "zwei", "drei", "vier",
        "fuenf", "sechs", "sieben", "acht", "neun"
    )
    private val ZEHN_BIS_NEUNZEHN = listOf(
        "zehn", "elf", "zwoelf", "dreizehn", "vierzehn",
        "fuenfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn"
    )
    private val ZEHNER = listOf(
        "", "zehn", "zwanzig", "dreissig", "vierzig",
        "fuenfzig", "sechzig", "siebzig", "achtzig", "neunzig"
    )

    /**
     * Levenshtein mit Rückverfolgung: die günstigste Folge von
     * Uebereinstimmungen, Ersetzungen, Auslassungen und Einfügungen.
     *
     * Ohne Ausrichtung wäre ein einziges verschlucktes Wort am Anfang ein
     * Totalausfall des ganzen Vergleichs -- alles danach wäre verschoben.
     */
    private fun richteAus(bezug: List<String>, erkannt: List<String>): List<Schritt> {
        val n = bezug.size
        val m = erkannt.size
        val kosten = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) kosten[i][0] = i
        for (j in 0..m) kosten[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val gleich = bezug[i - 1] == erkannt[j - 1]
                kosten[i][j] = minOf(
                    kosten[i - 1][j - 1] + if (gleich) 0 else 1,
                    kosten[i - 1][j] + 1,
                    kosten[i][j - 1] + 1
                )
            }
        }

        val schritte = ArrayDeque<Schritt>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            val gleich = i > 0 && j > 0 && bezug[i - 1] == erkannt[j - 1]
            when {
                i > 0 && j > 0 &&
                    kosten[i][j] == kosten[i - 1][j - 1] + if (gleich) 0 else 1 -> {
                    schritte.addFirst(
                        Schritt(
                            if (gleich) Art.GLEICH else Art.ERSETZT,
                            bezug[i - 1],
                            erkannt[j - 1]
                        )
                    )
                    i--; j--
                }
                i > 0 && kosten[i][j] == kosten[i - 1][j] + 1 -> {
                    schritte.addFirst(Schritt(Art.FEHLT, bezug[i - 1], null))
                    i--
                }
                else -> {
                    schritte.addFirst(Schritt(Art.ZUSAETZLICH, null, erkannt[j - 1]))
                    j--
                }
            }
        }
        return schritte.toList()
    }
}
