package de.ithandwerkstuttgart.nibra.erkennung

import kotlinx.coroutines.flow.Flow

/**
 * Quelle erkannter Sprache. Der Betrieb nutzt [Spracherkenner]; Tests
 * setzen eine eigene Quelle ein, weil sich echtes Sprechen nicht
 * nachstellen lässt.
 */
interface Erkennerquelle {
    fun erkenne(sprachCode: String, stoppBeiStille: Boolean): Flow<Erkennungsereignis>

    /** Beendet die Aufnahme und lässt das Gesprochene noch auswerten. */
    fun stoppen()

    /**
     * Gibt gehaltene Mittel frei -- aufzurufen, wenn das Diktat wirklich
     * zu Ende ist, nicht zwischen zwei Sätzen.
     *
     * Der Betriebserkenner hält seine Bindung an den Systemdienst über
     * einzelne Sätze hinweg, damit dazwischen keine Lücke entsteht. Ohne
     * diesen Aufruf bliebe die Bindung bestehen, obwohl niemand sie braucht.
     */
    fun gibFrei() = Unit

    /**
     * Bittet Android, das Sprachpaket auf das Gerät zu holen.
     *
     * Der Fluss endet mit [Ladestand.Fertig], [Ladestand.Fehlgeschlagen]
     * oder [Ladestand.NurUeberEinstellungen] -- er bleibt nie offen.
     */
    fun ladeSprachpaket(sprachCode: String): Flow<Ladestand> =
        kotlinx.coroutines.flow.flowOf(Ladestand.NurUeberEinstellungen)
}
