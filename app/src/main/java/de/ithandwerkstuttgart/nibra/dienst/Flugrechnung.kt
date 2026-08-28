package de.ithandwerkstuttgart.nibra.dienst

import kotlin.math.abs

/**
 * Die Physik des Blasenflugs -- ohne Fenster, ohne Ansicht, ohne Android.
 *
 * Sie steht getrennt von [Blasenflug], damit sie geprüft werden kann: ein
 * langsamer Schubs, ein harter Wurf, ein Wurf gegen den nahen Rand, die
 * Randfälle. Am Gerät ließe sich das nur mit dem Finger nachstellen, und
 * dann auch nur ungefähr.
 *
 * ## Das Koordinatensystem
 *
 * Die Blase hängt unten **rechts** (`Gravity.BOTTOM or END`). Ihr `x` wird
 * darum **von rechts** gemessen: `x = 0` klebt am rechten Rand, großes `x`
 * liegt links. Das ist die häufigste Fehlerquelle in dieser Rechnung, und
 * deshalb steht es hier.
 */
object Flugrechnung {

    /** Wo die Blase liegen darf und wie schnell sie sich gerade bewegt. */
    data class Flugstand(val stelle: Float, val geschwindigkeit: Float)

    /** Die beiden Ruhelagen: am rechten und am linken Bildschirmrand. */
    data class Kanten(val rechts: Int, val links: Int)

    /**
     * Die zulässigen Kanten für eine Blase dieser Breite.
     *
     * Ist das Fenster schmaler als Blase plus zwei Ränder -- theoretisch bei
     * sehr kleinen Bildschirmen --, fallen beide Kanten zusammen. Dann gibt es
     * nur eine Ruhelage, und die liegt noch im Bild.
     */
    fun kanten(fensterbreite: Int, blasenbreite: Int, randAbstand: Int): Kanten {
        val links = (fensterbreite - blasenbreite - randAbstand).coerceAtLeast(0)
        val rechts = randAbstand.coerceAtMost(links)
        return Kanten(rechts = rechts, links = links)
    }

    /**
     * An welche Kante die Blase gehört.
     *
     * Ein Wurf über [WURFSCHWELLE] gewinnt gegen die Nähe -- wer die Blase
     * quer über den Bildschirm wirft, will sie drüben haben, auch wenn sie
     * losgelassen wurde, bevor sie die Mitte erreicht hat. Darunter entscheidet
     * der kürzere Weg.
     *
     * @param geschwindigkeitX Bildpunkte je Sekunde **im Bildschirmsinn**:
     *        positiv heißt nach rechts.
     */
    fun zielkante(stelle: Int, geschwindigkeitX: Float, kanten: Kanten): Int {
        val mitte = (kanten.links + kanten.rechts) / 2f
        return when {
            geschwindigkeitX > WURFSCHWELLE -> kanten.rechts
            geschwindigkeitX < -WURFSCHWELLE -> kanten.links
            stelle > mitte -> kanten.links
            else -> kanten.rechts
        }
    }

    /**
     * Ein Schritt der gedämpften Feder.
     *
     * Halbimplizit gerechnet: erst die Geschwindigkeit aus der Kraft, dann die
     * Stelle aus der neuen Geschwindigkeit. Explizit gerechnet schaukelt sich
     * eine Feder bei diesem Zeitschritt auf.
     *
     * @param abstand Zeitschritt in Sekunden.
     */
    fun schritt(stand: Flugstand, ziel: Int, abstand: Float): Flugstand {
        val auslenkung = stand.stelle - ziel
        val geschwindigkeit = stand.geschwindigkeit +
            (-HAERTE * auslenkung - DAEMPFUNG * stand.geschwindigkeit) * abstand
        return Flugstand(
            stelle = stand.stelle + geschwindigkeit * abstand,
            geschwindigkeit = geschwindigkeit
        )
    }

    /** Wahr, wenn die Feder nah genug und langsam genug am Ziel ist. */
    fun istAngekommen(stand: Flugstand, ziel: Int): Boolean =
        abs(stand.stelle - ziel) < RUHEWEG && abs(stand.geschwindigkeit) < RUHETEMPO

    /**
     * Hält die Blase im Bild.
     *
     * Die Feder schwingt über ihr Ziel hinaus -- das ist gewollt und sieht
     * gut aus, würde die Blase aber bei einem harten Wurf kurz über den
     * Bildschirmrand tragen. Sie darf **nie** außerhalb liegen, auch nicht
     * für ein Bild.
     */
    fun imBild(stelle: Float, kanten: Kanten): Int =
        stelle.toInt().coerceIn(kanten.rechts.coerceAtMost(0), kanten.links)

    /**
     * Hält die senkrechte Lage im Bild.
     *
     * Gezogen wird frei; ohne diese Grenze ließe sich die Blase unter die
     * Navigationsleiste oder über den oberen Rand schieben und wäre dort
     * nicht mehr zu greifen.
     */
    fun senkrechtImBild(y: Int, fensterhoehe: Int, blasenhoehe: Int, randAbstand: Int): Int {
        val oben = (fensterhoehe - blasenhoehe - randAbstand).coerceAtLeast(0)
        return y.coerceIn(randAbstand.coerceAtMost(oben), oben)
    }

    /** Ab dieser Wurfstärke gewinnt die Richtung gegen die Nähe. */
    const val WURFSCHWELLE = 900f

    /** Federhärte und Dämpfung -- dieselbe Anmutung wie Stufe RAUM. */
    const val HAERTE = 500f
    const val DAEMPFUNG = 40f

    /** Zeitschritt der Rechnung in Millisekunden. */
    const val SCHRITT_MILLIS = 16L

    /** Ab hier gilt die Feder als angekommen. */
    const val RUHEWEG = 0.6f
    const val RUHETEMPO = 24f
}
