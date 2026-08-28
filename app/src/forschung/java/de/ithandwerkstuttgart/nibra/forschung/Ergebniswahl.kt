package de.ithandwerkstuttgart.nibra.forschung

/**
 * Welcher Text aus einem Erkennungslauf zählt -- und woher er kam.
 *
 * Eigene Klasse, weil an dieser Entscheidung an einem einzigen Tag **vier**
 * Fehlmessungen hingen. Jede hätte etwas Funktionierendes als kaputt
 * gemeldet:
 *
 * - `onResults` war leer, also galt `EXTRA_AUDIO_SOURCE` als „nicht
 *   unterstützt" -- dabei hatte der Erkenner alles verstanden und Wort für
 *   Wort in den Zwischenständen gemeldet.
 * - Alternativen wurden mit Trennstrich verkettet; der Wortvergleich zählte
 *   jede als zusätzliche Wörter und meldete 133 % Fehlerrate für einen
 *   Satz, der fast richtig war.
 *
 * Deshalb steht die Regel hier an **einer** Stelle, in reiner Form, mit
 * Golden Tests dahinter. Messcode, auf dem Architekturentscheidungen
 * beruhen, ist Produktionscode.
 */
object Ergebniswahl {

    /** Woher der beste Text stammt. Gehört in jeden Bericht. */
    enum class Herkunft { SEGMENT, ENDERGEBNIS, ZWISCHENSTAND, KEINER }

    data class Wahl(val text: String, val herkunft: Herkunft) {
        val hatText: Boolean get() = text.isNotBlank()
    }

    /**
     * Die Rangfolge: Segment, sonst Endergebnis, sonst Zwischenstand.
     *
     * @param segmente je Eintrag **die beste** Lesart eines Segments --
     *        niemals mehrere Alternativen in einer Zeichenkette. Eine
     *        Alternative ist keine zusätzlich gesprochene Wortfolge.
     * @param endergebnis die n-beste Liste des Schlussberichts, beste zuerst.
     * @param zwischenstaende in zeitlicher Reihenfolge; der letzte gilt.
     */
    fun waehle(
        segmente: List<String>,
        endergebnis: List<String>,
        zwischenstaende: List<String>
    ): Wahl {
        // Segmente aneinander, weil sie **nacheinander gesprochene**
        // Abschnitte sind -- im Gegensatz zu Alternativen, die dieselbe
        // Stelle verschieden deuten.
        val ausSegmenten = segmente.map { it.trim() }.filter { it.isNotEmpty() }
        if (ausSegmenten.isNotEmpty()) {
            return Wahl(ausSegmenten.joinToString(" "), Herkunft.SEGMENT)
        }
        endergebnis.firstOrNull { it.isNotBlank() }?.let {
            return Wahl(it.trim(), Herkunft.ENDERGEBNIS)
        }
        zwischenstaende.lastOrNull { it.isNotBlank() }?.let {
            return Wahl(it.trim(), Herkunft.ZWISCHENSTAND)
        }
        return Wahl("", Herkunft.KEINER)
    }

    /**
     * Ob der geprüfte Weg überhaupt funktioniert hat.
     *
     * **Ein leeres `onResults` ist kein Beweis für „nicht unterstützt".**
     * Kamen vorher gültige Segmente oder Zwischenstände, hat der Erkenner
     * den Strom gelesen -- und genau das war die Frage.
     */
    fun wurdeUnterstuetzt(
        segmente: List<String>,
        endergebnis: List<String>,
        zwischenstaende: List<String>
    ): Boolean = waehle(segmente, endergebnis, zwischenstaende).hatText

    /**
     * Entfernt Wiederholungen, wenn derselbe Inhalt über mehrere Rückrufe
     * kommt.
     *
     * Ein Erkenner darf ein Segment melden **und** dieselbe Stelle noch
     * einmal im Schlussbericht -- ohne Prüfung stünde der Satz zweimal auf
     * dem Bildschirm.
     */
    fun ohneWiederholung(abschnitte: List<String>): List<String> {
        val ergebnis = mutableListOf<String>()
        abschnitte.map { it.trim() }.filter { it.isNotEmpty() }.forEach { neu ->
            val vorheriger = ergebnis.lastOrNull()
            when {
                vorheriger == null -> ergebnis += neu
                // Der Erkenner verlängert oft denselben Abschnitt: erst
                // „guten Morgen", dann „guten Morgen hier spricht". Dann
                // ersetzt die längere Fassung die kürzere, statt beide
                // hintereinanderzustellen.
                neu.startsWith(vorheriger, ignoreCase = true) ->
                    ergebnis[ergebnis.lastIndex] = neu
                vorheriger.startsWith(neu, ignoreCase = true) -> Unit
                else -> ergebnis += neu
            }
        }
        return ergebnis
    }
}
