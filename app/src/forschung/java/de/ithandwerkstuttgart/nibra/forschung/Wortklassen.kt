package de.ithandwerkstuttgart.nibra.forschung

/**
 * Die Wortklassen für die Auswertung des Mikrofonvergleichs.
 *
 * **Hier stehen keine Prüfsätze mehr.** Sie standen einmal hier, wortgleich
 * neben [Testfall.VOLL] -- eine dritte Kopie desselben Korpus, nachdem
 * bereits zwei auseinandergelaufen waren und zwanzig Minuten Vorlesen
 * gekostet hatten. Die Prüfsätze stehen ausschließlich in [Testfall].
 *
 * Was hier bleibt, ist die Einteilung der Wörter -- eine andere Sache als
 * die Sätze selbst. Sie ist bewusst von Hand gepflegt: welches Wort ein
 * Eigenname ist, lässt sich nicht aus dem Satz ableiten, und eine falsche
 * Ableitung wäre schlimmer als eine sichtbare Liste.
 */
object Wortklassen {

    /**
     * Eigennamen, Firmen und Zahlwörter aus den Prüfsätzen.
     *
     * Ortsnamen zählen zu den Eigennamen: für den Nutzer ist „Freiberg"
     * genauso ein Name wie „Weinreich", und beide scheitern an denselben
     * Stellen.
     */
    val FUER_MIKROFON: Map<String, Fehlerarten.Klasse> = Fehlerarten.klassenAus(
        eigennamen = listOf(
            "Belkis", "Aslani", "Weinreich", "Freiberg", "Neckar"
        ),
        fachbegriffe = listOf(
            "Nibra", "audiotechnik", "Backup", "Deployment", "Meeting",
            "Konferenzraum", "Spracherkennung", "Montagmorgen"
        ),
        zahlen = listOf(
            "vierzehn", "dreißig", "drei", "dritten", "zwölften", "Oktober",
            "zweitausendsechsundzwanzig", "zweihundertvierzig", "achthundert",
            "siebzehn", "fünfundvierzig"
        )
    )
}
