package de.ithandwerkstuttgart.loqui.ui.gestalt

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Die Markenpalette von Loqui: warmes Moosgruen auf viel Ruheflaeche.
 * Kein dynamisches Farbschema — die App sieht auf jedem Geraet gleich aus.
 */
object Farben {
    val akzentHell = Color(0xFF2F6F63)
    val akzentDunkel = Color(0xFF7FD1BE)

    // Helle Oberflaeche: papierartig warm, nicht reinweiss.
    private val papier = Color(0xFFF6F3EE)
    private val papierErhoben = Color(0xFFFFFFFF)
    private val papierGetoent = Color(0xFFEBE6DD)
    private val tinte = Color(0xFF1C221F)
    private val tinteLeise = Color(0xFF5A625E)
    // `linie` umrandet Eingabefelder (Material setzt `outline` als Feldrand).
    // Diese Umrandung macht das Feld ueberhaupt erst als Feld erkennbar und
    // braucht darum 3:1 gegen Karte und Flaeche (WCAG 1.4.11). Der frueher
    // hier stehende Wert 0xFFCFC8BC kam auf 1,66:1.
    // Gemessen: 3,55:1 gegen die weisse Karte, 3,21:1 gegen das Papier.
    private val linie = Color(0xFF8F8779)

    // `linieLeise` trennt nur Zeilen voneinander und benennt kein
    // Bedienelement -- rein schmueckend und darum von 1.4.11 ausgenommen.
    private val linieLeise = Color(0xFFE2DCD2)

    // Dunkle Oberflaeche: tiefes, entsaettigtes Gruengrau.
    private val nacht = Color(0xFF111513)
    private val nachtErhoben = Color(0xFF181D1B)
    private val nachtGetoent = Color(0xFF222927)
    private val kreide = Color(0xFFE9EEEB)
    private val kreideLeise = Color(0xFFA5AFAB)
    // Gleiche Begruendung wie bei `linie`: Feldrand, darum 3:1.
    // Der frueher hier stehende Wert 0xFF39423F kam auf 1,65:1.
    // Gemessen: 3,07:1 gegen die Karte, 3,31:1 gegen die Flaeche.
    private val linieNacht = Color(0xFF5E6B67)
    private val linieNachtLeise = Color(0xFF2A312F)

    val hell = lightColorScheme(
        primary = akzentHell,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD8E7E2),
        onPrimaryContainer = Color(0xFF10302A),
        secondary = Color(0xFF4C635C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCE6E2),
        onSecondaryContainer = Color(0xFF16241F),
        tertiary = Color(0xFF7A6A4F),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEDE2CE),
        onTertiaryContainer = Color(0xFF281C0A),
        error = Color(0xFF8C3A2E),
        onError = Color.White,
        errorContainer = Color(0xFFF6DAD4),
        onErrorContainer = Color(0xFF3A0F09),
        background = papier,
        onBackground = tinte,
        surface = papierErhoben,
        onSurface = tinte,
        surfaceVariant = papierGetoent,
        onSurfaceVariant = tinteLeise,
        outline = linie,
        outlineVariant = linieLeise,
        inverseSurface = tinte,
        inverseOnSurface = papier,
        inversePrimary = akzentDunkel,
        surfaceTint = akzentHell,
        scrim = Color(0xFF000000)
    )

    val dunkel = darkColorScheme(
        primary = akzentDunkel,
        onPrimary = Color(0xFF0A2620),
        primaryContainer = Color(0xFF234139),
        onPrimaryContainer = Color(0xFFCCEDE4),
        secondary = Color(0xFFB2C6C0),
        onSecondary = Color(0xFF1C332D),
        secondaryContainer = Color(0xFF2A3B36),
        onSecondaryContainer = Color(0xFFD6E5E0),
        tertiary = Color(0xFFD9C6A4),
        onTertiary = Color(0xFF2C2110),
        tertiaryContainer = Color(0xFF443722),
        onTertiaryContainer = Color(0xFFF2E4CC),
        error = Color(0xFFF0B4A6),
        onError = Color(0xFF48120A),
        errorContainer = Color(0xFF66261B),
        onErrorContainer = Color(0xFFFBDBD3),
        background = nacht,
        onBackground = kreide,
        surface = nachtErhoben,
        onSurface = kreide,
        surfaceVariant = nachtGetoent,
        onSurfaceVariant = kreideLeise,
        outline = linieNacht,
        outlineVariant = linieNachtLeise,
        inverseSurface = kreide,
        inverseOnSurface = nacht,
        inversePrimary = akzentHell,
        surfaceTint = akzentDunkel,
        scrim = Color(0xFF000000)
    )
}
