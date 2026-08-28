package de.ithandwerkstuttgart.nibra.ui.gestalt

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Die Farben der lebendigen Aufnahmeflaeche.
 *
 * Sie liegen neben dem Material-Farbschema, weil Material dafuer keine
 * Rolle kennt -- und sie gehoeren trotzdem in das Thema, damit kein
 * Bildschirm sie selbst zusammenstellt.
 */
val LokaleBlobfarben = staticCompositionLocalOf { Farben.blobHell }

/**
 * Das eine Thema der App. Kein dynamisches Farbschema, keine Zweitpalette:
 * Farben, Formen und Schriftrollen kommen aus je einer Quelle.
 */
@Composable
fun NibraTheme(
    dunkel: Boolean = isSystemInDarkTheme(),
    inhalt: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LokaleBlobfarben provides if (dunkel) Farben.blobDunkel else Farben.blobHell
    ) {
        MaterialTheme(
            colorScheme = if (dunkel) Farben.dunkel else Farben.hell,
            typography = NibraTypografie,
            shapes = Formen.material,
            content = inhalt
        )
    }
}
