package de.ithandwerkstuttgart.nibra.dienst

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import de.ithandwerkstuttgart.nibra.R
import de.ithandwerkstuttgart.nibra.ui.gestalt.Blobquelle
import kotlin.math.PI

/**
 * Die Blase über fremden Apps -- Fläche und Symbol in **einer** Ansicht.
 *
 * Vorher war die lebendige Fläche eine [android.graphics.drawable.Drawable]
 * im Hintergrund eines `ImageButton`. Das hat auf beiden Testgeräten
 * wiederholt den Prozess abgeschossen:
 *
 * ```
 * JNI DETECTED ERROR IN APPLICATION: input is not valid Modified UTF-8
 *   string: 'unable to find uniform named P\244\205\337\177'
 *   from android.graphics.RuntimeShader.nativeUpdateUniforms
 *   ...
 *   Blasenzeichnung.draw
 *   android.view.View.getDrawableRenderNode
 * ```
 *
 * Der Uniform**name** war Speichermüll -- „groesse" und „zeit" sind
 * Konstanten aus dem Klassenpool und können nicht kaputtgehen. Etwas hat
 * also am selben Shader gearbeitet, während er schon gelesen wurde. Der
 * Stapel nennt `getDrawableRenderNode`: Android legt für Hintergrund-
 * Zeichnungen einen **eigenen** Aufzeichnungsknoten an, der außerhalb des
 * Abgleichs zwischen Anzeige- und Zeichenfaden erneuert wird.
 *
 * Das ist die Erklärung, nicht der Beweis. Der Beweis steht in
 * `messungen/blasen-stresstest.md`: derselbe Belastungslauf, null Abbrüche.
 *
 * Der Umbau folgt einer Regel: **der Shader gehört genau einer Ansicht und
 * wird nur in ihrem [onDraw] angefasst.** Jede Instanz baut ihren eigenen;
 * geteilt wird nichts.
 *
 * Am Verhalten ändert sich nichts: dieselbe Geometrie aus [Blobquelle],
 * dieselben Farben, derselbe Takt, derselbe Rückfallweg ohne Grafikeinheit,
 * dieselbe Berührungsfläche.
 */
class Blasenansicht(zusammenhang: Context) : ImageButton(zusammenhang) {

    private val farben = intArrayOf(
        ContextCompat.getColor(zusammenhang, R.color.nb_blob_a),
        ContextCompat.getColor(zusammenhang, R.color.nb_blob_b),
        ContextCompat.getColor(zusammenhang, R.color.nb_blob_c)
    )
    private val farbeGrund = ContextCompat.getColor(zusammenhang, R.color.nb_blob_grund)
    private val ringbreite = zusammenhang.resources.displayMetrics.density
    private val ringpinsel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringbreite
        color = ContextCompat.getColor(zusammenhang, R.color.marke_papier)
    }
    private val pinsel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskenpinsel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val flaeche = RectF()

    /** Feld für die drei Wolkenmitten -- einmal angelegt, je Bild überschrieben. */
    private val mitten = FloatArray(6)

    /** Vorgehaltene Farbpaare des Rückfallwegs -- sonst je Bild drei Felder. */
    private val verlaufsfarben: Array<IntArray> = Array(3) { stelle ->
        intArrayOf(farben[stelle], farben[stelle] and 0x00FFFFFF)
    }

    /**
     * Hat der Nutzer Animationen abgeschaltet, steht die Fläche still. Das
     * ist die gewollte Antwort auf „Bewegung reduzieren", kein Fehler.
     */
    private val bewegungErlaubt: Boolean = runCatching {
        Settings.Global.getFloat(
            zusammenhang.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }.getOrDefault(true)

    /**
     * Der Shader **dieser** Ansicht. Niemand sonst hält eine Referenz, und
     * angefasst wird er ausschließlich in [onDraw].
     *
     * Die Farben stehen fest, sobald die Ansicht gebaut ist -- sie einmal
     * hineinzugeben spart je Bild drei Sprünge über die Sprachgrenze. Das
     * Fenster wird bei einem Wechsel zwischen hell und dunkel ohnehin neu
     * aufgebaut.
     */
    private val shader: RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                RuntimeShader(Blobquelle.AGSL).apply {
                    setColorUniform("farbeA", farben[0])
                    setColorUniform("farbeB", farben[1])
                    setColorUniform("farbeC", farben[2])
                }
            }.getOrNull()
        } else {
            null
        }

    /** Wahr, solange diktiert wird -- der fachliche Zustand. */
    private var laeuft = false

    private var zeit = RUHEBILD

    /**
     * Der Zielpegel. Wird aus der Erkennungsschleife gesetzt, gelesen wird
     * er im Takt -- deshalb flüchtig, damit beide dieselbe Zahl sehen.
     */
    @Volatile
    private var pegelZiel = 0f

    private var weite = 0f
    private var letzterTakt = 0L

    private val takt = Runnable {
        val jetzt = SystemClock.uptimeMillis()
        val abstand = if (letzterTakt == 0L) 0f else (jetzt - letzterTakt) / 1000f
        letzterTakt = jetzt

        // Die Periode ist 200*PI und nicht 2*PI: bei 2*PI fällt der Umlauf
        // auf 0 zurück, während die drei Wolken wegen ihrer eigenen Tempi
        // (0,55 / 0,37 / 0,48) noch mitten in der Bewegung stehen -- sie
        // springen dann sichtbar. Bei 200*PI kommen alle sechs Phasen auf
        // geradzahlige Vielfache von PI zurück, der Übergang ist unsichtbar.
        zeit = Blasentakt.naechsteZeit(zeit, abstand, UMLAUF_JE_SEKUNDE, PERIODE)

        // Zeitbasiert geglättet, nicht je Bild: so bleibt der Eindruck
        // gleich, ob 30 oder 60 Bilder ankommen oder eines ausfällt.
        weite = Blasentakt.geglaettet(weite, pegelZiel, abstand, ZEITKONSTANTE)

        invalidate()
        halteTaktInGang()
    }

    init {
        // Ohne eigenen Umriss leitet Android den Schatten aus der Erhebung
        // von einem *rechteckigen* Rahmen ab -- die runde Blase bekäme einen
        // eckigen Schatten.
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(ansicht: View, umriss: Outline) {
                umriss.setOval(0, 0, ansicht.width, ansicht.height)
            }
        }
        clipToOutline = false
        // Ein ImageButton hat von Haus aus einen Hintergrund. Der wäre jetzt
        // eine zweite Zeichenebene über derselben Fläche -- genau die, die
        // den Absturz verursacht hat.
        background = null
    }

    /** Der aktuelle Pegel, 0f bis 1f. Wirkt erst über die Glättung. */
    fun setzePegel(wert: Float) {
        pegelZiel = wert.coerceIn(0f, 1f)
    }

    /** Startet und stoppt die Bewegung. Außerhalb der Aufnahme steht sie. */
    fun setzeLaeuft(laeuftJetzt: Boolean) {
        if (laeuftJetzt == laeuft) return
        laeuft = laeuftJetzt
        if (!laeuftJetzt) {
            // Zurück ins ruhende Bild, damit die Blase nach dem Diktat nicht
            // in einer zufälligen Stellung stehen bleibt.
            zeit = RUHEBILD
            pegelZiel = 0f
            weite = 0f
            invalidate()
        } else {
            pegelZiel = 0f
        }
        halteTaktInGang()
    }

    /**
     * Der Takt läuft genau dann, wenn diktiert **und** gesehen wird.
     *
     * `letzterTakt` wird beim Anhalten zurückgesetzt, damit der erste
     * Schritt nach einer Pause keinen riesigen Zeitsprung nachholt -- der
     * Blob würde sonst beim Einschalten des Bildschirms einmal weiterspringen.
     */
    private fun halteTaktInGang() {
        val sichtbar = isAttachedToWindow &&
            visibility == VISIBLE &&
            windowVisibility == VISIBLE
        if (Blasentakt.sollLaufen(laeuft, sichtbar, bewegungErlaubt)) {
            removeCallbacks(takt)
            if (letzterTakt == 0L) letzterTakt = SystemClock.uptimeMillis()
            postOnAnimationDelayed(takt, BILDABSTAND_MILLIS)
        } else {
            removeCallbacks(takt)
            letzterTakt = 0L
        }
    }

    // Der Takt hängt am Fenster: wird die Ansicht abgehängt oder der
    // Bildschirm dunkel, hört er von selbst auf. Ohne das zeichnete er
    // weiter, solange der Dienst lebt -- also den ganzen Tag.

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        halteTaktInGang()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(takt)
        letzterTakt = 0L
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(sichtbarkeit: Int) {
        super.onWindowVisibilityChanged(sichtbarkeit)
        halteTaktInGang()
    }

    override fun onVisibilityChanged(geaendert: View, sichtbarkeit: Int) {
        super.onVisibilityChanged(geaendert, sichtbarkeit)
        if (geaendert === this) halteTaktInGang()
    }

    /**
     * Zeichnet erst die Fläche, dann darüber das Symbol.
     *
     * **Die einzige Stelle, an der der Shader angefasst wird.**
     */
    override fun onDraw(leinwand: Canvas) {
        val breite = width
        val hoehe = height
        if (breite <= 0 || hoehe <= 0) {
            super.onDraw(leinwand)
            return
        }
        val kante = minOf(breite, hoehe).toFloat()
        val mitteX = breite / 2f
        val mitteY = hoehe / 2f
        val radius = kante / 2f

        // Grund, damit unter den Wolken nichts durchscheint.
        pinsel.shader = null
        pinsel.color = farbeGrund
        leinwand.drawCircle(mitteX, mitteY, radius, pinsel)

        // Der Shader braucht zwingend eine beschleunigte Fläche. Ist sie es
        // nicht -- etwa weil das Overlay-Fenster ohne Beschleunigung läuft
        // oder der Nutzer sie abgeschaltet hat --, zeichnete er nichts:
        // keine Meldung, kein Absturz, nur eine leere Blase.
        if (shader != null && leinwand.isHardwareAccelerated) {
            shader.setFloatUniform("groesse", breite.toFloat(), hoehe.toFloat())
            shader.setFloatUniform("zeit", zeit)
            shader.setFloatUniform("weite", weite)
            pinsel.shader = shader
            // Der Kreis ist zugleich der Beschnitt -- und im Gegensatz zu
            // `clipPath` auf der beschleunigten Fläche kantengeglättet.
            leinwand.drawCircle(mitteX, mitteY, radius, pinsel)
            pinsel.shader = null
        } else {
            zeichneWolken(leinwand, breite, hoehe, kante, mitteX, mitteY, radius)
        }

        leinwand.drawCircle(mitteX, mitteY, radius - ringbreite / 2f, ringpinsel)

        super.onDraw(leinwand)
    }

    /**
     * Derselbe Aufbau ohne Grafikeinheit: drei radiale Verläufe übereinander,
     * anschließend rund maskiert. Drei einzelne Kreise ließen sich nicht in
     * einen gemeinsamen Beschnitt zwingen, darum die Zwischenebene.
     */
    private fun zeichneWolken(
        leinwand: Canvas,
        breite: Int,
        hoehe: Int,
        kante: Float,
        mitteX: Float,
        mitteY: Float,
        radius: Float
    ) {
        flaeche.set(0f, 0f, breite.toFloat(), hoehe.toFloat())
        val ebene = leinwand.saveLayer(flaeche, null)

        Blobquelle.mitten(mitten, mitteX, mitteY, kante, zeit, weite)
        val wolkenradius = Blobquelle.radius(kante, weite)

        farben.indices.forEach { stelle ->
            val x = mitten[stelle * 2]
            val y = mitten[stelle * 2 + 1]
            // Verlauf selbst hängt an Stelle und Radius und muss je Bild neu
            // gebaut werden.
            pinsel.shader = RadialGradient(
                x, y, wolkenradius,
                verlaufsfarben[stelle],
                VERLAUFSSTELLEN,
                Shader.TileMode.CLAMP
            )
            leinwand.drawCircle(x, y, wolkenradius, pinsel)
        }
        pinsel.shader = null

        // Alles außerhalb des Kreises wegnehmen -- kantengeglättet.
        leinwand.drawCircle(mitteX, mitteY, radius, maskenpinsel)
        leinwand.restoreToCount(ebene)
    }

    private companion object {
        /** Haltepunkte der radialen Verläufe -- fest, also einmal. */
        val VERLAUFSSTELLEN = floatArrayOf(0f, 1f)

        /**
         * Die Stellung, in der die Fläche ruht. Nicht 0 -- dort liegen die
         * drei Wolken zu dicht übereinander und das Bild wirkt flach.
         */
        const val RUHEBILD = 1.9f

        /** Kleinste gemeinsame Periode aller sechs Tempi aus [Blobquelle]. */
        const val PERIODE = (200.0 * PI).toFloat()

        /** Wie schnell der Umlauf voranschreitet, in Bogenmaß je Sekunde. */
        const val UMLAUF_JE_SEKUNDE = 0.7f

        /**
         * 30 Bilder je Sekunde, nicht 60: drei weiche Wolken sind bei 33 ms
         * nicht von 16 ms zu unterscheiden, und die Blase liegt über fremden
         * Apps. Das halbiert den Aufwand an der teuersten Stelle.
         */
        const val BILDABSTAND_MILLIS = 33L

        /** Zeitkonstante der Pegelglättung -- entspricht dem früheren tween(320). */
        const val ZEITKONSTANTE = 0.15f
    }
}
