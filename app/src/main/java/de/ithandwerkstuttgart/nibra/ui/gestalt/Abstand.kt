package de.ithandwerkstuttgart.nibra.ui.gestalt

import androidx.compose.ui.unit.dp

/**
 * Die einzige Abstandsskala der App. Jeder Rand, jeder Zwischenraum und jede
 * Kachel-Polsterung kommt aus dieser Liste — nirgends im Bildschirm-Code
 * stehen freie dp-Werte.
 */
object Abstand {
    /** 4dp — Haarabstand zwischen zusammengehörenden Zeilen. */
    val winzig = 4.dp

    /** 8dp — Abstand innerhalb einer Gruppe. */
    val klein = 8.dp

    /** 12dp — Abstand zwischen Symbol und Beschriftung. */
    val schmal = 12.dp

    /** 16dp — Grundpolsterung jeder Kachel und jedes Bildschirmrands. */
    val normal = 16.dp

    /** 24dp — Abstand zwischen Gruppen. */
    val weit = 24.dp

    /** 32dp — Ruhefläche um die Aufnahmefläche und über Titeln. */
    val gross = 32.dp
}

/**
 * Feste Größen, die sich aus der Abstandsskala ableiten. Sie stehen hier,
 * damit kein Bildschirm eigene Maße erfindet.
 */
object Mass {
    /** Kleinstes antippbares Feld (Android-Mindestmaß). */
    val tippziel = 48.dp

    /** Kantenlänge der Symbole in Listen und Kopfzeilen. */
    val symbol = 24.dp

    /** Kantenlänge der großen Symbole in Leerzuständen und Einführung. */
    val symbolGross = 48.dp

    /** Das Markenzeichen, wenn es für sich steht (Einführung, Markenfuß). */
    val zeichen = 72.dp

    /** Durchmesser der mittigen Aufnahmefläche. */
    val aufnahmeflaeche = 200.dp

    /** Höhe der laufenden Pegelkurve im Aufnahme-Blatt. */
    val pegelkurve = 96.dp
}
