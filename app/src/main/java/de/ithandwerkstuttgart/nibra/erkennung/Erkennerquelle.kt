package de.ithandwerkstuttgart.nibra.erkennung

import kotlinx.coroutines.flow.Flow

/**
 * Quelle erkannter Sprache. Der Betrieb nutzt [Spracherkenner]; Tests
 * setzen eine eigene Quelle ein, weil sich echtes Sprechen nicht
 * nachstellen laesst.
 */
interface Erkennerquelle {
    fun erkenne(sprachCode: String, stoppBeiStille: Boolean): Flow<Erkennungsereignis>

    /** Beendet die Aufnahme und laesst das Gesprochene noch auswerten. */
    fun stoppen()

    /**
     * Gibt gehaltene Mittel frei -- aufzurufen, wenn das Diktat wirklich
     * zu Ende ist, nicht zwischen zwei Saetzen.
     *
     * Der Betriebserkenner haelt seine Bindung an den Systemdienst ueber
     * einzelne Saetze hinweg, damit dazwischen keine Luecke entsteht. Ohne
     * diesen Aufruf bliebe die Bindung bestehen, obwohl niemand sie braucht.
     */
    fun gibFrei() = Unit
}
