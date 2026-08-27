package de.ithandwerkstuttgart.loqui.ui.bildschirme

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.ithandwerkstuttgart.loqui.R
import de.ithandwerkstuttgart.loqui.ui.bausteine.Kachel
import de.ithandwerkstuttgart.loqui.ui.bausteine.Kopfzeile
import de.ithandwerkstuttgart.loqui.ui.bausteine.Symbol
import de.ithandwerkstuttgart.loqui.ui.gestalt.Abstand
import de.ithandwerkstuttgart.loqui.ui.gestalt.LoquiTheme

/**
 * Datenschutz im Klartext: was bleibt, was das Netz sieht, wozu der Dienst
 * dient und was sich loeschen laesst. Keine Verweise nach aussen.
 */
@Composable
fun DatenschutzBildschirm(
    aufZurueck: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { Kopfzeile(titel = R.string.sw_datenschutz_titel, aufZurueck = aufZurueck) }
    ) { raender ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(raender)
                .padding(horizontal = Abstand.normal)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.sw_datenschutz_kurz),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = Abstand.normal)
            )

            Abschnittkachel(
                zeichnung = R.drawable.lq_ic_datenschutz,
                titel = R.string.sw_datenschutz_geraet_titel,
                text = R.string.sw_datenschutz_geraet_text
            )
            Abschnittkachel(
                zeichnung = R.drawable.lq_ic_abbrechen,
                titel = R.string.sw_datenschutz_netz_titel,
                text = R.string.sw_datenschutz_netz_text,
                modifier = Modifier.padding(top = Abstand.klein)
            )
            Abschnittkachel(
                zeichnung = R.drawable.lq_ic_dienst,
                titel = R.string.sw_datenschutz_dienst_titel,
                text = R.string.sw_datenschutz_dienst_text,
                modifier = Modifier.padding(top = Abstand.klein)
            )
            Abschnittkachel(
                zeichnung = R.drawable.lq_ic_loeschen,
                titel = R.string.sw_datenschutz_loeschen_titel,
                text = R.string.sw_datenschutz_loeschen_text,
                modifier = Modifier.padding(top = Abstand.klein, bottom = Abstand.gross)
            )
        }
    }
}

@Composable
private fun Abschnittkachel(
    @DrawableRes zeichnung: Int,
    @StringRes titel: Int,
    @StringRes text: Int,
    modifier: Modifier = Modifier
) {
    Kachel(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row {
                Symbol(
                    zeichnung = zeichnung,
                    beschreibung = null,
                    modifier = Modifier.padding(end = Abstand.schmal),
                    farbe = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(titel),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Abstand.klein)
            )
        }
    }
}

@Preview(name = "Datenschutz", showBackground = true)
@Composable
private fun VorschauDatenschutz() {
    LoquiTheme {
        DatenschutzBildschirm(aufZurueck = {})
    }
}
