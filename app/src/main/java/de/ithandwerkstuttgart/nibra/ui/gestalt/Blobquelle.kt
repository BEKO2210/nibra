package de.ithandwerkstuttgart.nibra.ui.gestalt

import kotlin.math.cos
import kotlin.math.sin

/**
 * Die eine Quelle der lebendigen Flaeche.
 *
 * Sie steht hier, weil sie an zwei Stellen gebraucht wird: im Bildschirm der
 * App (Compose) und in der Blase ueber fremden Apps (eine Zeichnung im
 * Bedienungshilfen-Dienst, wo es kein Compose gibt). Zwei Abschriften wuerden
 * frueher oder spaeter auseinanderlaufen.
 *
 * ## Warum die Mittelpunkte nicht im Shader gerechnet werden
 *
 * Sie haengen allein an der Zeit, nicht am Bildpunkt -- im Shader stuenden sie
 * trotzdem in `main()` und liefen damit **je Bildpunkt**. Bei einer Flaeche von
 * 562 x 562 Punkten waren das rund 1,9 Millionen Sinus- und Kosinusaufrufe je
 * Bild, gemessen als der groesste Einzelposten der Bildzeit.
 *
 * Sie werden darum einmal je Bild auf dem Prozessor gerechnet ([mitten]) und
 * als Uniform hineingereicht. Denselben Weg nehmen die Rueckfall-Zeichnungen,
 * die ohne Grafikeinheit auskommen muessen -- eine Geometrie fuer alle vier
 * Zeichenwege.
 */
object Blobquelle {

    /** Drei Wolken, addiert und weich beschnitten. */
    const val AGSL = """
uniform float2 mitteA;
uniform float2 mitteB;
uniform float2 mitteC;
uniform float  radius;
layout(color) uniform half4 farbeA;
layout(color) uniform half4 farbeB;
layout(color) uniform half4 farbeC;

// Anteil einer Wolke an dieser Stelle: 1 in der Mitte, weich auf 0 am Rand.
float wolke(float2 stelle, float2 mitte) {
    return 1.0 - smoothstep(radius * 0.35, radius, distance(stelle, mitte));
}

half4 main(float2 fragCoord) {
    float wa = wolke(fragCoord, mitteA);
    float wb = wolke(fragCoord, mitteB);
    float wc = wolke(fragCoord, mitteC);

    // Addieren laesst sie verschmelzen; das Beschneiden haelt die
    // Ueberlagerung davon ab, in reines Weiss zu kippen.
    float summe = min(wa + wb + wc, 1.0);
    half3 ton = (farbeA.rgb * wa + farbeB.rgb * wb + farbeC.rgb * wc)
                / max(wa + wb + wc, 0.0001);

    // Innen voll, nach aussen weich auslaufend.
    float deckung = smoothstep(0.0, 0.55, summe);
    return half4(ton * deckung, deckung);
}
"""

    /** Grundabstand der Wolkenmitten vom Mittelpunkt, als Anteil der Kante. */
    const val BAHN_RUHE = 0.13f
    const val BAHN_JE_PEGEL = 0.07f

    /** Radius einer Wolke, als Anteil der Kante. */
    const val RADIUS_RUHE = 0.34f
    const val RADIUS_JE_PEGEL = 0.10f

    /** Die drei Umlaufgeschwindigkeiten und ihre Versaetze, je 120 Grad. */
    val TEMPO_X = floatArrayOf(0.55f, 0.37f, 0.48f)
    val TEMPO_Y = floatArrayOf(0.42f, 0.61f, 0.33f)
    val VERSATZ = floatArrayOf(0f, 2.09f, 4.19f)

    /** Wie weit die Wolkenmitten bei diesem Pegel vom Mittelpunkt abweichen. */
    fun bahn(kante: Float, weite: Float): Float =
        kante * (BAHN_RUHE + weite * BAHN_JE_PEGEL)

    /** Wie gross eine Wolke bei diesem Pegel ist. */
    fun radius(kante: Float, weite: Float): Float =
        kante * (RADIUS_RUHE + weite * RADIUS_JE_PEGEL)

    /**
     * Die drei Wolkenmitten zu diesem Zeitpunkt.
     *
     * Schreibt in das uebergebene Feld, statt eines anzulegen: das hier laeuft
     * je Bild, und ein frisches Feld je Bild waere Muell, den der Sammler
     * mitten in der Animation wieder einsammeln muesste.
     *
     * @param ziel Feld der Laenge 6: x0, y0, x1, y1, x2, y2.
     */
    fun mitten(
        ziel: FloatArray,
        mitteX: Float,
        mitteY: Float,
        kante: Float,
        zeit: Float,
        weite: Float
    ) {
        val bahn = bahn(kante, weite)
        for (stelle in 0..2) {
            ziel[stelle * 2] = mitteX + cos(zeit * TEMPO_X[stelle] + VERSATZ[stelle]) * bahn
            ziel[stelle * 2 + 1] = mitteY + sin(zeit * TEMPO_Y[stelle] + VERSATZ[stelle]) * bahn
        }
    }
}
