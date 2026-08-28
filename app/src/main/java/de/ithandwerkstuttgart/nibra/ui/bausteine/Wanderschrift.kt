package de.ithandwerkstuttgart.nibra.ui.bausteine

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle

/**
 * Ein Wort, durch das eine Welle laeuft: jeder Buchstabe hellt kurz auf und
 * hebt sich leicht, dann der naechste.
 *
 * Gedacht fuer das Warten -- sie sagt "es geht weiter", ohne einen Fortschritt
 * vorzutaeuschen, den Nibra nicht kennt. Anders als ein Kreisel hat sie einen
 * Anfang und ein Ende und laesst sich lesen.
 *
 * Fuer die Sprachausgabe bleibt es **ein** Text: die einzelnen Buchstaben
 * werden ausgeblendet, sonst buchstabiert der Vorleser das Wort.
 */
@Composable
fun Wanderschrift(
    text: String,
    modifier: Modifier = Modifier,
    farbe: Color = MaterialTheme.colorScheme.onBackground,
    stil: TextStyle = LocalTextStyle.current
) {
    val takt = rememberInfiniteTransition(label = "wanderschrift")
    val zeichen = text.toList()
    // Ein voller Durchlauf, plus eine Ruhepause am Ende, damit die Welle
    // nicht hetzt.
    val gesamt = zeichen.size * SCHRITT_MILLIS + RUHE_MILLIS

    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = text
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        zeichen.forEachIndexed { stelle, buchstabe ->
            val beginn = stelle * SCHRITT_MILLIS
            val welle by takt.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = gesamt
                        0f at beginn using LinearEasing
                        1f at (beginn + SCHRITT_MILLIS) using LinearEasing
                        0f at (beginn + SCHRITT_MILLIS * 3).coerceAtMost(gesamt)
                    }
                ),
                label = "buchstabe$stelle"
            )
            Text(
                text = buchstabe.toString(),
                style = stil,
                color = farbe,
                modifier = Modifier
                    .alpha(RUHE_DECKUNG + welle * (1f - RUHE_DECKUNG))
                    .scale(1f + welle * HEBUNG)
            )
        }
    }
}

/** Wie lange ein Buchstabe braucht, bis der naechste an der Reihe ist. */
private const val SCHRITT_MILLIS = 90

/** Pause nach einem Durchlauf. */
private const val RUHE_MILLIS = 700

/** Deckung eines Buchstabens, solange die Welle nicht bei ihm ist. */
private const val RUHE_DECKUNG = 0.35f

/** Wie weit ein Buchstabe im Scheitel der Welle waechst. */
private const val HEBUNG = 0.14f
