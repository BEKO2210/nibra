package de.ithandwerkstuttgart.nibra.ui.gestalt

/**
 * Die eine Quelle der lebendigen Flaeche.
 *
 * Sie steht hier, weil sie an zwei Stellen gebraucht wird: im Bildschirm der
 * App (Compose) und in der Blase ueber fremden Apps (eine Zeichnung im
 * Bedienungshilfen-Dienst, wo es kein Compose gibt). Zwei Abschriften wuerden
 * frueher oder spaeter auseinanderlaufen.
 *
 * Die Bahnen und Radien stehen als Zahlen ebenfalls hier, damit die
 * Rueckfall-Zeichnung ohne Grafikeinheit dieselbe Geometrie trifft.
 */
object Blobquelle {

    /** Drei Wolken, addiert und weich beschnitten. */
    const val AGSL = """
uniform float2 groesse;
uniform float  zeit;
uniform float  weite;
layout(color) uniform half4 farbeA;
layout(color) uniform half4 farbeB;
layout(color) uniform half4 farbeC;

// Anteil einer Wolke an dieser Stelle: 1 in der Mitte, weich auf 0 am Rand.
float wolke(float2 stelle, float2 mitte, float radius) {
    float d = distance(stelle, mitte);
    return 1.0 - smoothstep(radius * 0.35, radius, d);
}

half4 main(float2 fragCoord) {
    float2 mitte = groesse * 0.5;
    float  kante = min(groesse.x, groesse.y);
    float  bahn  = kante * (0.13 + weite * 0.07);
    float  radius = kante * (0.34 + weite * 0.10);

    // Drei Mittelpunkte auf einer gemeinsamen Kreisbahn, um 120 Grad
    // versetzt, mit leicht verschiedenen Umlaufzeiten -- so wiederholt
    // sich das Bild nicht sichtbar.
    float2 a = mitte + float2(cos(zeit * 0.55), sin(zeit * 0.42)) * bahn;
    float2 b = mitte + float2(cos(zeit * 0.37 + 2.09), sin(zeit * 0.61 + 2.09)) * bahn;
    float2 c = mitte + float2(cos(zeit * 0.48 + 4.19), sin(zeit * 0.33 + 4.19)) * bahn;

    float wa = wolke(fragCoord, a, radius);
    float wb = wolke(fragCoord, b, radius);
    float wc = wolke(fragCoord, c, radius);

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
}
