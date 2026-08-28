package de.ithandwerkstuttgart.nibra.ui.gestalt

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.ithandwerkstuttgart.nibra.R

/**
 * Markenschriften: Fraunces für Titel, Inter für Text (`marke.json`).
 *
 * Die Dateien unter `res/font` sind keine vollständigen Schriften, sondern
 * genau die vier Schnitte, die diese Datei unten anspricht -- aus den
 * variablen Originalen fest eingestellt und auf die Zeichen der sieben
 * Oberflächensprachen beschnitten. Zusammen 203 KB statt 1,2 MB.
 *
 * Wer einen weiteren Schnitt braucht, stellt ihn ebenso ein, statt hier auf
 * `FontWeight` auszuweichen: Android verzerrt fehlende Schnitte sonst selbst,
 * und verzerrte Schrift ist auf einem 412-px-Bildschirm sofort zu sehen.
 *
 * Beide Schriften stehen unter der SIL Open Font License 1.1 -- Nachweis in
 * `FREMDSOFTWARE.md` und im Bildschirm "Verwendete Fremdsoftware".
 */
object Schriften {
    /** Titelschrift der Marke: Fraunces, SOFT 30, WONK 0, opsz 28. */
    val titel: FontFamily = FontFamily(
        Font(R.font.fraunces_semibold, FontWeight.SemiBold)
    )

    /** Textschrift der Marke: Inter, opsz 14, in drei Stärken. */
    val text: FontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold)
    )
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
 * Benannte Typografie-Rollen. Bildschirme greifen ausschließlich über
 * `MaterialTheme.typography` darauf zu und bauen keine eigenen TextStyle-Werte.
 *
 * - display: die großen, ruhigen Aussagen (Einführung, Leerzustände)
 * - title:   Kopfzeilen und Kachel-Überschriften
 * - body:    Fließtext und Diktattexte
 * - label:   Schaltflächen, Marken, Zeitangaben
 */
val NibraTypografie = Typography(
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
