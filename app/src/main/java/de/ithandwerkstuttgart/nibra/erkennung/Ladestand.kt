package de.ithandwerkstuttgart.nibra.erkennung

/**
 * Wie weit das Nachladen eines Sprachpakets ist.
 *
 * Ein Sprachpaket zu laden ist der einzige Vorgang in Nibra, der Netz
 * braucht -- und zwar nicht den der App, sondern den von Android: die App
 * bittet den Systemdienst, das Paket zu holen. Nibra selbst hat keine
 * Netzberechtigung und bekommt die Daten nie zu sehen.
 */
sealed interface Ladestand {

    /**
     * Android hat den Auftrag angenommen, aber noch nicht begonnen -- es
     * wartet zum Beispiel auf ein besseres Netz.
     */
    data object Angestossen : Ladestand

    /** @param anteil 0 bis 100. */
    data class Laeuft(val anteil: Int) : Ladestand

    data object Fertig : Ladestand

    /**
     * @param grund der Fehlercode von Android, oder `null`, wenn dieses
     *        Gerät das Nachladen gar nicht anbietet.
     */
    data class Fehlgeschlagen(val grund: Int?) : Ladestand

    /**
     * Dieses Gerät kann Pakete nicht aus der App heraus laden -- unter
     * Android 13 gibt es die Schnittstelle nicht. Dann bleibt der Weg über
     * die Systemeinstellungen, und genau das gehört dem Nutzer gesagt.
     */
    data object NurUeberEinstellungen : Ladestand
}
