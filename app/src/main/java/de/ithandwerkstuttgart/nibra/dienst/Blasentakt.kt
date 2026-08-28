package de.ithandwerkstuttgart.nibra.dienst

import kotlin.math.exp

/**
 * Die Entscheidungen hinter dem Takt der Blasenzeichnung -- ohne Android,
 * damit sie geprueft werden koennen.
 *
 * Es sind wenige Zeilen, aber es sind die, an denen die Systemfaelle haengen:
 * Bildschirm aus waehrend eines Diktats, abgeschaltete Animationen, ein
 * ausgefallenes Bild.
 */
object Blasentakt {

    /**
     * Der Takt laeuft genau dann, wenn diktiert **und** gesehen wird und
     * Bewegung ueberhaupt erlaubt ist.
     *
     * Die drei Bedingungen fallen auseinander: geht waehrend eines Diktats
     * der Bildschirm aus, laeuft das Diktat weiter, aber niemand sieht die
     * Blase -- dann darf auch nichts gerechnet werden.
     */
    fun sollLaufen(laeuft: Boolean, sichtbar: Boolean, bewegungErlaubt: Boolean): Boolean =
        laeuft && sichtbar && bewegungErlaubt

    /**
     * Der naechste Stand des Umlaufs.
     *
     * Die Periode ist ein Vielfaches, bei dem alle drei Wolken gleichzeitig
     * an ihrem Anfang stehen -- sonst springt das Bild beim Ueberlauf.
     */
    fun naechsteZeit(zeit: Float, abstandSekunden: Float, tempo: Float, periode: Float): Float =
        (zeit + abstandSekunden * tempo) % periode

    /**
     * Ein Schritt der Pegelglaettung, zeitbasiert.
     *
     * Nicht je Bild um einen festen Anteil: dann haenge das Ergebnis an der
     * Bildrate, und bei 30 Bildern saehe es anders aus als bei 60. Ueber die
     * Zeitkonstante bleibt der Eindruck gleich, auch wenn ein Bild ausfaellt.
     */
    fun geglaettet(
        bisher: Float,
        ziel: Float,
        abstandSekunden: Float,
        zeitkonstante: Float
    ): Float = bisher + (ziel - bisher) * (1f - exp(-abstandSekunden / zeitkonstante))
}
