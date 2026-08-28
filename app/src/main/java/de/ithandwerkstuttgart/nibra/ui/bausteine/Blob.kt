package de.ithandwerkstuttgart.nibra.ui.bausteine

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Die lebendige Flaeche hinter der Aufnahme: drei weiche Farbwolken, die
 * umeinander wandern und dort verschmelzen, wo sie sich ueberlagern.
 *
 * Sie atmet in Ruhe langsam und weitet sich mit dem Pegel, wenn gesprochen
 * wird -- eine ehrliche Anzeige, keine Zappel-Animation (AUFTRAG.md,
 * "Anspruch").
 *
 * Zwei Wege, weil Nibra ab Android 8 laeuft:
 *
 * - ab Android 13 (API 33) rechnet ein AGSL-Shader auf der Grafikeinheit.
 *   Die Wolken verschmelzen dort echt, weil ihre Anteile addiert und
 *   anschliessend weich beschnitten werden.
 * - darunter zeichnet [Canvas] dieselben drei Wolken als radiale Verlaeufe
 *   uebereinander. Das verschmilzt nicht ganz so weich, kostet aber nichts
 *   und sieht ruhig aus.
 *
 * In beiden Faellen dieselbe Geometrie und dieselben Farben, damit das
 * Bild auf jedem Geraet dasselbe bleibt.
 */

/** Der Shader. Drei Wolken, addiert und weich beschnitten. */
private const val BLOB_AGSL = """
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

/**
 * @param pegel 0f..1f -- wie laut gerade gesprochen wird. In Ruhe 0f.
 * @param laeuft ob gerade aufgenommen wird; in Ruhe atmet die Flaeche nur.
 */
@Composable
fun Blobflaeche(
    pegel: Float,
    laeuft: Boolean,
    farbeA: Color,
    farbeB: Color,
    farbeC: Color,
    modifier: Modifier = Modifier
) {
    // Die Zeit laeuft immer -- auch in Ruhe, sonst steht das Bild still und
    // wirkt eingefroren. In Ruhe nur langsamer.
    val takt = rememberInfiniteTransition(label = "blob")
    val zeit by takt.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (laeuft) 9_000 else 18_000,
                easing = LinearEasing
            )
        ),
        label = "umlauf"
    )
    // Der Pegel wird geglaettet, damit die Flaeche nicht zuckt.
    val weite by animateFloatAsState(
        targetValue = if (laeuft) pegel.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "weite"
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(BLOB_AGSL) }
        Canvas(modifier = modifier) {
            shader.setFloatUniform("groesse", size.width, size.height)
            shader.setFloatUniform("zeit", zeit)
            shader.setFloatUniform("weite", weite)
            shader.setColorUniform("farbeA", farbeA.toArgb())
            shader.setColorUniform("farbeB", farbeB.toArgb())
            shader.setColorUniform("farbeC", farbeC.toArgb())
            drawRect(brush = ShaderBrush(shader))
        }
    } else {
        Canvas(modifier = modifier) {
            zeichneWolken(zeit, weite, farbeA, farbeB, farbeC)
        }
    }
}

/** Derselbe Aufbau ohne Grafikeinheit: drei radiale Verlaeufe uebereinander. */
private fun DrawScope.zeichneWolken(
    zeit: Float,
    weite: Float,
    farbeA: Color,
    farbeB: Color,
    farbeC: Color
) {
    val kante = size.minDimension
    val bahn = kante * (0.13f + weite * 0.07f)
    val radius = kante * (0.34f + weite * 0.10f)
    val mitte = Offset(size.width / 2f, size.height / 2f)

    val stellen = listOf(
        Triple(farbeA, 0.55f to 0.42f, 0f),
        Triple(farbeB, 0.37f to 0.61f, 2.09f),
        Triple(farbeC, 0.48f to 0.33f, 4.19f)
    )
    stellen.forEach { (farbe, tempo, versatz) ->
        val punkt = Offset(
            mitte.x + cos(zeit * tempo.first + versatz) * bahn,
            mitte.y + sin(zeit * tempo.second + versatz) * bahn
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(farbe.copy(alpha = 0.75f), Color.Transparent),
                center = punkt,
                radius = radius
            ),
            radius = radius,
            center = punkt
        )
    }
}
