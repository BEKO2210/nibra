package de.ithandwerkstuttgart.nibra.ui.bausteine

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.ithandwerkstuttgart.nibra.R
import de.ithandwerkstuttgart.nibra.ui.gestalt.Abstand
import de.ithandwerkstuttgart.nibra.ui.gestalt.Mass
import kotlin.math.PI
import kotlin.math.sin

/**
 * Die ruhige, gleitende Pegelkurve. Sie spiegelt den Pegelverlauf an der
 * Mittellinie, laeuft von links nach rechts aus und uebertreibt nichts: die
 * Kurve zeigt, dass zugehoert wird, nicht wie laut es ist.
 */
@Composable
fun Pegelkurve(
    verlauf: List<Float>,
    modifier: Modifier = Modifier,
    farbe: Color = MaterialTheme.colorScheme.primary
) {
    val beschreibung = stringResource(R.string.sw_aufnahme_pegel_beschreibung)
    val strichbreite = with(LocalDensity.current) { Abstand.winzig.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(Mass.pegelkurve)
            .semantics { contentDescription = beschreibung }
    ) {
        if (verlauf.isEmpty()) {
            zeichneRuhelinie(farbe, strichbreite)
            return@Canvas
        }
        val mitte = size.height / 2f
        val schritt = size.width / (verlauf.size - 1).coerceAtLeast(1)
        val hoehe = size.height / 2f - strichbreite

        val oben = Path()
        val unten = Path()
        verlauf.forEachIndexed { stelle, wert ->
            // Die juengsten Werte stehen rechts und schwingen am weitesten aus;
            // aeltere klingen nach links hin aus.
            val ausklang = (stelle + 1f) / verlauf.size
            val ausschlag = wert.coerceIn(0f, 1f) * hoehe * ausklang
            val x = stelle * schritt
            if (stelle == 0) {
                oben.moveTo(x, mitte - ausschlag)
                unten.moveTo(x, mitte + ausschlag)
            } else {
                oben.lineTo(x, mitte - ausschlag)
                unten.lineTo(x, mitte + ausschlag)
            }
        }
        drawPath(oben, farbe, style = Stroke(width = strichbreite))
        drawPath(unten, farbe.copy(alpha = 0.5f), style = Stroke(width = strichbreite))
    }
}

private fun DrawScope.zeichneRuhelinie(farbe: Color, strichbreite: Float) {
    val mitte = size.height / 2f
    drawLine(
        color = farbe.copy(alpha = 0.3f),
        start = Offset(0f, mitte),
        end = Offset(size.width, mitte),
        strokeWidth = strichbreite
    )
}
