package de.ithandwerkstuttgart.nibra.dienst

import android.os.Handler
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Laesst die losgelassene Blase ausschwingen, statt sie im Griff stehen zu
 * lassen.
 *
 * Wer die Blase wirft, erwartet, dass sie weiterfliegt und sich an den Rand
 * legt. Sie unter dem Finger anzuhalten fuehlt sich an, als haette man etwas
 * falsch gemacht.
 *
 * ## Warum von Hand gerechnet
 *
 * `ValueAnimator` faellt aus: bei abgeschalteten Animationen
 * (`ANIMATOR_DURATION_SCALE = 0`) endet er sofort, und die Blase bliebe
 * mitten im Flug stehen. `SpringAnimation` aus `dynamicanimation` waere die
 * fertige Loesung, ist aber eine weitere Abhaengigkeit fuer dreissig Zeilen.
 *
 * Gerechnet wird deshalb selbst, mit derselben Physik wie
 * [de.ithandwerkstuttgart.nibra.ui.gestalt.Stufe.RAUM]: eine gedaempfte Feder,
 * die die mitgebrachte Geschwindigkeit uebernimmt und am Rand ankommt, ohne
 * zu wippen.
 */
class Blasenflug(
    private val fensterVerwaltung: WindowManager,
    private val hauptfaden: Handler
) {

    private var laeuft = false
    private var schritt: Runnable? = null

    /**
     * Traegt die Blase mit der mitgebrachten Geschwindigkeit an den naeheren
     * senkrechten Rand.
     *
     * @param geschwindigkeitX Bildpunkte je Sekunde, aus dem `VelocityTracker`.
     *        Positiv heisst nach rechts -- die Blase haengt rechts, ihr `x`
     *        waechst nach links, das Vorzeichen wird hier gedreht.
     * @param sofort wahr, wenn der Nutzer Bewegung abgeschaltet hat: dann
     *        springt sie an den Rand, statt zu fliegen.
     */
    fun anDenRand(
        ansicht: View,
        parameter: WindowManager.LayoutParams,
        geschwindigkeitX: Float,
        randAbstand: Int,
        fensterbreite: Int,
        blasenbreite: Int,
        sofort: Boolean,
        fertig: (Int) -> Unit
    ) {
        stoppe()

        val linkerRand = fensterbreite - blasenbreite - randAbstand
        val rechterRand = randAbstand
        val mitte = (linkerRand + rechterRand) / 2f

        // Die Geschwindigkeit entscheidet mit: ein Wurf ueber die Schwelle
        // gewinnt gegen die Naehe. Sonst zieht der naehere Rand.
        val ziel = when {
            geschwindigkeitX > WURFSCHWELLE -> rechterRand
            geschwindigkeitX < -WURFSCHWELLE -> linkerRand
            parameter.x > mitte -> linkerRand
            else -> rechterRand
        }

        if (sofort) {
            parameter.x = ziel
            runCatching { fensterVerwaltung.updateViewLayout(ansicht, parameter) }
            fertig(ziel)
            return
        }

        // Das Vorzeichen dreht, weil `x` von rechts gemessen wird.
        var geschwindigkeit = -geschwindigkeitX
        var stelle = parameter.x.toFloat()
        laeuft = true

        val takt = object : Runnable {
            override fun run() {
                if (!laeuft) return
                val abstand = SCHRITT_MILLIS / 1000f

                // Gedaempfte Feder: Beschleunigung = -Haerte * Auslenkung
                // - Daempfung * Geschwindigkeit. Halbimplizit gerechnet,
                // damit sie bei diesem Zeitschritt nicht aufschwingt.
                val auslenkung = stelle - ziel
                geschwindigkeit += (-HAERTE * auslenkung - DAEMPFUNG * geschwindigkeit) * abstand
                stelle += geschwindigkeit * abstand

                val angekommen = abs(stelle - ziel) < RUHEWEG &&
                    abs(geschwindigkeit) < RUHETEMPO
                if (angekommen) {
                    stelle = ziel.toFloat()
                    laeuft = false
                }

                parameter.x = stelle.roundToInt().coerceAtLeast(0)
                runCatching { fensterVerwaltung.updateViewLayout(ansicht, parameter) }

                if (laeuft) {
                    hauptfaden.postDelayed(this, SCHRITT_MILLIS)
                } else {
                    fertig(parameter.x)
                }
            }
        }
        schritt = takt
        hauptfaden.postDelayed(takt, SCHRITT_MILLIS)
    }

    /** Haelt einen laufenden Flug an -- etwa wenn die Blase verschwindet. */
    fun stoppe() {
        laeuft = false
        schritt?.let { hauptfaden.removeCallbacks(it) }
        schritt = null
    }

    private companion object {
        /** Ab dieser Wurfstaerke gewinnt die Richtung gegen die Naehe. */
        const val WURFSCHWELLE = 900f

        /** Federhaerte und Daempfung -- dieselbe Anmutung wie Stufe RAUM. */
        const val HAERTE = 500f
        const val DAEMPFUNG = 40f

        const val SCHRITT_MILLIS = 16L

        /** Ab hier gilt die Feder als angekommen. */
        const val RUHEWEG = 0.6f
        const val RUHETEMPO = 24f
    }
}
