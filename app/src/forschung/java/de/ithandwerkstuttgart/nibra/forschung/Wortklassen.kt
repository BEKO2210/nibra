package de.ithandwerkstuttgart.nibra.forschung

/**
 * Die Wortklassen für den Mikrofonvergleich, an **einer** Stelle.
 *
 * Vorher standen sie in der Activity und wurden bei jedem neuen Versuch
 * abgeschrieben. Zwei Listen, die dasselbe meinen sollen, laufen
 * auseinander -- und dann vergleicht man Trefferquoten, die verschiedene
 * Wörter zählen.
 */
object Wortklassen {

    /** Eigennamen, Firmen und Zahlwörter aus den Prüfsätzen. */
    val FUER_MIKROFON: Map<String, Fehlerarten.Klasse> = Fehlerarten.klassenAus(
        eigennamen = listOf("Belkis", "Aslani", "Weinreich"),
        fachbegriffe = listOf(
            "Nibra", "audiotechnik", "Backup", "Deployment", "Meeting",
            "Konferenzraum", "Spracherkennung"
        ),
        zahlen = listOf(
            "vierzehn", "dreißig", "drei", "dritten", "zwölften", "Oktober",
            "zweitausendsechsundzwanzig", "zweihundertvierzig", "achthundert",
            "siebzehn", "fünfundvierzig"
        )
    )

    /**
     * Die zwölf Prüfsätze.
     *
     * Sie decken ab, was beim Diktieren wirklich vorkommt und wo Erkenner
     * scheitern: ein schneller Beginn, eine kurze und eine lange Denkpause,
     * Eigennamen, eine Firma mit Kürzel, Zahlen, Uhrzeit, Datum, ein
     * englischer Fachbegriff mitten im Deutschen und ein langer Satz, bei
     * dem der Anfang verloren gehen kann.
     */
    val PRUEFSAETZE = listOf(
        "Guten Morgen, ich fasse die Besprechung von gestern kurz zusammen.",
        "Sofort loslegen, wir haben wenig Zeit.",
        "Die Lieferung kommt am Freitag, nicht am Donnerstag.",
        "Ich überlege kurz. Also. Wir verschieben den Termin auf nächste Woche.",
        "Hier spricht Belkis Aslani aus Freiberg am Neckar.",
        "Bitte richte Herrn Weinreich aus, dass ich zurückrufe.",
        "Die Anlage stammt von d und b audiotechnik.",
        "Wir brauchen zweihundertvierzig Bauteile für achthundert Euro.",
        "Die Besprechung beginnt um vierzehn Uhr dreißig im Konferenzraum drei.",
        "Der Termin ist am zwölften Oktober zweitausendsechsundzwanzig.",
        "Mach bitte ein Backup, bevor das Deployment startet.",
        "Wenn die Unterlagen vollständig geprüft sind und niemand mehr " +
            "widerspricht, geben wir die Bestellung am Montagmorgen frei."
    )
}
