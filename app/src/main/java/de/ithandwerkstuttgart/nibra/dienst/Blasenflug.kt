package de.ithandwerkstuttgart.nibra.dienst

import android.os.Handler
import android.view.View
import android.view.WindowManager

/**
 * Lässt die losgelassene Blase ausschwingen, statt sie im Griff stehen zu
 * lassen.
 *
 * Wer die Blase wirft, erwartet, dass sie weiterfliegt und sich an den Rand
 * legt. Sie unter dem Finger anzuhalten fühlt sich an, als wäre etwas
 * hakengeblieben.
 *
 * Diese Klasse hält nur den Takt und das Fenster. Die Physik steht in
 * [Flugrechnung] und ist dort ohne Gerät zu prüfen.
 *
 * ## Warum von Hand gerechnet
 *
 * `ValueAnimator` fällt aus: bei abgeschalteten Animationen
 * (`ANIMATOR_DURATION_SCALE = 0`) endet er sofort, und die Blase bliebe
 * mitten im Flug stehen. `SpringAnimation` aus `dynamicanimation` wäre die
 * fertige Lösung, ist aber eine weitere Abhängigkeit für dreißig Zeilen.
 */
class Blasenflug(
    private val fensterVerwaltung: WindowManager,
    private val hauptfaden: Handler
) {

    private var laeuft = false
    private var schritt: Runnable? = null

    /**
     * Trägt die Blase mit der mitgebrachten Geschwindigkeit an den näheren
     * senkrechten Rand.
     *
     * @param geschwindigkeitX Bildpunkte je Sekunde aus dem `VelocityTracker`,
     *        im Bildschirmsinn: positiv heißt nach rechts.
     * @param sofort wahr, wenn der Nutzer Bewegung abgeschaltet hat -- dann
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

        val kanten = Flugrechnung.kanten(fensterbreite, blasenbreite, randAbstand)
        val ziel = Flugrechnung.zielkante(parameter.x, geschwindigkeitX, kanten)

        if (sofort) {
            setze(ansicht, parameter, ziel)
            fertig(ziel)
            return
        }

        // Das Vorzeichen dreht, weil `x` von rechts gemessen wird.
        var stand = Flugrechnung.Flugstand(
            stelle = parameter.x.toFloat(),
            geschwindigkeit = -geschwindigkeitX
        )
        laeuft = true

        val abstand = Flugrechnung.SCHRITT_MILLIS / 1000f
        val takt = object : Runnable {
            override fun run() {
                if (!laeuft) return
                stand = Flugrechnung.schritt(stand, ziel, abstand)

                if (Flugrechnung.istAngekommen(stand, ziel)) {
                    laeuft = false
                    setze(ansicht, parameter, ziel)
                    fertig(ziel)
                    return
                }

                // Die Feder schwingt über ihr Ziel hinaus. Das ist gewollt --
                // aber die Blase darf dabei nie aus dem Bild geraten, auch
                // nicht für ein einziges Bild.
                setze(ansicht, parameter, Flugrechnung.imBild(stand.stelle, kanten))
                hauptfaden.postDelayed(this, Flugrechnung.SCHRITT_MILLIS)
            }
        }
        schritt = takt
        hauptfaden.postDelayed(takt, Flugrechnung.SCHRITT_MILLIS)
    }

    /**
     * Hält einen laufenden Flug an und legt die Blase an ihre Zielkante.
     *
     * Ohne das Setzen bliebe sie in einem Zwischenzustand stehen, wenn der
     * Dienst mitten im Flug endet oder die Blase verschwindet.
     */
    fun stoppe() {
        laeuft = false
        schritt?.let { hauptfaden.removeCallbacks(it) }
        schritt = null
    }

    private fun setze(ansicht: View, parameter: WindowManager.LayoutParams, x: Int) {
        parameter.x = x
        runCatching { fensterVerwaltung.updateViewLayout(ansicht, parameter) }
    }
}
