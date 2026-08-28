package de.ithandwerkstuttgart.nibra.ui.gestalt

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Die einzige Formskala der App. Bildschirme greifen über
 * `MaterialTheme.shapes` darauf zu und setzen keine eigenen
 * RoundedCornerShape-Werte.
 */
object Formen {
    /** 8dp — Marken, Zähler, kleine Schaltflächen. */
    val klein = RoundedCornerShape(8.dp)

    /** 12dp — Eingabefelder und Listenzeilen. */
    val schmal = RoundedCornerShape(12.dp)

    /** 16dp — Kacheln und Karten. */
    val normal = RoundedCornerShape(16.dp)

    /** 24dp — Blätter und große Flächen. */
    val weit = RoundedCornerShape(24.dp)

    /** 32dp — oberer Rand des Aufnahme-Blatts. */
    val gross = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    val material = Shapes(
        extraSmall = klein,
        small = schmal,
        medium = normal,
        large = weit,
        extraLarge = RoundedCornerShape(32.dp)
    )
}
