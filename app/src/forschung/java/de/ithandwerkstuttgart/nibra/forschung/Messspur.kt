package de.ithandwerkstuttgart.nibra.forschung

/**
 * Die Tonaufnahmen, die eine Messung verwenden darf.
 *
 * **Kein Dateiname aus einer Absicht.** Der erste Wurf nahm die Zeichenkette
 * aus dem Zusatz entgegen und prüfte sie mit einem Muster. Das ist die
 * schwächere Bauart: sie verlangt, dass die Prüfung an jeder Stelle richtig
 * ist und bei jeder Erweiterung mitgezogen wird. Ein `../` im Namen verlässt
 * das Verzeichnis, denn `File(ordner, name)` normalisiert nicht.
 *
 * Hier entscheidet die Absicht nur noch über eine **Kennung**. Die
 * Zuordnung zum Dateinamen steht im Programm. Was nicht in dieser Liste
 * steht, gibt es nicht -- ein Pfad lässt sich damit gar nicht mehr
 * hineinreichen, egal wie die Eingabe aussieht.
 */
enum class Messspur(val kennung: String, val dateiname: String) {
    DEUTSCH("de", "vorlauf.pcm"),
    ENGLISCH("en", "vorlauf-en.pcm"),
    VORGABE("vorgabe", "biasing.pcm"),
    VERGLEICH("vergleich", "vergleich.wav");

    companion object {
        val STANDARD = DEUTSCH

        /**
         * @return die Spur zur Kennung, sonst [STANDARD].
         *
         * Unbekanntes fällt still auf die Vorgabe zurück, statt zu scheitern:
         * eine Messung, die wegen eines Tippfehlers gar nicht läuft, ist
         * ärgerlicher als eine, die die gewöhnliche Aufnahme nimmt -- und
         * welche sie genommen hat, steht im Bericht.
         */
        fun ausKennung(kennung: String?): Messspur =
            entries.firstOrNull { it.kennung == kennung } ?: STANDARD
    }
}
