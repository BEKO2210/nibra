package de.ithandwerkstuttgart.loqui.ui.bildschirme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import de.ithandwerkstuttgart.loqui.R
import de.ithandwerkstuttgart.loqui.ui.bausteine.Kachel
import de.ithandwerkstuttgart.loqui.ui.bausteine.Kopfzeile
import de.ithandwerkstuttgart.loqui.ui.bausteine.Leerzustand
import de.ithandwerkstuttgart.loqui.ui.bausteine.Symbolknopf
import de.ithandwerkstuttgart.loqui.ui.gestalt.Abstand
import de.ithandwerkstuttgart.loqui.ui.gestalt.LoquiTheme
import de.ithandwerkstuttgart.loqui.ui.modell.Textbaustein

/**
 * Textbausteine: eigene Ersetzungen, die beim Diktieren sofort greifen.
 * Anlegen, Bearbeiten und Loeschen geschehen in einem einzigen, ruhigen Blatt.
 */
@Composable
fun TextbausteineBildschirm(
    bausteine: List<Textbaustein>,
    aufSichern: (Textbaustein) -> Unit,
    aufLoeschen: (Textbaustein) -> Unit,
    aufZurueck: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Der gerade bearbeitete Baustein; null heisst: kein Blatt offen.
    var bearbeitung by remember { mutableStateOf<Textbaustein?>(null) }
    var blattOffen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Kopfzeile(titel = R.string.sw_bausteine_titel, aufZurueck = aufZurueck) {
                Symbolknopf(
                    zeichnung = R.drawable.lq_ic_hinzufuegen,
                    beschreibung = R.string.sw_bausteine_neu,
                    aufTippen = {
                        bearbeitung = null
                        blattOffen = true
                    }
                )
            }
        }
    ) { raender ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(raender)
                .padding(horizontal = Abstand.normal)
        ) {
            if (bausteine.isEmpty()) {
                Leerzustand(
                    zeichnung = R.drawable.lq_ic_baustein,
                    titel = R.string.sw_bausteine_leer_titel,
                    text = R.string.sw_bausteine_leer_text,
                    handlung = R.string.sw_bausteine_leer_handlung,
                    aufHandlung = {
                        bearbeitung = null
                        blattOffen = true
                    }
                )
            } else {
                Text(
                    text = stringResource(R.string.sw_bausteine_hinweis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Abstand.schmal)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Abstand.gross),
                    verticalArrangement = Arrangement.spacedBy(Abstand.klein)
                ) {
                    items(bausteine, key = { it.id }) { baustein ->
                        Bausteinzeile(
                            baustein = baustein,
                            aufBearbeiten = {
                                bearbeitung = baustein
                                blattOffen = true
                            },
                            aufLoeschen = { aufLoeschen(baustein) }
                        )
                    }
                }
            }
        }
    }

    if (blattOffen) {
        BausteinBlatt(
            vorlage = bearbeitung,
            aufSichern = { gesichert ->
                aufSichern(gesichert)
                blattOffen = false
                bearbeitung = null
            },
            aufAbbrechen = {
                blattOffen = false
                bearbeitung = null
            }
        )
    }
}

@Composable
private fun Bausteinzeile(
    baustein: Textbaustein,
    aufBearbeiten: () -> Unit,
    aufLoeschen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Kachel(modifier = modifier, aufTippen = aufBearbeiten) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = baustein.kuerzel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = baustein.ersatz,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Abstand.winzig)
                )
            }
            Symbolknopf(
                zeichnung = R.drawable.lq_ic_bearbeiten,
                beschreibung = R.string.sw_bausteine_eintrag_bearbeiten,
                aufTippen = aufBearbeiten,
                farbe = MaterialTheme.colorScheme.primary
            )
            Symbolknopf(
                zeichnung = R.drawable.lq_ic_loeschen,
                beschreibung = R.string.sw_bausteine_eintrag_loeschen,
                aufTippen = aufLoeschen,
                farbe = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Das Blatt zum Anlegen und Bearbeiten. Leer heisst: ein neuer Baustein. */
@Composable
private fun BausteinBlatt(
    vorlage: Textbaustein?,
    aufSichern: (Textbaustein) -> Unit,
    aufAbbrechen: () -> Unit
) {
    var kuerzel by rememberSaveable(vorlage?.id) { mutableStateOf(vorlage?.kuerzel.orEmpty()) }
    var ersatz by rememberSaveable(vorlage?.id) { mutableStateOf(vorlage?.ersatz.orEmpty()) }
    val vollstaendig = kuerzel.isNotBlank() && ersatz.isNotBlank()

    AlertDialog(
        onDismissRequest = aufAbbrechen,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                text = stringResource(
                    if (vorlage == null) R.string.sw_bausteine_anlegen_titel
                    else R.string.sw_bausteine_bearbeiten_titel
                ),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = kuerzel,
                    onValueChange = { kuerzel = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = stringResource(R.string.sw_bausteine_kuerzel),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
                OutlinedTextField(
                    value = ersatz,
                    onValueChange = { ersatz = it },
                    shape = MaterialTheme.shapes.small,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Abstand.schmal),
                    label = {
                        Text(
                            text = stringResource(R.string.sw_bausteine_ersatz),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
                Text(
                    text = stringResource(R.string.sw_bausteine_hinweis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Abstand.schmal)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    aufSichern(
                        Textbaustein(
                            id = vorlage?.id.orEmpty(),
                            kuerzel = kuerzel.trim(),
                            ersatz = ersatz.trim()
                        )
                    )
                },
                enabled = vollstaendig
            ) {
                Text(
                    text = stringResource(R.string.sw_sichern),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = aufAbbrechen) {
                Text(
                    text = stringResource(R.string.sw_abbrechen),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}

@Preview(name = "Textbausteine leer", showBackground = true)
@Composable
private fun VorschauBausteineLeer() {
    LoquiTheme {
        TextbausteineBildschirm(
            bausteine = emptyList(),
            aufSichern = {},
            aufLoeschen = {},
            aufZurueck = {}
        )
    }
}

@Preview(name = "Textbausteine gefuellt", showBackground = true)
@Composable
private fun VorschauBausteine() {
    LoquiTheme {
        TextbausteineBildschirm(
            bausteine = listOf(
                Textbaustein("1", "mfg", "Mit freundlichen Gruessen, Belkis"),
                Textbaustein("2", "adr", "Loqui, Postfach 12, 10115 Berlin")
            ),
            aufSichern = {},
            aufLoeschen = {},
            aufZurueck = {}
        )
    }
}
