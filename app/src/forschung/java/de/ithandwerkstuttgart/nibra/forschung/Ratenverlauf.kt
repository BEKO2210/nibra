package de.ithandwerkstuttgart.nibra.forschung

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

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
        if (ppm.size < MINDESTFENSTER) return null
        val erste = ppm.take(ppm.size / 2).average()
        val zweite = ppm.drop(ppm.size / 2).average()
        val unterschied = abs(zweite - erste)
        if (unterschied < grenzePpm) return false
        // **Der Unterschied muss auch die Streuung überragen.** Die erste
        // Fassung verglich nur gegen eine feste Schwelle -- und meldete
        // beim Sechzig-Sekunden-Lauf „die Abweichung wandert", obwohl der
        // Unterschied bei 1,2 Standardfehlern lag. Bei fünf Fenstern, deren
        // Einzelwerte um rund 1000 ppm streuen, ist ein Unterschied von
        // 550 ppm schlicht das, was Rauschen erzeugt.
        //
        // Die Streuung kommt hier nicht aus dem Takt, sondern daher, dass
        // jede Stichprobe auf eine Blockgrenze fällt: ein Block ist bei
        // 16 kHz gut sechzig Millisekunden, auf ein Zehnsekundenfenster
        // also mehrere tausend Teile je Million.
        val streuung = streuungVon(ppm)
        val standardfehler = streuung / sqrt(ppm.size.toDouble())
        return standardfehler <= 0.0 || unterschied >= SICHERHEIT * standardfehler
    }

    private fun streuungVon(werte: List<Long>): Double {
        val mittel = werte.average()
        return sqrt(werte.sumOf { (it - mittel).pow(2) } / werte.size)
    }

    /**
     * So viele Fenster braucht es mindestens. Fünf -- was ein
     * Sechzig-Sekunden-Lauf hergibt -- reichen nicht, um einen Verlauf von
     * Rauschen zu trennen.
     */
    const val MINDESTFENSTER = 8

    /** So viele Standardfehler muss der Unterschied überragen. */
    const val SICHERHEIT = 2.0

}
