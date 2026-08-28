package de.ithandwerkstuttgart.loqui.daten

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

private val Context.ablage: DataStore<Preferences> by preferencesDataStore(name = "loqui_einstellungen")

/** Was Loqui sich zwischen zwei Starts merkt. Alles lokal. */
data class GespeicherteEinstellungen(
    val stoppBeiStille: Boolean = true,
    val aufnahmenBehalten: Boolean = false,
    val eingerichtet: Boolean = false,
    /** Leer bedeutet: noch nicht gewaehlt, dann gilt die Systemsprache. */
    val diktatSprachCode: String = ""
)

@Singleton
class EinstellungenAblage @Inject constructor(
    private val context: Context
) {
    private object Schluessel {
        val stoppBeiStille = booleanPreferencesKey("stopp_bei_stille")
        val aufnahmenBehalten = booleanPreferencesKey("aufnahmen_behalten")
        val eingerichtet = booleanPreferencesKey("eingerichtet")
        val diktatSprachCode = stringPreferencesKey("diktat_sprach_code")
    }

    val fluss: Flow<GespeicherteEinstellungen> = context.ablage.data.map { werte ->
        GespeicherteEinstellungen(
            stoppBeiStille = werte[Schluessel.stoppBeiStille] ?: true,
            aufnahmenBehalten = werte[Schluessel.aufnahmenBehalten] ?: false,
            eingerichtet = werte[Schluessel.eingerichtet] ?: false,
            diktatSprachCode = werte[Schluessel.diktatSprachCode] ?: ""
        )
    }

    suspend fun setzeStoppBeiStille(an: Boolean) =
        schreibe { it[Schluessel.stoppBeiStille] = an }

    suspend fun setzeAufnahmenBehalten(an: Boolean) =
        schreibe { it[Schluessel.aufnahmenBehalten] = an }

    suspend fun setzeEingerichtet(fertig: Boolean) =
        schreibe { it[Schluessel.eingerichtet] = fertig }

    suspend fun setzeDiktatSprache(code: String) =
        schreibe { it[Schluessel.diktatSprachCode] = code }

    private suspend fun schreibe(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.ablage.edit(block)
    }
}
