package de.ithandwerkstuttgart.nibra.erkennung

/**
 * Eine Lesart dessen, was gesprochen wurde.
 *
 * Erkenner liefern in der Regel mehrere Vorschlaege, nach Wahrscheinlichkeit
 * geordnet. Nibra hat davon bisher nur den ersten genommen und den Rest
 * verworfen -- samt der Angabe, wie sicher der Erkenner war.
 *
 * @param konfidenz 0f bis 1f, oder **`null`**, wenn das Geraet keine Angabe
 *        macht. `null` heisst "unbekannt" und darf nie durch einen
 *        geschaetzten Wert ersetzt werden: eine erfundene Sicherheit ist
 *        schlimmer als gar keine, weil sie Entscheidungen traegt, die auf
 *        nichts beruhen.
 */
data class Lesart(
    val text: String,
    val konfidenz: Float?
)

/**
 * Das Ergebnis einer Erkennung: die beste Lesart und, soweit vorhanden, die
 * Alternativen.
 *
 * Die Alternativen werden heute noch nicht ausgewertet. Sie sind die
 * Grundlage fuer das, was spaeter kommen soll -- eine zweite Meinung
 * einzuholen, wenn der Erkenner unsicher ist, oder einen Eigennamen gegen
 * ein persoenliches Woerterbuch zu pruefen. Ohne sie waere beides nicht
 * moeglich, und sie sind nachtraeglich nicht zu beschaffen.
 */
data class Erkennungsergebnis(
    val lesarten: List<Lesart>
) {
    /** Die wahrscheinlichste Lesart. */
    val text: String get() = lesarten.firstOrNull()?.text.orEmpty()

    /** Die Sicherheit der besten Lesart, oder `null`, wenn unbekannt. */
    val konfidenz: Float? get() = lesarten.firstOrNull()?.konfidenz

    /** Wahr, wenn das Geraet ueberhaupt keine Sicherheit gemeldet hat. */
    val konfidenzUnbekannt: Boolean get() = lesarten.all { it.konfidenz == null }

    companion object {
        /**
         * Baut das Ergebnis aus den beiden Feldern, die
         * `SpeechRecognizer` liefert.
         *
         * Die Sicherheiten kommen als eigenes Feld und sind der Textliste
         * **nach Stellung** zugeordnet -- es gibt keine Verknuepfung ausser
         * dem Index. Sind es weniger als Texte, gilt der Rest als unbekannt.
         * Das ist der Regelfall: die meisten Geraete liefern das Feld gar
         * nicht.
         */
        fun aus(texte: List<String>?, sicherheiten: FloatArray?): Erkennungsergebnis {
            val sauber = texte.orEmpty().filter { it.isNotBlank() }
            return Erkennungsergebnis(
                sauber.mapIndexed { stelle, text ->
                    Lesart(
                        text = text,
                        konfidenz = sicherheiten
                            ?.getOrNull(stelle)
                            ?.takeIf { it in 0f..1f }
                    )
                }
            )
        }
    }
}
