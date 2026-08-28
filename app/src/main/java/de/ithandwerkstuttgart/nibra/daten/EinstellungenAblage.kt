package de.ithandwerkstuttgart.nibra.daten

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.standardAblage: DataStore<Preferences> by
    preferencesDataStore(name = "nibra_einstellungen")

/** Was Nibra sich zwischen zwei Starts merkt. Alles lokal. */
data class GespeicherteEinstellungen(
    val stoppBeiStille: Boolean = true,
    val eingerichtet: Boolean = false,
    /** Leer bedeutet: noch nicht gewaehlt, dann gilt die Systemsprache. */
    val diktatSprachCode: String = ""
)

/**
 * Was Nibra sich merkt, liegt in einem DataStore.
 *
 * Die Ablage wird hereingereicht statt am Context zu haengen: die
 * Erweiterung `Context.standardAblage` haelt ihren Wert im Speicher, und
 * eine Datei zu loeschen setzt das nicht zurueck. In Tests erbte dadurch
 * jeder Test die Einstellungen des vorigen. Mit dieser Naht bekommt jeder
 * Test seine eigene Ablage.
 */
@Singleton
class EinstellungenAblage(
    private val ablage: DataStore<Preferences>
) {
    /** Der Weg der App: die eine Ablage des Geraets. */
    @Inject
    constructor(context: Context) : this(context.standardAblage)

    private object Schluessel {
        val stoppBeiStille = booleanPreferencesKey("stopp_bei_stille")
        val eingerichtet = booleanPreferencesKey("eingerichtet")
        val diktatSprachCode = stringPreferencesKey("diktat_sprach_code")
    }

    val fluss: Flow<GespeicherteEinstellungen> = ablage.data.map { werte ->
        GespeicherteEinstellungen(
            stoppBeiStille = werte[Schluessel.stoppBeiStille] ?: true,
            eingerichtet = werte[Schluessel.eingerichtet] ?: false,
            diktatSprachCode = werte[Schluessel.diktatSprachCode] ?: ""
        )
    }

    suspend fun setzeStoppBeiStille(an: Boolean) =
        schreibe { it[Schluessel.stoppBeiStille] = an }


    suspend fun setzeEingerichtet(fertig: Boolean) =
        schreibe { it[Schluessel.eingerichtet] = fertig }

    suspend fun setzeDiktatSprache(code: String) =
        schreibe { it[Schluessel.diktatSprachCode] = code }

    private suspend fun schreibe(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        ablage.edit(block)
    }
}
