package de.ithandwerkstuttgart.nibra.ui.bildschirme

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import de.ithandwerkstuttgart.nibra.R
import de.ithandwerkstuttgart.nibra.ui.bausteine.Kachel
import de.ithandwerkstuttgart.nibra.ui.bausteine.Kopfzeile
import de.ithandwerkstuttgart.nibra.ui.bausteine.Pegelkurve
import de.ithandwerkstuttgart.nibra.ui.bausteine.Symbol
import de.ithandwerkstuttgart.nibra.ui.bausteine.Symbolknopf
import de.ithandwerkstuttgart.nibra.ui.bausteine.Wandelkurve
import de.ithandwerkstuttgart.nibra.ui.bausteine.klickbar
import de.ithandwerkstuttgart.nibra.ui.gestalt.Abstand
import de.ithandwerkstuttgart.nibra.ui.gestalt.Mass
import de.ithandwerkstuttgart.nibra.ui.gestalt.NibraTheme
import de.ithandwerkstuttgart.nibra.ui.modell.Aufnahmezustand
import de.ithandwerkstuttgart.nibra.ui.modell.Diktat
import de.ithandwerkstuttgart.nibra.ui.modell.Fehlerart

/**
 * Der Hauptbildschirm: eine grosse, mittige Aufnahmeflaeche, darunter Dauer,
 * Pegelkurve und der jeweils passende Satz. Alles Uebrige tritt zurueck.
 */
@Composable
fun AufnahmeBildschirm(
    zustand: Aufnahmezustand,
    aufAufnahmeUmschalten: () -> Unit,
    aufErneutVersuchen: () -> Unit,
    aufVerlauf: () -> Unit,
    aufEinstellungen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Das zuletzt fertige Diktat -- steht sichtbar unter der Flaeche. */
    letztesDiktat: Diktat? = null,
    aufLetztesKopieren: () -> Unit = {},
    aufLetztesEinfuegen: () -> Unit = {},
    aufLetztesOeffnen: () -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Kopfzeile(titel = R.string.sw_aufnahme_titel) {
                Symbolknopf(
                    zeichnung = R.drawable.nb_ic_verlauf,
                    beschreibung = R.string.sw_verlauf_oeffnen,
                    aufTippen = aufVerlauf
                )
                Symbolknopf(
                    zeichnung = R.drawable.nb_ic_einstellungen,
                    beschreibung = R.string.sw_einstellungen_oeffnen,
                    aufTippen = aufEinstellungen
                )
            }
        }
    ) { raender ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(raender)
                .padding(horizontal = Abstand.weit)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Aufnahmeflaeche(
                zustand = zustand,
                aufTippen = aufAufnahmeUmschalten
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Abstand.gross),
                contentAlignment = Alignment.Center
            ) {
                when (zustand) {
                    is Aufnahmezustand.Bereit -> BereitText()
                    is Aufnahmezustand.Laeuft -> LaufendText(zustand)
                    is Aufnahmezustand.Wandelt -> WandeltText()
                    is Aufnahmezustand.Fehler -> FehlerText(
                        art = zustand.art,
                        aufErneutVersuchen = aufErneutVersuchen
                    )
                }
            }

            // Das Ergebnis bleibt stehen, bis das naechste Diktat beginnt.
            if (zustand is Aufnahmezustand.Bereit && letztesDiktat != null) {
                Ergebniskarte(
                    diktat = letztesDiktat,
                    aufKopieren = aufLetztesKopieren,
                    aufEinfuegen = aufLetztesEinfuegen,
                    aufOeffnen = aufLetztesOeffnen,
                    modifier = Modifier.padding(top = Abstand.gross)
                )
            }
        }
    }
}

/**
 * Die Aufnahmeflaeche: konzentrische Bogenwellen um einen ruhenden Mittelpunkt.
 * Im Zustand Laeuft weiten sich die Ringe mit dem Pegel, sonst ruhen sie.
 */
@Composable
private fun Aufnahmeflaeche(
    zustand: Aufnahmezustand,
    aufTippen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val laeuft = zustand is Aufnahmezustand.Laeuft
    val pegel = (zustand as? Aufnahmezustand.Laeuft)?.pegel ?: 0f
    val geglaettet by animateFloatAsState(
        targetValue = pegel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "pegel"
    )
    val atem = rememberInfiniteTransition(label = "atem")
    val ruhewelle by atem.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ruhewelle"
    )
    val ringfarbe = MaterialTheme.colorScheme.primary
    val strichbreite = with(LocalDensity.current) { Abstand.winzig.toPx() }
    val ansage = stringResource(
        if (laeuft) R.string.sw_aufnahme_beenden else R.string.sw_aufnahme_starten
    )

    Surface(
        modifier = modifier
            .size(Mass.aufnahmeflaeche)
            .clip(CircleShape)
            .klickbar(aufTippen)
            .semantics { contentDescription = ansage },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val mittelpunkt = size.minDimension / 2f
                val weite = if (laeuft) geglaettet else ruhewelle * 0.25f
                // Drei Ringe um denselben Mittelpunkt, gleiche Strichstaerke,
                // Anfang und Ende spiegelgleich.
                listOf(0.55f, 0.72f, 0.9f).forEachIndexed { stelle, anteil ->
                    val radius = mittelpunkt * (anteil + weite * 0.08f * (stelle + 1))
                    drawCircle(
                        color = ringfarbe.copy(alpha = 0.5f - stelle * 0.14f),
                        radius = radius,
                        style = Stroke(width = strichbreite)
                    )
                }
            }
            Symbol(
                zeichnung = if (laeuft) R.drawable.nb_ic_stopp else R.drawable.nb_ic_mikrofon,
                beschreibung = null,
                groesse = Mass.symbolGross,
                farbe = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BereitText(modifier: Modifier = Modifier) {
    Zustandstext(
        modifier = modifier,
        titel = R.string.sw_aufnahme_bereit_titel,
        text = R.string.sw_aufnahme_bereit_hinweis
    )
}

@Composable
private fun LaufendText(zustand: Aufnahmezustand.Laeuft, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = dauerText(zustand.dauerSekunden),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Pegelkurve(
            verlauf = zustand.verlauf,
            modifier = Modifier.padding(top = Abstand.normal)
        )
        // Was Nibra bisher verstanden hat -- laeuft ruhig mit. Beim
        // Dauerdiktat stehen hier auch die bereits fertigen Saetze, sonst
        // waere der Bildschirm nach jedem Satz wieder leer.
        if (zustand.sichtbarerText.isNotBlank()) {
            Text(
                text = zustand.sichtbarerText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Abstand.normal)
            )
        }
        Text(
            text = stringResource(
                if (zustand.stilleErkannt) R.string.sw_aufnahme_stille_hinweis
                else R.string.sw_aufnahme_laeuft_hinweis
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (zustand.stilleErkannt) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Abstand.normal)
        )
    }
}

/** Das fertige Diktat unter der Flaeche: lesen, kopieren, einfuegen, oeffnen. */
@Composable
private fun Ergebniskarte(
    diktat: Diktat,
    aufKopieren: () -> Unit,
    aufEinfuegen: () -> Unit,
    aufOeffnen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Kachel(modifier = modifier.fillMaxWidth(), aufTippen = aufOeffnen) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.sw_aufnahme_letztes_diktat),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = diktat.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Abstand.klein)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Abstand.normal),
                horizontalArrangement = Arrangement.spacedBy(Abstand.klein)
            ) {
                Symbolknopf(
                    zeichnung = R.drawable.nb_ic_kopieren,
                    beschreibung = R.string.sw_kopieren,
                    aufTippen = aufKopieren
                )
                Symbolknopf(
                    zeichnung = R.drawable.nb_ic_einfuegen,
                    beschreibung = R.string.sw_einfuegen,
                    aufTippen = aufEinfuegen
                )
            }
        }
    }
}

@Composable
private fun WandeltText(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Wandelkurve()
        Zustandstext(
            titel = R.string.sw_aufnahme_wandelt_titel,
            text = R.string.sw_aufnahme_wandelt_hinweis,
            modifier = Modifier.padding(top = Abstand.normal)
        )
    }
}

@Composable
private fun FehlerText(
    art: Fehlerart,
    aufErneutVersuchen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Symbol(
            zeichnung = R.drawable.nb_ic_warnung,
            beschreibung = null,
            groesse = Mass.symbolGross,
            farbe = MaterialTheme.colorScheme.error
        )
        Text(
            text = stringResource(R.string.sw_fehler_titel),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Abstand.normal)
        )
        Text(
            text = stringResource(fehlerErklaerung(art)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Abstand.klein)
        )
        Button(
            onClick = aufErneutVersuchen,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(top = Abstand.weit)
        ) {
            Text(
                text = stringResource(R.string.sw_fehler_erneut_versuchen),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun Zustandstext(
    @StringRes titel: Int,
    @StringRes text: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(titel),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Abstand.klein)
        )
    }
}

/** Dauer als Minuten und Sekunden — das Format kommt aus `strings.xml`. */
@Composable
fun dauerText(sekunden: Int): String = stringResource(
    R.string.sw_aufnahme_dauer_format,
    sekunden / 60,
    sekunden % 60
)

@StringRes
internal fun fehlerErklaerung(art: Fehlerart): Int = when (art) {
    Fehlerart.KEIN_MIKROFON_RECHT -> R.string.sw_fehler_kein_mikrofon_recht
    Fehlerart.ERKENNUNG_NICHT_VERFUEGBAR -> R.string.sw_fehler_erkennung_nicht_verfuegbar
    Fehlerart.SPRACHE_NICHT_AUF_GERAET -> R.string.sw_fehler_sprache_nicht_auf_geraet
    Fehlerart.NICHTS_VERSTANDEN -> R.string.sw_fehler_nichts_verstanden
    Fehlerart.UNBEKANNT -> R.string.sw_fehler_unbekannt
}

@Preview(name = "Bereit", showBackground = true)
@Composable
private fun VorschauBereit() {
    NibraTheme {
        AufnahmeBildschirm(
            zustand = Aufnahmezustand.Bereit,
            aufAufnahmeUmschalten = {},
            aufErneutVersuchen = {},
            aufVerlauf = {},
            aufEinstellungen = {}
        )
    }
}

@Preview(name = "Laeuft", showBackground = true)
@Composable
private fun VorschauLaeuft() {
    NibraTheme {
        AufnahmeBildschirm(
            zustand = Aufnahmezustand.Laeuft(
                pegel = 0.6f,
                dauerSekunden = 47,
                verlauf = List(48) { stelle -> 0.2f + 0.5f * ((stelle % 7) / 7f) },
                stilleErkannt = false
            ),
            aufAufnahmeUmschalten = {},
            aufErneutVersuchen = {},
            aufVerlauf = {},
            aufEinstellungen = {}
        )
    }
}

@Preview(name = "Fehler", showBackground = true)
@Composable
private fun VorschauFehler() {
    NibraTheme {
        AufnahmeBildschirm(
            zustand = Aufnahmezustand.Fehler(Fehlerart.SPRACHE_NICHT_AUF_GERAET),
            aufAufnahmeUmschalten = {},
            aufErneutVersuchen = {},
            aufVerlauf = {},
            aufEinstellungen = {}
        )
    }
}
