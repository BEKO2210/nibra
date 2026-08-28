package de.ithandwerkstuttgart.nibra.ui.bildschirme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.ithandwerkstuttgart.nibra.R
import de.ithandwerkstuttgart.nibra.ui.bausteine.Abschnittstitel
import de.ithandwerkstuttgart.nibra.ui.bausteine.Kachel
import de.ithandwerkstuttgart.nibra.ui.bausteine.Kopfzeile
import de.ithandwerkstuttgart.nibra.ui.bausteine.Leerzustand
import de.ithandwerkstuttgart.nibra.ui.bausteine.Symbol
import de.ithandwerkstuttgart.nibra.ui.gestalt.Abstand
import de.ithandwerkstuttgart.nibra.ui.gestalt.NibraTheme
import de.ithandwerkstuttgart.nibra.ui.modell.Diktatsprache

/**
 * Die Wahl der Diktatsprache. Zuletzt genutzte Sprachen stehen oben; bei jeder
 * Sprache steht, ob das Gerät sie ohne Netz erkennen kann.
 */
@Composable
fun DiktatspracheBildschirm(
    sprachen: List<Diktatsprache>,
    gewaehlterCode: String,
    /** Wahr, solange das Gerät nach seinen Sprachen gefragt wird. */
    laedt: Boolean = false,
    aufSprache: (Diktatsprache) -> Unit,
    aufZurueck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuletzt = sprachen.filter { it.zuletztGenutzt }
    val uebrige = sprachen.filterNot { it.zuletztGenutzt }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { Kopfzeile(titel = R.string.sw_sprache_titel, aufZurueck = aufZurueck) }
    ) { raender ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(raender)
                .padding(horizontal = Abstand.normal)
        ) {
            if (sprachen.isEmpty() && laedt) {
                // Warten heißt warten -- und nicht "keine Sprache gefunden".
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.sw_sprache_laedt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Abstand.normal)
                    )
                }
            } else if (sprachen.isEmpty()) {
                Leerzustand(
                    zeichnung = R.drawable.nb_ic_sprache,
                    titel = R.string.sw_sprache_leer_titel,
                    text = R.string.sw_sprache_leer_text
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Abstand.gross),
                    verticalArrangement = Arrangement.spacedBy(Abstand.klein)
                ) {
                    if (zuletzt.isNotEmpty()) {
                        item(key = "gruppe-zuletzt") {
                            Abschnittstitel(titel = R.string.sw_sprache_gruppe_zuletzt)
                        }
                        items(zuletzt, key = { "zuletzt-" + it.code }) { sprache ->
                            Sprachzeile(
                                sprache = sprache,
                                gewaehlt = sprache.code == gewaehlterCode,
                                aufTippen = { aufSprache(sprache) }
                            )
                        }
                    }
                    item(key = "gruppe-alle") {
                        Abschnittstitel(titel = R.string.sw_sprache_gruppe_alle)
                    }
                    items(uebrige, key = { "alle-" + it.code }) { sprache ->
                        Sprachzeile(
                            sprache = sprache,
                            gewaehlt = sprache.code == gewaehlterCode,
                            aufTippen = { aufSprache(sprache) }
                        )
                    }
                    item(key = "hinweis") {
                        Text(
                            text = stringResource(R.string.sw_sprache_hinweis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Abstand.normal)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Sprachzeile(
    sprache: Diktatsprache,
    gewaehlt: Boolean,
    aufTippen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Kachel(modifier = modifier, aufTippen = aufTippen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sprache.eigenName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = sprache.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Abstand.winzig)
                )
                // Drei Fälle, nicht zwei. „Nicht auf dem Gerät" darf nur
                // dastehen, wenn das Gerät das auch gesagt hat.
                Text(
                    text = stringResource(
                        when {
                            !sprache.verfuegbarkeitBekannt ->
                                R.string.sw_sprache_unbekannt
                            sprache.aufGeraetVerfuegbar -> R.string.sw_sprache_auf_geraet
                            else -> R.string.sw_sprache_nicht_auf_geraet
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        !sprache.verfuegbarkeitBekannt ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                        sprache.aufGeraetVerfuegbar -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = Abstand.winzig)
                )
            }
            if (gewaehlt) {
                Symbol(
                    zeichnung = R.drawable.nb_ic_haken,
                    beschreibung = R.string.sw_sprache_gewaehlt,
                    farbe = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(name = "Diktatsprache", showBackground = true)
@Composable
private fun VorschauSprache() {
    NibraTheme {
        DiktatspracheBildschirm(
            sprachen = listOf(
                Diktatsprache("de-DE", "Deutsch", "Deutsch", true, zuletztGenutzt = true),
                Diktatsprache("en-US", "Englisch", "English", true),
                Diktatsprache("tr-TR", "Tuerkisch", "Turkce", false)
            ),
            gewaehlterCode = "de-DE",
            aufSprache = {},
            aufZurueck = {}
        )
    }
}

@Preview(name = "Diktatsprache leer", showBackground = true)
@Composable
private fun VorschauSpracheLeer() {
    NibraTheme {
        DiktatspracheBildschirm(
            sprachen = emptyList(),
            gewaehlterCode = "",
            aufSprache = {},
            aufZurueck = {}
        )
    }
}
