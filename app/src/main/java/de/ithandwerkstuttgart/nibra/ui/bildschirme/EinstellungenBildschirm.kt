package de.ithandwerkstuttgart.nibra.ui.bildschirme

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.ithandwerkstuttgart.nibra.BuildConfig
import de.ithandwerkstuttgart.nibra.R
import de.ithandwerkstuttgart.nibra.ui.bausteine.Abschnittstitel
import de.ithandwerkstuttgart.nibra.ui.bausteine.Kachel
import de.ithandwerkstuttgart.nibra.ui.bausteine.Kopfzeile
import de.ithandwerkstuttgart.nibra.ui.bausteine.Schalterzeile
import de.ithandwerkstuttgart.nibra.ui.bausteine.Symbol
import de.ithandwerkstuttgart.nibra.ui.bausteine.Wertzeile
import de.ithandwerkstuttgart.nibra.ui.gestalt.Abstand
import de.ithandwerkstuttgart.nibra.ui.gestalt.Mass
import de.ithandwerkstuttgart.nibra.ui.gestalt.NibraTheme
import de.ithandwerkstuttgart.nibra.ui.modell.Dienstzustand
import de.ithandwerkstuttgart.nibra.ui.modell.Einstellungen
import de.ithandwerkstuttgart.nibra.ui.modell.Mikrofonzustand

/**
 * Die Einstellungen: Aufnahme, Sprache, Dienst und die übrigen Bildschirme —
 * jede Gruppe eine Kachelreihe, alle aus derselben Abstandsskala.
 */
@Composable
fun EinstellungenBildschirm(
    einstellungen: Einstellungen,
    aufZustaendeAktualisieren: () -> Unit,
    aufStoppBeiStille: (Boolean) -> Unit,
    aufOberflaechensprache: () -> Unit,
    aufDiktatsprache: () -> Unit,
    aufMikrofonErlauben: () -> Unit,
    aufDienstEinrichten: () -> Unit,
    aufTextbausteine: () -> Unit,
    aufDatenschutz: () -> Unit,
    aufFremdsoftware: () -> Unit,
    aufZurueck: () -> Unit,
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

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { Kopfzeile(titel = R.string.sw_einstellungen_titel, aufZurueck = aufZurueck) }
    ) { raender ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(raender)
                .padding(horizontal = Abstand.normal)
                .verticalScroll(rememberScrollState())
        ) {
            Abschnittstitel(titel = R.string.sw_einstellungen_gruppe_aufnahme)
            Schalterzeile(
                titel = R.string.sw_einstellungen_stopp_bei_stille,
                text = R.string.sw_einstellungen_stopp_bei_stille_text,
                an = einstellungen.stoppBeiStille,
                aufUmschalten = aufStoppBeiStille
            )

            Abschnittstitel(titel = R.string.sw_einstellungen_gruppe_sprache)
            Wertzeile(
                titel = R.string.sw_einstellungen_oberflaechensprache,
                wert = einstellungen.oberflaechenspracheName,
                aufTippen = aufOberflaechensprache,
                zeichnung = R.drawable.nb_ic_sprache
            )
            Wertzeile(
                titel = R.string.sw_einstellungen_diktatsprache,
                wert = einstellungen.diktatspracheName,
                aufTippen = aufDiktatsprache,
                zeichnung = R.drawable.nb_ic_mikrofon,
                modifier = Modifier.padding(top = Abstand.klein)
            )

            Abschnittstitel(titel = R.string.sw_einstellungen_gruppe_rechte)
            Zustandskachel(
                zeichnung = R.drawable.nb_ic_mikrofon,
                titel = R.string.sw_einstellungen_mikrofon,
                erfuellt = einstellungen.mikrofonzustand == Mikrofonzustand.ERTEILT,
                zustandErfuellt = R.string.sw_einstellungen_mikrofon_erteilt,
                zustandOffen = R.string.sw_einstellungen_mikrofon_nicht_erteilt,
                handlung = R.string.sw_einstellungen_mikrofon_erlauben,
                aufHandlung = aufMikrofonErlauben
            )

            Abschnittstitel(titel = R.string.sw_einstellungen_gruppe_dienst)
            Zustandskachel(
                zeichnung = R.drawable.nb_ic_dienst,
                titel = R.string.sw_einstellungen_dienst,
                erfuellt = einstellungen.dienstzustand == Dienstzustand.EINGERICHTET,
                zustandErfuellt = R.string.sw_einstellungen_dienst_eingerichtet,
                zustandOffen = R.string.sw_einstellungen_dienst_nicht_eingerichtet,
                handlung = R.string.sw_einstellungen_dienst_einrichten,
                aufHandlung = aufDienstEinrichten
            )

            Abschnittstitel(titel = R.string.sw_einstellungen_gruppe_mehr)
            Wertzeile(
                titel = R.string.sw_bausteine_titel,
                wert = "",
                aufTippen = aufTextbausteine,
                zeichnung = R.drawable.nb_ic_baustein
            )
            Wertzeile(
                titel = R.string.sw_datenschutz_titel,
                wert = "",
                aufTippen = aufDatenschutz,
                zeichnung = R.drawable.nb_ic_datenschutz,
                modifier = Modifier.padding(top = Abstand.klein)
            )
            Wertzeile(
                titel = R.string.sw_einstellungen_fremdsoftware,
                wert = "",
                aufTippen = aufFremdsoftware,
                zeichnung = R.drawable.nb_ic_lizenz,
                modifier = Modifier.padding(top = Abstand.klein)
            )

            Markenfuss(modifier = Modifier.padding(top = Abstand.gross, bottom = Abstand.gross))
        }
    }
}

/**
 * Der Fuß der Einstellungen: das Zeichen, der Name, die Fassung und der
 * eine Satz, der Nibra ausmacht. Kein Knopf, keine Handlung -- er sagt nur,
 * wessen App das hier ist.
 */
@Composable
private fun Markenfuss(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Symbol(
            zeichnung = R.drawable.nb_zeichen,
            beschreibung = null,
            groesse = Mass.zeichen,
            farbe = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = Abstand.schmal)
        )
        Text(
            text = stringResource(R.string.sw_marke_fassung, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Abstand.winzig)
        )
        Text(
            text = stringResource(R.string.sw_marke_satz),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Abstand.schmal)
        )
    }
}

/**
 * Eine Kachel, die einen Zustand des Geräts zeigt: erfüllt mit Haken, offen
 * mit der Einladung, ihn zu erteilen. Mikrofon-Recht und Bedienungshilfen-Dienst
 * sehen damit gleich aus.
 */
@Composable
private fun Zustandskachel(
    @DrawableRes zeichnung: Int,
    @StringRes titel: Int,
    erfuellt: Boolean,
    @StringRes zustandErfuellt: Int,
    @StringRes zustandOffen: Int,
    @StringRes handlung: Int,
    aufHandlung: () -> Unit,
    modifier: Modifier = Modifier
) {
    Kachel(
        modifier = modifier,
        aufTippen = if (erfuellt) null else aufHandlung
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Symbol(
                zeichnung = zeichnung,
                beschreibung = null,
                modifier = Modifier.padding(end = Abstand.schmal),
                farbe = if (erfuellt) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titel),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(if (erfuellt) zustandErfuellt else zustandOffen),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Abstand.winzig)
                )
            }
            if (erfuellt) {
                Symbol(
                    zeichnung = R.drawable.nb_ic_haken,
                    beschreibung = null,
                    farbe = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = stringResource(handlung),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(name = "Einstellungen", showBackground = true)
@Composable
private fun VorschauEinstellungen() {
    NibraTheme {
        EinstellungenBildschirm(
            einstellungen = Einstellungen(
                oberflaechenspracheName = "Deutsch",
                diktatspracheName = "Deutsch"
            ),
            aufZustaendeAktualisieren = {},
            aufStoppBeiStille = {},
            aufOberflaechensprache = {},
            aufDiktatsprache = {},
            aufMikrofonErlauben = {},
            aufDienstEinrichten = {},
            aufTextbausteine = {},
            aufDatenschutz = {},
            aufFremdsoftware = {},
            aufZurueck = {}
        )
    }
}
