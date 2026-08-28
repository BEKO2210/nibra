package de.ithandwerkstuttgart.nibra.dienst

import kotlin.math.exp

/**
 * Die Entscheidungen hinter dem Takt der Blasenzeichnung -- ohne Android,
 * damit sie geprüft werden können.
 *
 * Es sind wenige Zeilen, aber es sind die, an denen die Systemfälle hängen:
 * Bildschirm aus während eines Diktats, abgeschaltete Animationen, ein
 * ausgefallenes Bild.
 */
object Blasentakt {

    /**
     * Der Takt läuft genau dann, wenn diktiert **und** gesehen wird und
     * Bewegung überhaupt erlaubt ist.
     *
     * Die drei Bedingungen fallen auseinander: geht während eines Diktats
     * der Bildschirm aus, läuft das Diktat weiter, aber niemand sieht die
     * Blase -- dann darf auch nichts gerechnet werden.
     */
    fun sollLaufen(laeuft: Boolean, sichtbar: Boolean, bewegungErlaubt: Boolean): Boolean =
        laeuft && sichtbar && bewegungErlaubt

    /**
     * Der nächste Stand des Umlaufs.
     *
     * Die Periode ist ein Vielfaches, bei dem alle drei Wolken gleichzeitig
     * an ihrem Anfang stehen -- sonst springt das Bild beim Überlauf.
     */
    fun naechsteZeit(zeit: Float, abstandSekunden: Float, tempo: Float, periode: Float): Float =
        (zeit + abstandSekunden * tempo) % periode

    /**
     * Ein Schritt der Pegelglättung, zeitbasiert.
     *
     * Nicht je Bild um einen festen Anteil: dann hänge das Ergebnis an der
     * Bildrate, und bei 30 Bildern sähe es anders aus als bei 60. Über die
     * Zeitkonstante bleibt der Eindruck gleich, auch wenn ein Bild ausfällt.
     */
    fun geglaettet(
        bisher: Float,
        ziel: Float,
        abstandSekunden: Float,
        zeitkonstante: Float
    ): Float = bisher + (ziel - bisher) * (1f - exp(-abstandSekunden / zeitkonstante))
}
