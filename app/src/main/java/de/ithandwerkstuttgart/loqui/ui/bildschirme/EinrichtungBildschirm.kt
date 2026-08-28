package de.ithandwerkstuttgart.loqui.ui.bildschirme

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.ithandwerkstuttgart.loqui.R
import de.ithandwerkstuttgart.loqui.ui.bausteine.Kachel
import de.ithandwerkstuttgart.loqui.ui.bausteine.Symbol
import de.ithandwerkstuttgart.loqui.ui.gestalt.Abstand
import de.ithandwerkstuttgart.loqui.ui.gestalt.Mass
import de.ithandwerkstuttgart.loqui.ui.gestalt.LoquiTheme
import de.ithandwerkstuttgart.loqui.ui.modell.Dienstzustand
import de.ithandwerkstuttgart.loqui.ui.modell.Mikrofonzustand

/**
 * Die Einrichtung: zwei Schritte, beide erklaert, bevor sie etwas verlangen.
 * Der Offenlegungstext zum Bedienungshilfen-Dienst steht vollstaendig hier —
 * uebersetzt wie jeder andere Satz der App.
 */
@Composable
fun EinrichtungBildschirm(
    mikrofonzustand: Mikrofonzustand,
    dienstzustand: Dienstzustand,
    aufZustaendeAktualisieren: () -> Unit,
    aufMikrofonErlauben: () -> Unit,
    aufDienstAktivieren: () -> Unit,
    aufSpaeter: () -> Unit,
    aufFertig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val aktuelleAktualisierung = rememberUpdatedState(aufZustaendeAktualisieren)
    DisposableEffect(lifecycleOwner) {
        aktuelleAktualisierung.value()
        val beobachter = LifecycleEventObserver { _, ereignis ->
            if (ereignis == Lifecycle.Event.ON_RESUME) aktuelleAktualisierung.value()
        }
        lifecycleOwner.lifecycle.addObserver(beobachter)
        onDispose { lifecycleOwner.lifecycle.removeObserver(beobachter) }
    }

    val mikrofonErteilt = mikrofonzustand == Mikrofonzustand.ERTEILT
    val dienstAktiv = dienstzustand == Dienstzustand.EINGERICHTET

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background
    ) { raender ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(raender)
                .padding(horizontal = Abstand.weit)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Das Markenzeichen selbst, nicht ein Sinnbild dafuer: dies ist
            // der erste Bildschirm, den jemand von Loqui sieht.
            Symbol(
                zeichnung = R.drawable.lq_zeichen,
                beschreibung = null,
                modifier = Modifier.padding(top = Abstand.gross),
                groesse = Mass.zeichen,
                farbe = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.sw_einrichtung_willkommen_titel),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Abstand.weit)
            )
            Text(
                text = stringResource(R.string.sw_einrichtung_willkommen_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Abstand.klein)
            )

            Schrittkachel(
                nummer = 1,
                zeichnung = R.drawable.lq_ic_mikrofon,
                titel = R.string.sw_einrichtung_mikrofon_titel,
                text = R.string.sw_einrichtung_mikrofon_text,
                erledigt = mikrofonErteilt,
                erledigtText = R.string.sw_einrichtung_mikrofon_erteilt,
                handlung = R.string.sw_einrichtung_mikrofon_handlung,
                aufHandlung = aufMikrofonErlauben,
                modifier = Modifier.padding(top = Abstand.gross)
            )

            Schrittkachel(
                nummer = 2,
                zeichnung = R.drawable.lq_ic_dienst,
                titel = R.string.sw_einrichtung_dienst_titel,
                text = R.string.sw_einrichtung_dienst_offenlegung,
                erledigt = dienstAktiv,
                erledigtText = R.string.sw_einrichtung_dienst_eingerichtet,
                handlung = R.string.sw_einrichtung_dienst_handlung,
                aufHandlung = aufDienstAktivieren,
                zusatz = R.string.sw_einrichtung_dienst_systemhinweis,
                modifier = Modifier.padding(top = Abstand.klein)
            )

            Button(
                onClick = aufFertig,
                enabled = mikrofonErteilt,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Abstand.gross)
            ) {
                Text(
                    text = stringResource(R.string.sw_einrichtung_los),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            TextButton(
                onClick = aufSpaeter,
                modifier = Modifier.padding(top = Abstand.winzig, bottom = Abstand.gross)
            ) {
                Text(
                    text = stringResource(R.string.sw_einrichtung_spaeter),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun Schrittkachel(
    nummer: Int,
    @DrawableRes zeichnung: Int,
    @StringRes titel: Int,
    @StringRes text: Int,
    erledigt: Boolean,
    @StringRes erledigtText: Int,
    @StringRes handlung: Int,
    aufHandlung: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes zusatz: Int? = null
) {
    Kachel(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Symbol(
                    zeichnung = if (erledigt) R.drawable.lq_ic_haken else zeichnung,
                    beschreibung = null,
                    modifier = Modifier.padding(end = Abstand.schmal),
                    farbe = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sw_einrichtung_schritt_format, nummer, 2),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(titel),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = Abstand.winzig)
                    )
                }
            }
            Text(
                text = stringResource(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Abstand.schmal)
            )
            if (zusatz != null && !erledigt) {
                Text(
                    text = stringResource(zusatz),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Abstand.klein)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Abstand.normal),
                horizontalArrangement = Arrangement.End
            ) {
                if (erledigt) {
                    Text(
                        text = stringResource(erledigtText),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Button(onClick = aufHandlung, shape = MaterialTheme.shapes.small) {
                        Text(
                            text = stringResource(handlung),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Einrichtung", showBackground = true)
@Composable
private fun VorschauEinrichtung() {
    LoquiTheme {
        EinrichtungBildschirm(
            mikrofonzustand = Mikrofonzustand.NICHT_ERTEILT,
            dienstzustand = Dienstzustand.NICHT_EINGERICHTET,
            aufZustaendeAktualisieren = {},
            aufMikrofonErlauben = {},
            aufDienstAktivieren = {},
            aufSpaeter = {},
            aufFertig = {}
        )
    }
}

@Preview(name = "Einrichtung erledigt", showBackground = true)
@Composable
private fun VorschauEinrichtungErledigt() {
    LoquiTheme {
        EinrichtungBildschirm(
            mikrofonzustand = Mikrofonzustand.ERTEILT,
            dienstzustand = Dienstzustand.EINGERICHTET,
            aufZustaendeAktualisieren = {},
            aufMikrofonErlauben = {},
            aufDienstAktivieren = {},
            aufSpaeter = {},
            aufFertig = {}
        )
    }
}
