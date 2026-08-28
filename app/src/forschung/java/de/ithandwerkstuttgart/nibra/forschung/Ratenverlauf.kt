package de.ithandwerkstuttgart.nibra.forschung

import kotlin.math.abs

/**
 * Wertet aus, wie sich die Ankunftsrate über einen Lauf verhält.
 *
 * **Was hier gemessen wird, ist die effektive Rate -- nicht ihre
 * Ursache.** Kommen weniger Abtastwerte an, als die Nennrate und unsere
 * Uhr erwarten lassen, kann das am Audiotakt der Hardware liegen, am
 * Treiber, an einer Umtastung darin, an der Einteilung der Rechenzeit oder
 * an unserer eigenen Uhr. Diese Klasse entscheidet davon nichts. Sie sagt
 * nur, **wie groß** die Abweichung ist und **ob sie ruhig bleibt**.
 *
 * Und das ist die Frage, die für Nibra zählt. Ein gleichmäßiger kleiner
 * Versatz ist harmlos: er verschiebt nichts, solange kein Block verworfen
 * wird und der Verbraucher alles bekommt. Eine wandernde oder springende
 * Abweichung ist etwas anderes -- sie deutet darauf, dass sich unter uns
 * etwas ändert. Am Endwert allein sind die beiden nicht zu unterscheiden:
 * dort steht in beiden Fällen dieselbe Zahl.
 */
object Ratenverlauf {

    /**
     * Abweichung je Teilfenster in Teilen je Million, aus aufeinander
     * folgenden Stichproben.
     *
     * Jeweils zwischen zwei Punkten gerechnet, nie vom Anfang aus. Vom
     * Anfang aus gerechnet würde ein einzelner Ausrutscher am Start alle
     * folgenden Fenster mitfärben, und eine ruhige Strecke sähe aus, als
     * erhole sie sich langsam.
     */
    fun jeFenster(
        zeitenMillis: List<Long>,
        rahmen: List<Long>,
        abtastrate: Int
    ): List<Pair<Long, Long>> {
        if (zeitenMillis.size != rahmen.size || zeitenMillis.size < 2) return emptyList()
        return (1 until zeitenMillis.size).mapNotNull { i ->
            val zeit = zeitenMillis[i] - zeitenMillis[i - 1]
            val erwartet = zeit * abtastrate / 1000
            if (erwartet <= 0) null
            else zeitenMillis[i] to
                (rahmen[i] - rahmen[i - 1] - erwartet) * 1_000_000 / erwartet
        }
    }

    /**
     * Wandert die Abweichung, statt ruhig zu bleiben?
     *
     * Verglichen werden die Mittel der ersten und der zweiten Hälfte. Ein
     * Vergleich von kleinstem und größtem Wert taugt hier nicht: ein
     * einzelner Ausreißer -- eine Stichprobe, die eine Zehntelsekunde zu
     * spät fällt -- machte daraus sofort einen Befund.
     *
     * @return `null`, wenn es zu wenige Fenster für eine Aussage gibt.
     *         Nicht `false`: „zu wenig gemessen" ist nicht „bleibt ruhig".
     */
    fun wandert(ppm: List<Long>, grenzePpm: Double): Boolean? {
        if (ppm.size < 4) return null
        val erste = ppm.take(ppm.size / 2).average()
        val zweite = ppm.drop(ppm.size / 2).average()
        return abs(zweite - erste) >= grenzePpm
    }
}
