package de.ithandwerkstuttgart.nibra.ui.gestalt

import androidx.compose.ui.unit.dp

/**
 * Die einzige Abstandsskala der App. Jeder Rand, jeder Zwischenraum und jede
 * Kachel-Polsterung kommt aus dieser Liste — nirgends im Bildschirm-Code
 * stehen freie dp-Werte.
 */
object Abstand {
    /** 4dp — Haarabstand zwischen zusammengehoerenden Zeilen. */
    val winzig = 4.dp

    /** 8dp — Abstand innerhalb einer Gruppe. */
    val klein = 8.dp

    /** 12dp — Abstand zwischen Symbol und Beschriftung. */
    val schmal = 12.dp

    /** 16dp — Grundpolsterung jeder Kachel und jedes Bildschirmrands. */
    val normal = 16.dp

    /** 24dp — Abstand zwischen Gruppen. */
    val weit = 24.dp

    /** 32dp — Ruheflaeche um die Aufnahmeflaeche und ueber Titeln. */
    val gross = 32.dp
}

/**
 * Feste Groessen, die sich aus der Abstandsskala ableiten. Sie stehen hier,
 * damit kein Bildschirm eigene Masse erfindet.
 */
object Mass {
    /** Kleinstes antippbares Feld (Android-Mindestmass). */
    val tippziel = 48.dp

    /** Kantenlaenge der Symbole in Listen und Kopfzeilen. */
    val symbol = 24.dp

    /** Kantenlaenge der grossen Symbole in Leerzustaenden und Einfuehrung. */
    val symbolGross = 48.dp

    /** Das Markenzeichen, wenn es fuer sich steht (Einfuehrung, Markenfuss). */
    val zeichen = 72.dp

    /** Durchmesser der mittigen Aufnahmeflaeche. */
    val aufnahmeflaeche = 200.dp

    /** Hoehe der laufenden Pegelkurve im Aufnahme-Blatt. */
    val pegelkurve = 96.dp
}
