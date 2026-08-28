package de.ithandwerkstuttgart.nibra.erkennung

import de.ithandwerkstuttgart.nibra.ui.modell.Textbaustein

/**
 * Setzt die eigenen Ersetzungen im erkannten Text ein. Ersetzt wird nur ein
 * ganzes Wort, Gross- und Kleinschreibung spielen keine Rolle; laengere
 * Kuerzel gewinnen, damit "mfg" nicht Teile von "mfgx" zerlegt.
 */
fun wendeBausteineAn(text: String, bausteine: List<Textbaustein>): String {
    if (text.isBlank() || bausteine.isEmpty()) return text
    return bausteine
        .filter { it.kuerzel.isNotBlank() }
        .sortedByDescending { it.kuerzel.length }
        .fold(text) { zwischenstand, baustein ->
            val muster = Regex(
                "(?<![\\p{L}\\p{N}])" + Regex.escape(baustein.kuerzel) + "(?![\\p{L}\\p{N}])",
                RegexOption.IGNORE_CASE
            )
            muster.replace(zwischenstand) { baustein.ersatz }
        }
}
