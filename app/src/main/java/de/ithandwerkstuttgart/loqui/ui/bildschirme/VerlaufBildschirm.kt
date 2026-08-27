package de.ithandwerkstuttgart.loqui.ui.bildschirme

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import de.ithandwerkstuttgart.loqui.R
import de.ithandwerkstuttgart.loqui.ui.bausteine.Abschnittstitel
import de.ithandwerkstuttgart.loqui.ui.bausteine.Kachel
import de.ithandwerkstuttgart.loqui.ui.bausteine.Kopfzeile
import de.ithandwerkstuttgart.loqui.ui.bausteine.Leerzustand
import de.ithandwerkstuttgart.loqui.ui.bausteine.Symbol
import de.ithandwerkstuttgart.loqui.ui.bausteine.Symbolknopf
import de.ithandwerkstuttgart.loqui.ui.gestalt.Abstand
import de.ithandwerkstuttgart.loqui.ui.gestalt.LoquiTheme
import de.ithandwerkstuttgart.loqui.ui.modell.Diktat
import de.ithandwerkstuttgart.loqui.ui.modell.Gruppenschluessel
import de.ithandwerkstuttgart.loqui.ui.modell.VerlaufGruppe

/**
 * Der Verlauf: alle Diktate dieses Geraets, durchsuchbar und nach Datum
 * gruppiert. Ohne Eintraege steht hier ein eigener Leerzustand, nie eine
 * leere Flaeche.
 */
@Composable
fun VerlaufBildschirm(
    gruppen: List<VerlaufGruppe>,
    suchbegriff: String,
    aufSuchbegriff: (String) -> Unit,
    aufDiktat: (Diktat) -> Unit,
    aufErstesDiktat: () -> Unit,
    aufZurueck: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { Kopfzeile(titel = R.string.sw_verlauf_titel, aufZurueck = aufZurueck) }
    ) { raender ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(raender)
                .padding(horizontal = Abstand.normal)
        ) {
            Suchfeld(
                suchbegriff = suchbegriff,
                aufSuchbegriff = aufSuchbegriff
            )

            when {
                gruppen.isEmpty() && suchbegriff.isNotEmpty() -> Leerzustand(
                    zeichnung = R.drawable.lq_ic_suche,
                    titel = R.string.sw_verlauf_ohne_treffer_titel,
                    text = R.string.sw_verlauf_ohne_treffer_text
                )

                gruppen.isEmpty() -> Leerzustand(
                    zeichnung = R.drawable.lq_ic_verlauf,
                    titel = R.string.sw_verlauf_leer_titel,
                    text = R.string.sw_verlauf_leer_text,
                    handlung = R.string.sw_verlauf_leer_handlung,
                    aufHandlung = aufErstesDiktat
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Abstand.gross),
                    verticalArrangement = Arrangement.spacedBy(Abstand.klein)
                ) {
                    gruppen.forEach { gruppe ->
                        item(key = gruppe.schluessel.name + gruppe.eigenesDatum) {
                            val eigenesDatum = gruppe.eigenesDatum
                            if (eigenesDatum != null) {
                                Abschnittstitel(titel = eigenesDatum)
                            } else {
                                Abschnittstitel(titel = gruppentitel(gruppe.schluessel))
                            }
                        }
                        items(gruppe.diktate, key = { it.id }) { diktat ->
                            Verlaufzeile(diktat = diktat, aufTippen = { aufDiktat(diktat) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Suchfeld(
    suchbegriff: String,
    aufSuchbegriff: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = suchbegriff,
        onValueChange = aufSuchbegriff,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Abstand.klein),
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                text = stringResource(R.string.sw_verlauf_suche),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Symbol(zeichnung = R.drawable.lq_ic_suche, beschreibung = null)
        },
        trailingIcon = {
            if (suchbegriff.isNotEmpty()) {
                Symbolknopf(
                    zeichnung = R.drawable.lq_ic_abbrechen,
                    beschreibung = R.string.sw_verlauf_suche_leeren,
                    aufTippen = { aufSuchbegriff("") }
                )
            }
        }
    )
}

@Composable
private fun Verlaufzeile(
    diktat: Diktat,
    aufTippen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Kachel(modifier = modifier, aufTippen = aufTippen) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = diktat.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(modifier = Modifier.padding(top = Abstand.klein)) {
                Text(
                    text = stringResource(
                        R.string.sw_verlauf_eintrag_meta,
                        diktat.uhrzeit,
                        dauerText(diktat.dauerSekunden),
                        diktat.sprachName
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@StringRes
internal fun gruppentitelRes(schluessel: Gruppenschluessel): Int = when (schluessel) {
    Gruppenschluessel.HEUTE -> R.string.sw_verlauf_gruppe_heute
    Gruppenschluessel.GESTERN -> R.string.sw_verlauf_gruppe_gestern
    Gruppenschluessel.DIESE_WOCHE -> R.string.sw_verlauf_gruppe_diese_woche
    Gruppenschluessel.AELTER -> R.string.sw_verlauf_gruppe_aelter
}

@Composable
private fun gruppentitel(schluessel: Gruppenschluessel): String =
    stringResource(gruppentitelRes(schluessel))

@Preview(name = "Verlauf leer", showBackground = true)
@Composable
private fun VorschauVerlaufLeer() {
    LoquiTheme {
        VerlaufBildschirm(
            gruppen = emptyList(),
            suchbegriff = "",
            aufSuchbegriff = {},
            aufDiktat = {},
            aufErstesDiktat = {},
            aufZurueck = {}
        )
    }
}

@Preview(name = "Verlauf gefuellt", showBackground = true)
@Composable
private fun VorschauVerlauf() {
    LoquiTheme {
        Box {
            VerlaufBildschirm(
                gruppen = listOf(
                    VerlaufGruppe(
                        schluessel = Gruppenschluessel.HEUTE,
                        diktate = listOf(
                            Diktat(
                                id = "1",
                                text = "Bitte den Vertrag bis Freitag gegenlesen und mir kurz Bescheid geben.",
                                zeitpunktMillis = 0L,
                                uhrzeit = "09:14",
                                datum = "27.08.2026",
                                sprachCode = "de-DE",
                                sprachName = "Deutsch",
                                dauerSekunden = 23
                            )
                        )
                    ),
                    VerlaufGruppe(
                        schluessel = Gruppenschluessel.GESTERN,
                        diktate = listOf(
                            Diktat(
                                id = "2",
                                text = "Einkauf: Mehl, Hefe, zwei Zitronen.",
                                zeitpunktMillis = 0L,
                                uhrzeit = "18:02",
                                datum = "26.08.2026",
                                sprachCode = "de-DE",
                                sprachName = "Deutsch",
                                dauerSekunden = 8
                            )
                        )
                    )
                ),
                suchbegriff = "",
                aufSuchbegriff = {},
                aufDiktat = {},
                aufErstesDiktat = {},
                aufZurueck = {}
            )
        }
    }
}
