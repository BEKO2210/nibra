package de.ithandwerkstuttgart.loqui.ui.gestalt

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Das eine Thema der App. Kein dynamisches Farbschema, keine Zweitpalette:
 * Farben, Formen und Schriftrollen kommen aus je einer Quelle.
 */
@Composable
fun LoquiTheme(
    dunkel: Boolean = isSystemInDarkTheme(),
    inhalt: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (dunkel) Farben.dunkel else Farben.hell,
        typography = LoquiTypografie,
        shapes = Formen.material,
        content = inhalt
    )
}
