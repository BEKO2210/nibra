package de.ithandwerkstuttgart.nibra.dienst

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import de.ithandwerkstuttgart.nibra.R
import de.ithandwerkstuttgart.nibra.ui.gestalt.Blobquelle
import kotlin.math.PI

/**
 * Die lebendige Flaeche als Hintergrund der Blase ueber fremden Apps.
 *
 * Dieselbe Bildsprache wie im Hauptbildschirm, aber ohne Compose -- der
 * Bedienungshilfen-Dienst arbeitet mit gewoehnlichen Android-Ansichten.
 * Geometrie aus [Blobquelle], Farben aus denselben Ressourcen, die auch das
 * Thema der App liest. Keine zweite Abschrift.
 *
 * Sie ist eine [Drawable] und kein eigener View, damit am bestehenden
 * `ImageButton` nichts angefasst werden muss: Tippen, Ziehen, Symbol,
 * Polsterung und Erhebung bleiben, wie sie sind.
 *
 * **Bewegt wird nur waehrend der Aufnahme.** Die Blase liegt oft minutenlang
 * ueber fremden Apps; dauerhafte Arbeit auf der Grafikeinheit kostet dort
 * Akku, ohne dass jemand hinsieht.
 */
class Blasenzeichnung(context: Context) : Drawable(), Animatable {

    private val farben = intArrayOf(
        ContextCompat.getColor(context, R.color.nb_blob_a),
        ContextCompat.getColor(context, R.color.nb_blob_b),
        ContextCompat.getColor(context, R.color.nb_blob_c)
    )
    private val farbeGrund = ContextCompat.getColor(context, R.color.nb_blob_grund)
    private val ringbreite = context.resources.displayMetrics.density
    private val ringpinsel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringbreite
        color = ContextCompat.getColor(context, R.color.marke_papier)
    }
    private val pinsel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskenpinsel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val flaeche = RectF()

    /** Feld fuer die drei Wolkenmitten -- einmal angelegt, je Bild ueberschrieben. */
    private val mitten = FloatArray(6)

    /** Vorgehaltene Farbpaare des Rueckfallwegs -- sonst je Bild drei Felder. */
    private val verlaufsfarben: Array<IntArray> = Array(3) { stelle ->
        intArrayOf(farben[stelle], farben[stelle] and 0x00FFFFFF)
    }

    /**
     * Hat der Nutzer Animationen abgeschaltet, steht die Flaeche still. Das
     * ist die gewollte Antwort auf "Bewegung reduzieren", kein Fehler.
     */
    private val bewegungErlaubt: Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }.getOrDefault(true)

    /**
     * Die Farben stehen fest, sobald die Zeichnung gebaut ist -- sie einmal
     * in den Shader zu geben spart je Bild drei JNI-Aufrufe. Das Fenster wird
     * bei einem Wechsel zwischen hell und dunkel ohnehin neu aufgebaut.
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

    /**
     * Wahr, solange die Blase wirklich zu sehen ist.
     *
     * Getrennt von [laeuft], weil beides auseinanderfaellt: geht waehrend
     * eines Diktats der Bildschirm aus, ist die Blase unsichtbar, aber das
     * Diktat laeuft weiter. Der Takt muss dann ruhen und beim Einschalten
     * genau dort weitergehen -- nicht bei null anfangen.
     */
    private var sichtbar = true

    private var zeit = RUHEBILD
    private var pegelZiel = 0f
    private var weite = 0f
    private var letzterTakt = 0L

    private val takt = Runnable {
        val jetzt = SystemClock.uptimeMillis()
        val abstand = if (letzterTakt == 0L) 0f else (jetzt - letzterTakt) / 1000f
        letzterTakt = jetzt

        // Die Periode ist 200*PI und nicht 2*PI: bei 2*PI faellt der Umlauf
        // auf 0 zurueck, waehrend die drei Wolken wegen ihrer eigenen Tempi
        // (0,55 / 0,37 / 0,48) noch mitten in der Bewegung stehen -- sie
        // springen dann sichtbar. Bei 200*PI kommen alle sechs Phasen auf
        // geradzahlige Vielfache von PI zurueck, der Uebergang ist unsichtbar.
        zeit = Blasentakt.naechsteZeit(zeit, abstand, UMLAUF_JE_SEKUNDE, PERIODE)

        // Zeitbasiert geglaettet, nicht je Bild: so bleibt der Eindruck
        // gleich, ob 30 oder 60 Bilder ankommen oder eines ausfaellt.
        weite = Blasentakt.geglaettet(weite, pegelZiel, abstand, ZEITKONSTANTE)

        invalidateSelf()
        if (laeuft && sichtbar) planeNaechstenTakt() else halteTaktInGang()
    }

    /** Der aktuelle Pegel, 0f bis 1f. Wirkt erst ueber die Glaettung. */
    fun setzePegel(wert: Float) {
        pegelZiel = wert.coerceIn(0f, 1f)
    }

    /** Startet und stoppt die Bewegung. Ausserhalb der Aufnahme steht sie. */
    fun setzeLaeuft(laeuft: Boolean) {
        if (laeuft) start() else stop()
    }

    override fun start() {
        if (laeuft) return
        laeuft = true
        pegelZiel = 0f
        halteTaktInGang()
    }

    override fun stop() {
        if (!laeuft && letzterTakt == 0L) return
        laeuft = false
        halteTaktInGang()
        // Zurueck ins ruhende Bild, damit die Blase nach dem Diktat nicht in
        // einer zufaelligen Stellung stehen bleibt.
        zeit = RUHEBILD
        pegelZiel = 0f
        weite = 0f
        invalidateSelf()
    }

    /**
     * Der Takt laeuft genau dann, wenn diktiert **und** gesehen wird.
     *
     * `letzterTakt` wird beim Anhalten zurueckgesetzt, damit der erste
     * Schritt nach einer Pause keinen riesigen Zeitsprung nachholt -- der
     * Blob wuerde sonst beim Einschalten des Bildschirms einmal weiterspringen.
     */
    private fun halteTaktInGang() {
        val sollLaufen = Blasentakt.sollLaufen(laeuft, sichtbar, bewegungErlaubt)
        if (sollLaufen) {
            if (letzterTakt == 0L) planeNaechstenTakt()
        } else {
            unscheduleSelf(takt)
            letzterTakt = 0L
        }
    }

    override fun isRunning(): Boolean = laeuft

    /**
     * Wird die Blase unsichtbar, muss der Takt weg -- sonst zeichnet er
     * weiter, solange der Dienst lebt, also den ganzen Tag.
     */
    override fun setVisible(sichtbar: Boolean, neuStarten: Boolean): Boolean {
        this.sichtbar = sichtbar
        halteTaktInGang()
        return super.setVisible(sichtbar, neuStarten)
    }

    /**
     * Ohne diese Angabe leitet Android den Schatten aus der Erhebung von
     * einem *rechteckigen* Umriss ab -- die runde Blase bekaeme einen
     * eckigen Schatten. Bisher besorgte das die Oval-Form der alten
     * Hintergrundzeichnung.
     */
    override fun getOutline(umriss: Outline) {
        umriss.setOval(bounds)
    }

    /**
     * `scheduleSelf` und nicht `Choreographer`: der Takt laeuft ueber die
     * Rueckmeldung der Ansicht und pausiert von selbst, sobald sie abgehaengt
     * ist. Ein Choreographer haelt dagegen eine Referenz, die das
     * Overlay-Fenster ueberlebt. `ValueAnimator` scheidet ebenfalls aus: bei
     * abgeschalteten Animationen endet er sofort und die Flaeche friere ein,
     * ohne dass jemand den Grund saehe.
     */
    private fun planeNaechstenTakt() {
        unscheduleSelf(takt)
        if (letzterTakt == 0L) letzterTakt = SystemClock.uptimeMillis()
        scheduleSelf(takt, SystemClock.uptimeMillis() + BILDABSTAND_MILLIS)
    }

    override fun draw(canvas: Canvas) {
        val rand = bounds
        if (rand.isEmpty) return
        val kante = minOf(rand.width(), rand.height()).toFloat()
        val mitteX = rand.exactCenterX()
        val mitteY = rand.exactCenterY()
        val radius = kante / 2f

        // Grund, damit unter den Wolken nichts durchscheint.
        pinsel.shader = null
        pinsel.color = farbeGrund
        canvas.drawCircle(mitteX, mitteY, radius, pinsel)

        // Der Shader braucht zwingend eine beschleunigte Flaeche. Ist sie es
        // nicht -- etwa weil das Overlay-Fenster ohne Beschleunigung laeuft
        // oder der Nutzer sie abgeschaltet hat --, zeichnete er nichts:
        // keine Meldung, kein Absturz, nur eine leere Blase.
        if (shader != null && canvas.isHardwareAccelerated) {
            shader.setFloatUniform("groesse", rand.width().toFloat(), rand.height().toFloat())
            shader.setFloatUniform("zeit", zeit)
            shader.setFloatUniform("weite", weite)
            pinsel.shader = shader
            // Der Kreis ist zugleich der Beschnitt -- und im Gegensatz zu
            // `clipPath` auf der beschleunigten Flaeche kantengeglaettet.
            canvas.drawCircle(mitteX, mitteY, radius, pinsel)
            pinsel.shader = null
        } else {
            zeichneWolken(canvas, rand, kante, mitteX, mitteY, radius)
        }

        canvas.drawCircle(mitteX, mitteY, radius - ringbreite / 2f, ringpinsel)
    }

    /**
     * Derselbe Aufbau ohne Grafikeinheit: drei radiale Verlaeufe uebereinander,
     * anschliessend rund maskiert. Drei einzelne Kreise liessen sich nicht in
     * einen gemeinsamen Beschnitt zwingen, darum die Zwischenebene.
     */
    private fun zeichneWolken(
        canvas: Canvas,
        rand: android.graphics.Rect,
        kante: Float,
        mitteX: Float,
        mitteY: Float,
        radius: Float
    ) {
        flaeche.set(rand)
        val ebene = canvas.saveLayer(flaeche, null)

        Blobquelle.mitten(mitten, mitteX, mitteY, kante, zeit, weite)
        val wolkenradius = Blobquelle.radius(kante, weite)

        farben.indices.forEach { stelle ->
            val x = mitten[stelle * 2]
            val y = mitten[stelle * 2 + 1]
            // Verlauf selbst haengt an Stelle und Radius und muss je Bild neu
            // gebaut werden.
            pinsel.shader = RadialGradient(
                x, y, wolkenradius,
                verlaufsfarben[stelle],
                VERLAUFSSTELLEN,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, wolkenradius, pinsel)
        }
        pinsel.shader = null

        // Alles ausserhalb des Kreises wegnehmen -- kantengeglaettet.
        canvas.drawCircle(mitteX, mitteY, radius, maskenpinsel)
        canvas.restoreToCount(ebene)
    }

    override fun setAlpha(alpha: Int) {
        pinsel.alpha = alpha
    }

    override fun setColorFilter(filter: ColorFilter?) {
        pinsel.colorFilter = filter
    }

    @Deprecated("Von Drawable vorgegeben", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        /** Haltepunkte der radialen Verlaeufe -- fest, also einmal. */
        val VERLAUFSSTELLEN = floatArrayOf(0f, 1f)

        /**
         * Die Stellung, in der die Flaeche ruht. Nicht 0 -- dort liegen die
         * drei Wolken zu dicht uebereinander und das Bild wirkt flach.
         */
        const val RUHEBILD = 1.9f

        /** Kleinste gemeinsame Periode aller sechs Tempi aus [Blobquelle]. */
        const val PERIODE = (200.0 * PI).toFloat()

        /** Wie schnell der Umlauf voranschreitet, in Bogenmass je Sekunde. */
        const val UMLAUF_JE_SEKUNDE = 0.7f

        /**
         * 30 Bilder je Sekunde, nicht 60: drei weiche Wolken sind bei 33 ms
         * nicht von 16 ms zu unterscheiden, und die Blase liegt ueber fremden
         * Apps. Das halbiert den Aufwand an der teuersten Stelle.
         */
        const val BILDABSTAND_MILLIS = 33L

        /** Zeitkonstante der Pegelglaettung -- entspricht dem frueheren tween(320). */
        const val ZEITKONSTANTE = 0.15f
    }
}
