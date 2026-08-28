package de.ithandwerkstuttgart.nibra.forschung

import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Perzentile und Mittel für die Verzugsmessung.
 *
 * Eigener, geprüfter Ort statt einer Zeile im Versuch. Der Mittelwert
 * allein verbirgt genau das, was den Nutzer stört: nicht der übliche
 * Verzug ärgert, sondern der schlechte. Liegt der Schnitt bei 400 ms und
 * jeder zwanzigste Fall bei drei Sekunden, fühlt sich die App kaputt an,
 * und der Schnitt sagt nichts davon.
 *
 * Gerechnet wird mit dem **nächsten Rang**: der zurückgegebene Wert ist
 * immer eine wirklich gemessene Zahl, nie eine zwischen zwei Messungen
 * gemittelte. Bei zwanzig Läufen wäre ein interpoliertes P95 eine
 * Erfindung -- es gibt schlicht keinen Messwert dazwischen.
 */
object Kennzahlen {

    /**
     * @param anteil 0.0 bis 1.0. `0.5` ist der Mittelwert der Rangfolge,
     *        `0.95` der schlechte Fall.
     * @return `null`, wenn nichts gemessen wurde -- **nicht** 0. Null Verzug
     *         wäre ein hervorragendes Ergebnis und das genaue Gegenteil von
     *         „keine Daten".
     */
    fun perzentil(werte: List<Long>, anteil: Double): Long? {
        if (werte.isEmpty()) return null
        val sortiert = werte.sorted()
        val rang = ceil(anteil.coerceIn(0.0, 1.0) * sortiert.size).toInt()
        return sortiert[(rang - 1).coerceIn(0, sortiert.lastIndex)]
    }

    /** `null` bei leerer Liste, aus demselben Grund. */
    fun mittel(werte: List<Long>): Long? =
        if (werte.isEmpty()) null else (werte.sum().toDouble() / werte.size).roundToLong()

    /**
     * Wie viele Läufe überhaupt einen Wert geliefert haben.
     *
     * Ein P95 aus drei von zwanzig Läufen ist wertlos, sieht aber genauso
     * aus wie eines aus zwanzig. Deshalb steht die Ausbeute im Bericht
     * neben jeder Kennzahl.
     */
    fun ausbeute(werte: List<Long?>): Pair<Int, Int> =
        werte.count { it != null } to werte.size
}
