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
import de.ithandwerkstuttgart.nibra.ui.gestalt.Bewegung
import de.ithandwerkstuttgart.nibra.ui.gestalt.Blobquelle
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI

/**
 * Die lebendige Fläche hinter der Aufnahme: drei weiche Farbwolken, die
 * umeinander wandern und dort verschmelzen, wo sie sich überlagern.
 *
 * Sie atmet in Ruhe langsam und weitet sich mit dem Pegel, wenn gesprochen
 * wird -- eine ehrliche Anzeige, keine Zappel-Animation (AUFTRAG.md,
 * "Anspruch").
 *
 * Zwei Wege, weil Nibra ab Android 8 läuft:
 *
 * - ab Android 13 (API 33) rechnet ein AGSL-Shader auf der Grafikeinheit.
 *   Die Wolken verschmelzen dort echt, weil ihre Anteile addiert und
 *   anschließend weich beschnitten werden.
 * - darunter zeichnet [Canvas] dieselben drei Wolken als radiale Verläufe
 *   übereinander. Das verschmilzt nicht ganz so weich, kostet aber nichts
 *   und sieht ruhig aus.
 *
 * In beiden Fällen dieselbe Geometrie und dieselben Farben, damit das
 * Bild auf jedem Gerät dasselbe bleibt.
 */



/**
 * @param pegel 0f..1f -- wie laut gerade gesprochen wird. In Ruhe 0f.
 * @param läuft ob gerade aufgenommen wird; in Ruhe atmet die Fläche nur.
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
    // Die Zeit läuft immer -- auch in Ruhe, sonst steht das Bild still und
    // wirkt eingefroren. In Ruhe nur langsamer.
    val takt = rememberInfiniteTransition(label = "blob")
    // Die Periode ist 200*PI und nicht 2*PI. Bei 2*PI fällt der Umlauf auf 0
    // zurück, während die drei Wolken wegen ihrer eigenen Tempi (0,55 /
    // 0,37 / 0,48) noch mitten in der Bewegung stehen -- sie springen dann
    // alle neun Sekunden sichtbar. Bei 200*PI kommen alle sechs Phasen auf
    // geradzahlige Vielfache von PI zurück; der Übergang ist unsichtbar.
    // Die Dauer wächst im selben Verhältnis, die Geschwindigkeit bleibt.
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
    // Der Pegel wird geglättet, damit die Fläche nicht zuckt.
    //
    // Eine Feder und kein Zeitverlauf: das Ziel wird zehnmal je Sekunde neu
    // gesetzt, und ein `tween` fängt bei jedem neuen Ziel von vorn an -- der
    // Wert käme nie zur Ruhe und die Bewegung wirkte gehackt. Eine Feder
    // nimmt ihre Geschwindigkeit mit und liest sich als träge Maße.
    val weite by animateFloatAsState(
        targetValue = if (laeuft) pegel.coerceIn(0f, 1f) else 0f,
        animationSpec = Bewegung.wirkung(),
        label = "weite"
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(Blobquelle.AGSL) }
        // Farben und Pinsel hängen nicht am Bild. Sie hier einmal zu setzen
        // spart je Bild drei JNI-Aufrufe, drei `toArgb`-Umrechnungen und eine
        // frische `ShaderBrush` -- Müll, den der Sammler sonst während der
        // Animation wieder einsammeln muss.
        val pinsel = remember(shader, farbeA, farbeB, farbeC) {
            shader.setColorUniform("farbeA", farbeA.toArgb())
            shader.setColorUniform("farbeB", farbeB.toArgb())
            shader.setColorUniform("farbeC", farbeC.toArgb())
            ShaderBrush(shader)
        }
        // Ein Feld für die drei Mittelpunkte, einmal angelegt und je Bild
        // überschrieben -- statt je Bild ein neues.
        val mitten = remember { FloatArray(6) }
        Canvas(modifier = modifier) {
            val kante = size.minDimension
            Blobquelle.mitten(
                ziel = mitten,
                mitteX = size.width / 2f,
                mitteY = size.height / 2f,
                kante = kante,
                zeit = zeit,
                weite = weite
            )
            shader.setFloatUniform("mitteA", mitten[0], mitten[1])
            shader.setFloatUniform("mitteB", mitten[2], mitten[3])
            shader.setFloatUniform("mitteC", mitten[4], mitten[5])
            shader.setFloatUniform("radius", Blobquelle.radius(kante, weite))
            drawRect(brush = pinsel)
        }
    } else {
        val mitten = remember { FloatArray(6) }
        Canvas(modifier = modifier) {
            zeichneWolken(mitten, zeit, weite, farbeA, farbeB, farbeC)
        }
    }
}

/** Derselbe Aufbau ohne Grafikeinheit: drei radiale Verläufe übereinander. */
private fun DrawScope.zeichneWolken(
    mitten: FloatArray,
    zeit: Float,
    weite: Float,
    farbeA: Color,
    farbeB: Color,
    farbeC: Color
) {
    // Dieselbe Rechnung wie im Shader -- beide holen Geometrie und
    // Mittelpunkte aus `Blobquelle`, sonst laufen die Wege auseinander.
    val kante = size.minDimension
    Blobquelle.mitten(mitten, size.width / 2f, size.height / 2f, kante, zeit, weite)
    val radius = Blobquelle.radius(kante, weite)

    listOf(farbeA, farbeB, farbeC).forEachIndexed { stelle, farbe ->
        val punkt = Offset(mitten[stelle * 2], mitten[stelle * 2 + 1])
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

