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
}
