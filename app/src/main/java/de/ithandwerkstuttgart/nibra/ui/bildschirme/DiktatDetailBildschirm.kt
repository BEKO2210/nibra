package de.ithandwerkstuttgart.nibra.ui.bildschirme

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import de.ithandwerkstuttgart.nibra.R
import de.ithandwerkstuttgart.nibra.ui.bausteine.Abschnittstitel
import de.ithandwerkstuttgart.nibra.ui.bausteine.Kachel
import de.ithandwerkstuttgart.nibra.ui.bausteine.Kopfzeile
import de.ithandwerkstuttgart.nibra.ui.bausteine.Symbol
import de.ithandwerkstuttgart.nibra.ui.gestalt.Abstand
import de.ithandwerkstuttgart.nibra.ui.gestalt.Bewegung
import de.ithandwerkstuttgart.nibra.ui.gestalt.NibraTheme
import de.ithandwerkstuttgart.nibra.ui.modell.Diktat

/**
 * Das einzelne Diktat: der vollständige Text, unmittelbar bearbeitbar, und
 * die Handlungen darunter. Sprache und Dauer stehen als Angabe darüber --
 * sie beschreiben, wie der Eintrag entstanden ist, und sind nicht zu ändern.
 */
@Composable
fun DiktatDetailBildschirm(
    diktat: Diktat,
    aufKopieren: () -> Unit,
    aufEinfuegen: () -> Unit,
    aufTeilen: () -> Unit,
    aufLoeschen: () -> Unit,
    aufTextSichern: (String) -> Unit,
    aufZurueck: () -> Unit,
    modifier: Modifier = Modifier,
    meldungen: SnackbarHostState = remember { SnackbarHostState() }
) {
    var loeschfrageOffen by remember { mutableStateOf(false) }

    // Der Entwurf fängt beim gespeicherten Text an und beginnt neu, sobald
    // der Eintrag sich von außen ändert.
    var entwurf by remember(diktat.id, diktat.text) { mutableStateOf(diktat.text) }
    val geaendert = entwurf.trim() != diktat.text && entwurf.isNotBlank()
    val textfeldAnsage = stringResource(R.string.sw_detail_text_bearbeiten)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(meldungen) },
        topBar = { Kopfzeile(titel = R.string.sw_detail_titel, aufZurueck = aufZurueck) }
    ) { raender ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(raender)
                .padding(horizontal = Abstand.normal)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(
                    R.string.sw_verlauf_eintrag_meta,
                    diktat.uhrzeit,
                    dauerText(diktat.dauerSekunden),
                    diktat.sprachName
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Abstand.klein)
            )

            // Der Text ist unmittelbar bearbeitbar (Roadmap, Lauf 4.2): kein
            // Stiftknopf, kein Umschalten in einen zweiten Zustand. Wer
            // hineintippt, ändert. Gesichert wird erst auf Ansage -- solange
            // nichts gesichert ist, bleibt der ursprüngliche Text erhalten.
            Kachel {
                BasicTextField(
                    value = entwurf,
                    onValueChange = { entwurf = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = textfeldAnsage }
                )
            }

            // Die beiden Knöpfe kommen herein, sobald sich etwas geändert
            // hat. Ohne Bewegung springt der Text darunter -- das liest sich,
            // als hätte man etwas kaputtgemacht.
            AnimatedVisibility(
                visible = geaendert,
                enter = fadeIn(Bewegung.wirkung()) +
                    expandVertically(Bewegung.raum(), expandFrom = Alignment.Top),
                exit = fadeOut(Bewegung.wirkung()) +
                    shrinkVertically(Bewegung.raum(), shrinkTowards = Alignment.Top)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Abstand.klein),
                    horizontalArrangement = Arrangement.spacedBy(Abstand.klein)
                ) {
                    Handlungsknopf(
                        zeichnung = R.drawable.nb_ic_haken,
                        beschriftung = R.string.sw_detail_text_sichern,
                        aufTippen = { aufTextSichern(entwurf) },
                        modifier = Modifier.weight(1f)
                    )
                    Handlungsknopf(
                        zeichnung = R.drawable.nb_ic_abbrechen,
                        beschriftung = R.string.sw_detail_text_verwerfen,
                        aufTippen = { entwurf = diktat.text },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Abstand.normal),
                horizontalArrangement = Arrangement.spacedBy(Abstand.klein)
            ) {
                Handlungsknopf(
                    zeichnung = R.drawable.nb_ic_kopieren,
                    beschriftung = R.string.sw_kopieren,
                    aufTippen = aufKopieren,
                    modifier = Modifier.weight(1f)
                )
                Handlungsknopf(
                    zeichnung = R.drawable.nb_ic_einfuegen,
                    beschriftung = R.string.sw_einfuegen,
                    aufTippen = aufEinfuegen,
                    modifier = Modifier.weight(1f)
                )
                Handlungsknopf(
                    zeichnung = R.drawable.nb_ic_teilen,
                    beschriftung = R.string.sw_teilen,
                    aufTippen = aufTeilen,
                    modifier = Modifier.weight(1f)
                )
                Handlungsknopf(
                    zeichnung = R.drawable.nb_ic_loeschen,
                    beschriftung = R.string.sw_loeschen,
                    aufTippen = { loeschfrageOffen = true },
                    modifier = Modifier.weight(1f),
                    warnend = true
                )
            }

            Text(
                text = stringResource(R.string.sw_detail_einfuegen_hinweis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Abstand.klein)
            )

        }
    }

    if (loeschfrageOffen) {
        AlertDialog(
            onDismissRequest = { loeschfrageOffen = false },
            shape = MaterialTheme.shapes.large,
            title = {
                Text(
                    text = stringResource(R.string.sw_detail_loeschen_titel),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.sw_detail_loeschen_text),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    loeschfrageOffen = false
                    aufLoeschen()
                }) {
                    Text(
                        text = stringResource(R.string.sw_loeschen),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { loeschfrageOffen = false }) {
                    Text(
                        text = stringResource(R.string.sw_abbrechen),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}

/** Symbol über Beschriftung — die drei Handlungen liegen gleich breit nebeneinander. */
@Composable
private fun Handlungsknopf(
    @DrawableRes zeichnung: Int,
    @StringRes beschriftung: Int,
    aufTippen: () -> Unit,
    modifier: Modifier = Modifier,
    warnend: Boolean = false
) {
    val farbe = if (warnend) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Kachel(modifier = modifier, aufTippen = aufTippen) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Symbol(zeichnung = zeichnung, beschreibung = null, farbe = farbe)
            Text(
                text = stringResource(beschriftung),
                style = MaterialTheme.typography.labelMedium,
                color = farbe,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Abstand.klein)
            )
        }
    }
}

@Preview(name = "Diktat-Detail", showBackground = true)
@Composable
private fun VorschauDetail() {
    NibraTheme {
        DiktatDetailBildschirm(
            diktat = Diktat(
                id = "1",
                text = "Bitte den Vertrag bis Freitag gegenlesen und mir kurz Bescheid geben. " +
                    "Wenn etwas unklar ist, rufe ich am Donnerstag an.",
                zeitpunktMillis = 0L,
                uhrzeit = "09:14",
                datum = "27.08.2026",
                sprachCode = "de-DE",
                sprachName = "Deutsch",
                dauerSekunden = 23
            ),
            aufKopieren = {},
            aufEinfuegen = {},
            aufTeilen = {},
            aufLoeschen = {},
            aufTextSichern = {},
            aufZurueck = {}
        )
    }
}
