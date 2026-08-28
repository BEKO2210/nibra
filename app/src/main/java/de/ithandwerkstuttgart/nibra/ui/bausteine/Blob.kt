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
import de.ithandwerkstuttgart.nibra.ui.gestalt.Blobquelle
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
    // Die Periode ist 200*PI und nicht 2*PI. Bei 2*PI faellt der Umlauf auf 0
    // zurueck, waehrend die drei Wolken wegen ihrer eigenen Tempi (0,55 /
    // 0,37 / 0,48) noch mitten in der Bewegung stehen -- sie springen dann
    // alle neun Sekunden sichtbar. Bei 200*PI kommen alle sechs Phasen auf
    // geradzahlige Vielfache von PI zurueck; der Uebergang ist unsichtbar.
    // Die Dauer waechst im selben Verhaeltnis, die Geschwindigkeit bleibt.
    val zeit by takt.animateFloat(
        initialValue = 0f,
        targetValue = (200f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (laeuft) 900_000 else 1_800_000,
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
        val shader = remember { RuntimeShader(Blobquelle.AGSL) }
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
    // Dieselben Zahlen wie im Shader -- beide lesen aus `Blobquelle`, sonst
    // laufen die zwei Wege mit der Zeit auseinander.
    val kante = size.minDimension
    val bahn = kante * (Blobquelle.BAHN_RUHE + weite * Blobquelle.BAHN_JE_PEGEL)
    val radius = kante * (Blobquelle.RADIUS_RUHE + weite * Blobquelle.RADIUS_JE_PEGEL)
    val mitte = Offset(size.width / 2f, size.height / 2f)

    listOf(farbeA, farbeB, farbeC).forEachIndexed { stelle, farbe ->
        val punkt = Offset(
            mitte.x + cos(zeit * Blobquelle.TEMPO_X[stelle] + Blobquelle.VERSATZ[stelle]) * bahn,
            mitte.y + sin(zeit * Blobquelle.TEMPO_Y[stelle] + Blobquelle.VERSATZ[stelle]) * bahn
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
