package de.ithandwerkstuttgart.loqui.ui.gestalt

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Markenschriften: Fraunces fuer Titel, Inter fuer Text.
 *
 * Die Schriftdateien liegen noch nicht unter `res/font`. Bis sie dort liegen,
 * traegt die Formsprache: ein weicher Serif fuer Titel, eine ruhige Grotesk im
 * Text. Sobald `fraunces.ttf` und `inter.ttf` in `res/font` liegen, werden hier
 * nur die beiden Familien ausgetauscht:
 *
 *     val titel = FontFamily(Font(R.font.fraunces, FontWeight.SemiBold), ...)
 *     val text  = FontFamily(Font(R.font.inter, FontWeight.Normal), ...)
 *
 * Kein Bildschirm aendert sich dabei — alle greifen ueber die Typografie-Rollen
 * auf die Schriften zu.
 */
object Schriften {
    /** Titelschrift der Marke (Fraunces; bis dahin der System-Serif). */
    val titel: FontFamily = FontFamily.Serif

    /** Textschrift der Marke (Inter; bis dahin die System-Grotesk). */
    val text: FontFamily = FontFamily.SansSerif
}

private fun titelStil(groesse: Int, zeile: Int, gewicht: FontWeight, laufweite: Double = 0.0) = TextStyle(
    fontFamily = Schriften.titel,
    fontWeight = gewicht,
    fontSize = groesse.sp,
    lineHeight = zeile.sp,
    letterSpacing = laufweite.sp
)

private fun textStil(groesse: Int, zeile: Int, gewicht: FontWeight, laufweite: Double = 0.0) = TextStyle(
    fontFamily = Schriften.text,
    fontWeight = gewicht,
    fontSize = groesse.sp,
    lineHeight = zeile.sp,
    letterSpacing = laufweite.sp
)

/**
 * Benannte Typografie-Rollen. Bildschirme greifen ausschliesslich ueber
 * `MaterialTheme.typography` darauf zu und bauen keine eigenen TextStyle-Werte.
 *
 * - display: die grossen, ruhigen Aussagen (Einfuehrung, Leerzustaende)
 * - title:   Kopfzeilen und Kachel-Ueberschriften
 * - body:    Fliesstext und Diktattexte
 * - label:   Schaltflaechen, Marken, Zeitangaben
 */
val LoquiTypografie = Typography(
    displayLarge = titelStil(44, 52, FontWeight.SemiBold, -0.5),
    displayMedium = titelStil(36, 44, FontWeight.SemiBold, -0.4),
    displaySmall = titelStil(30, 38, FontWeight.SemiBold, -0.3),

    headlineLarge = titelStil(28, 36, FontWeight.SemiBold),
    headlineMedium = titelStil(24, 32, FontWeight.SemiBold),
    headlineSmall = titelStil(21, 28, FontWeight.SemiBold),

    titleLarge = titelStil(20, 26, FontWeight.SemiBold),
    titleMedium = textStil(17, 24, FontWeight.SemiBold),
    titleSmall = textStil(15, 20, FontWeight.SemiBold),

    bodyLarge = textStil(17, 26, FontWeight.Normal),
    bodyMedium = textStil(15, 23, FontWeight.Normal),
    bodySmall = textStil(13, 19, FontWeight.Normal),

    labelLarge = textStil(15, 20, FontWeight.SemiBold, 0.1),
    labelMedium = textStil(13, 18, FontWeight.Medium, 0.2),
    labelSmall = textStil(12, 16, FontWeight.Medium, 0.4)
)
