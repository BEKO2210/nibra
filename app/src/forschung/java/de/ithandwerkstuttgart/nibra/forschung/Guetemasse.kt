package de.ithandwerkstuttgart.nibra.forschung

import kotlin.math.min

/**
 * Die Masse, an denen die Erkennung gemessen wird.
 *
 * Eine einzige Wortfehlerrate reicht nicht. Sie vermischt drei Dinge, die
 * für den Nutzer völlig verschieden sind: ein **verhörtes** Wort, ein
 * **fehlendes** Wort und ein **anders geschriebenes** Wort. Wer „14:30"
 * statt „vierzehn Uhr dreißig" schreibt, hat richtig verstanden; wer
 * „vierzig" statt „vierzehn" schreibt, nicht. Dieselbe Zahl für beides ist
 * eine Auskunft, auf die sich keine Entscheidung stützen lässt.
 */
object Guetemasse {

    data class Befund(
        /** Wortfehlerrate auf dem Wortlaut, ohne jede Nachsicht. */
        val roheWortfehlerrate: Double,
        /**
         * Dieselbe Rate, nachdem Zahlen und Zahlwörter auf eine Form
         * gebracht wurden. Der Unterschied zur rohen Rate ist genau das,
         * was **nur** Schreibweise war.
         */
        val bereinigteWortfehlerrate: Double,
        /** Zeichenfehlerrate -- feiner als Wörter, fängt kleine Verhörer. */
        val zeichenfehlerrate: Double,
        val auslassungsrate: Double,
        val einfuegungsrate: Double,
        /** Wie viele Wörter am **Anfang** fehlen, bis das erste passt. */
        val verlustAmAnfang: Int,
        /** Wie viele Wörter am **Ende** fehlen. */
        val verlustAmEnde: Int
    )

    fun beurteile(bezug: String, erkannt: String): Befund {
        // **Roh heisst wirklich roh.** Wortvergleich.zerlege wandelt
        // Ziffern in Zahlwörter, bevor verglichen wird; jede damit
        // gerechnete Rate ist bereits eine bereinigte. Für die rohe Rate
        // gibt es deshalb einen eigenen Weg.
        val roh = Wortvergleich.vergleicheRoh(bezug, erkannt)
        val bereinigt = Wortvergleich.vergleiche(bezug, erkannt)
        val bezugsworte = Wortvergleich.zerlege(bezug)
        val erkannteWorte = Wortvergleich.zerlege(erkannt)

        return Befund(
            roheWortfehlerrate = roh.fehlerrate,
            bereinigteWortfehlerrate = bereinigt.fehlerrate,
            zeichenfehlerrate = zeichenfehlerrate(bezug, erkannt),
            auslassungsrate = anteil(roh.fehlt, roh.bezugsworte),
            einfuegungsrate = anteil(roh.zusätzlich, roh.bezugsworte),
            verlustAmAnfang = verlustVorn(bezugsworte, erkannteWorte),
            verlustAmEnde = verlustVorn(bezugsworte.reversed(), erkannteWorte.reversed())
        )
    }

    /**
     * Bringt Zahlwörter und Ziffern auf eine Form.
     *
     * **Bewusst eng.** Nur Ziffernfolgen werden zu ihrem Zahlwort. Alles
     * Weitere wäre Auslegung, und eine großzügige Auslegung rechnet echte
     * Hörfehler weg -- genau das, was diese Kennzahl aufdecken soll.
     */
    @Deprecated("Wortvergleich.zerlege vereinheitlicht bereits selbst.")
    fun vereinheitliche(text: String): String =
        Wortvergleich.zerlege(text).joinToString(" ") { wort ->
            val zahl = wort.toLongOrNull()
            if (zahl != null && wort.all { it.isDigit() }) {
                Wortvergleich.zahlwort(zahl).replace(" ", "")
            } else {
                wort
            }
        }

    /**
     * Zeichenfehlerrate über die zerlegten Wörter, mit einem Leerzeichen
     * verbunden.
     *
     * Auf dem rohen Text gerechnet zählte jedes Satzzeichen mit -- und ein
     * Erkenner, der keine Kommas setzt, sähe schlechter aus, als er hört.
     */
    fun zeichenfehlerrate(bezug: String, erkannt: String): Double {
        val a = Wortvergleich.zerlegeRoh(bezug).joinToString(" ")
        val b = Wortvergleich.zerlegeRoh(erkannt).joinToString(" ")
        if (a.isEmpty()) return if (b.isEmpty()) 0.0 else 1.0
        return abstand(a, b).toDouble() / a.length
    }

    /**
     * Wie viele Wörter vom Anfang der Bezugsliste fehlen, bis das erste
     * wieder auftaucht.
     *
     * Nicht „ist das erste Wort da" -- die Zahl sagt, **wie viel** fehlt.
     * Ein fehlendes Wort am Anfang ist ärgerlich, fünf sind ein anderes
     * Problem.
     */
    fun verlustVorn(bezug: List<String>, erkannt: List<String>): Int {
        if (bezug.isEmpty() || erkannt.isEmpty()) return bezug.size
        val ersteErkannt = erkannt.first()
        val stelle = bezug.indexOf(ersteErkannt)
        // Taucht das erste erkannte Wort gar nicht im Bezug auf, ist es
        // erfunden und sagt nichts über einen Verlust am Anfang.
        return if (stelle < 0) 0 else stelle
    }

    private fun anteil(zahl: Int, von: Int): Double =
        if (von == 0) 0.0 else zahl.toDouble() / von

    /** Levenshtein-Abstand, zeilenweise gerechnet. */
    private fun abstand(a: String, b: String): Int {
        var vorige = IntArray(b.length + 1) { it }
        var jetzige = IntArray(b.length + 1)
        for (i in 1..a.length) {
            jetzige[0] = i
            for (j in 1..b.length) {
                val kosten = if (a[i - 1] == b[j - 1]) 0 else 1
                jetzige[j] = min(
                    min(jetzige[j - 1] + 1, vorige[j] + 1),
                    vorige[j - 1] + kosten
                )
            }
            val tausch = vorige; vorige = jetzige; jetzige = tausch
        }
        return vorige[b.length]
    }
}
