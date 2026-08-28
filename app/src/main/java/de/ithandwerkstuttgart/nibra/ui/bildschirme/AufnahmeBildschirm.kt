package de.ithandwerkstuttgart.nibra.ui.bildschirme

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import de.ithandwerkstuttgart.nibra.R
import de.ithandwerkstuttgart.nibra.ui.bausteine.Kachel
import de.ithandwerkstuttgart.nibra.ui.bausteine.Kopfzeile
import de.ithandwerkstuttgart.nibra.ui.bausteine.Symbol
import de.ithandwerkstuttgart.nibra.ui.bausteine.Symbolknopf
import de.ithandwerkstuttgart.nibra.ui.bausteine.Blobflaeche
import de.ithandwerkstuttgart.nibra.ui.bausteine.Wanderschrift
import de.ithandwerkstuttgart.nibra.ui.bausteine.klickbar
import de.ithandwerkstuttgart.nibra.ui.gestalt.Abstand
import de.ithandwerkstuttgart.nibra.ui.gestalt.Bewegung
import de.ithandwerkstuttgart.nibra.ui.gestalt.LokaleBewegungAus
import de.ithandwerkstuttgart.nibra.ui.gestalt.Mass
import de.ithandwerkstuttgart.nibra.ui.gestalt.LokaleBlobfarben
import de.ithandwerkstuttgart.nibra.ui.gestalt.NibraTheme
import de.ithandwerkstuttgart.nibra.ui.modell.Aufnahmezustand
import de.ithandwerkstuttgart.nibra.ui.modell.Diktat
import de.ithandwerkstuttgart.nibra.ui.modell.Fehlerart

/**
 * Der Hauptbildschirm: eine große, mittige Aufnahmefläche, darunter Dauer,
 * Pegelkurve und der jeweils passende Satz. Alles Übrige tritt zurück.
 */
@Composable
fun AufnahmeBildschirm(
    zustand: Aufnahmezustand,
    aufAufnahmeUmschalten: () -> Unit,
    aufErneutVersuchen: () -> Unit,
    aufVerlauf: () -> Unit,
    aufEinstellungen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Das zuletzt fertige Diktat -- steht sichtbar unter der Fläche. */
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
        // Im Querformat und auf niedrigen Fenstern stehen Fläche und Text
        // nebeneinander. Untereinander wäre der Text abgeschnitten: die
        // Fläche allein ist 200 dp hoch, ein Querformat-Fenster oft nur 384.
        val eng = LocalConfiguration.current.screenHeightDp < ENGE_HOEHE_DP

        val flaeche = @Composable {
            Aufnahmeflaeche(zustand = zustand, aufTippen = aufAufnahmeUmschalten)
        }
        // Einmal gelesen und weitergereicht: der transitionSpec unten ist
        // kein Composable-Kontext und kann LokaleBewegungAus nicht selbst
        // lesen.
        val bewegungAus = LokaleBewegungAus.current
        val zustandstext = @Composable {
            // Überblendet wird nur beim Wechsel der **Art** des Zustands.
            // Auf `zustand` selbst zu hören wäre ein Fehler: `Laeuft` kommt
            // zehnmal je Sekunde mit neuem Pegel, und die Überblendung liefe
            // dauernd neu an.
            // Nacheinander statt gleichzeitig: bei einer Überblendung lagen
            // der alte und der neue Text übereinander und waren beide
            // unlesbar. Siehe Bewegung.textwechsel.
            AnimatedContent(
                targetState = zustandsart(zustand),
                transitionSpec = { Bewegung.textwechsel(bewegungAus) },
                label = "zustandstext"
            ) { art ->
                when (art) {
                    Zustandsart.BEREIT -> BereitText()
                    Zustandsart.LAEUFT -> (zustand as? Aufnahmezustand.Laeuft)
                        ?.let { LaufendText(it) }
                    Zustandsart.WANDELT -> WandeltText()
                    Zustandsart.FEHLER -> (zustand as? Aufnahmezustand.Fehler)?.let {
                        FehlerText(art = it.art, aufErneutVersuchen = aufErneutVersuchen)
                    }
                }
            }
        }

        val grundriss = Modifier
            .fillMaxSize()
            .padding(raender)
            .padding(horizontal = Abstand.weit)

        if (eng) {
            Row(
                modifier = grundriss,
                horizontalArrangement = Arrangement.spacedBy(Abstand.weit),
                verticalAlignment = Alignment.CenterVertically
            ) {
                flaeche()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    zustandstext()
                }
            }
        } else {
            Column(
                modifier = grundriss.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                flaeche()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Abstand.gross),
                    contentAlignment = Alignment.Center
                ) {
                    zustandstext()
                }

                // Das Ergebnis bleibt stehen, bis das nächste Diktat beginnt.
                // Es kommt herein, statt zu erscheinen -- eine Karte, die aus
                // dem Nichts steht, liest sich wie ein Fehler.
                //
                // Im engen Aufbau entfällt sie: dort ist kein Platz, und der
                // Verlauf ist einen Griff entfernt.
                AnimatedVisibility(
                    visible = zustand is Aufnahmezustand.Bereit && letztesDiktat != null,
                    enter = fadeIn(Bewegung.wirkung()) +
                        expandVertically(Bewegung.raum(), expandFrom = Alignment.Top),
                    exit = fadeOut(Bewegung.wirkung()) +
                        shrinkVertically(Bewegung.raum(), shrinkTowards = Alignment.Top)
                ) {
                    // Während des Hinausgehens ist der Eintrag schon fort --
                    // der zuletzt gezeigte bleibt bis zum Ende der Bewegung.
                    val gezeigt = remember(letztesDiktat) { letztesDiktat }
                    gezeigt?.let {
                        Ergebniskarte(
                            diktat = it,
                            aufKopieren = aufLetztesKopieren,
                            aufEinfuegen = aufLetztesEinfuegen,
                            aufOeffnen = aufLetztesOeffnen,
                            modifier = Modifier.padding(top = Abstand.gross)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Die Aufnahmefläche: eine runde, lebendige Fläche. Drei Farbwolken
 * wandern darin umeinander und greifen mit dem Pegel weiter aus; in Ruhe
 * atmen sie nur langsam weiter.
 */
@Composable
private fun Aufnahmeflaeche(
    zustand: Aufnahmezustand,
    aufTippen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val laeuft = zustand is Aufnahmezustand.Laeuft
    val pegel = (zustand as? Aufnahmezustand.Laeuft)?.pegel ?: 0f
    val ansage = stringResource(
        if (laeuft) R.string.sw_aufnahme_beenden else R.string.sw_aufnahme_starten
    )
    val blob = LokaleBlobfarben.current

    // Der Finger druckt die Fläche ein. Die Antwort kommt sofort und ohne
    // Nachwippen -- sie soll sich anfühlen, nicht auffallen.
    val beruehrungen = remember { MutableInteractionSource() }
    val gedrueckt by beruehrungen.collectIsPressedAsState()
    val druck by animateFloatAsState(
        targetValue = if (gedrueckt) DRUCK_MASS else 1f,
        animationSpec = Bewegung.wirkung(),
        label = "druck"
    )

    // Der Zustandswechsel ist der eine große Moment der App. Nur hier darf
    // etwas überschwingen -- die Fläche holt beim Start kurz aus.
    val auftritt by animateFloatAsState(
        targetValue = if (laeuft) AUFNAHME_MASS else 1f,
        animationSpec = Bewegung.auftritt(),
        label = "auftritt"
    )

    // Skaliert wird über die Zeichenebene, nicht über die Größe: so
    // bewegt sich nichts im Layout und der Rest des Bildschirms bleibt ruhig.
    val rueckmeldung = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .size(Mass.aufnahmeflaeche)
            .graphicsLayer {
                scaleX = druck * auftritt
                scaleY = druck * auftritt
            }
            .clip(CircleShape)
            .sizeIn(minWidth = Mass.tippziel, minHeight = Mass.tippziel)
            .clickable(
                interactionSource = beruehrungen,
                indication = null,
                role = Role.Button
            ) {
                // Ein kurzer Stoß zum Beginn und zum Ende des Diktats --
                // die einzige Stelle der App, an der gefühlt wird. Überall
                // sonst wäre es Lärm.
                rueckmeldung.performHapticFeedback(HapticFeedbackType.LongPress)
                aufTippen()
            }
            .semantics { contentDescription = ansage },
        shape = CircleShape,
        // Der Kreis trägt dieselbe Farbe wie die dunkelste Wolke, damit
        // zwischen Blob und Rand kein heller Ring stehen bleibt.
        color = blob.grund,
        contentColor = blob.symbol
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Die lebendige Fläche: drei Farbwolken, die umeinander wandern
            // und mit dem Pegel weiter ausgreifen. In Ruhe atmen sie nur.
            // Die Fläche selbst atmet mit der Stimme. Das ersetzt die
            // frühere Strichkurve: eine Anzeige, die man ansieht, statt
            // einer zweiten daneben, die man ablesen muss.
            //
            // Nur wenige Prozent -- eine Blase, die bei jedem Laut auf und
            // ab springt, wirkt wie Spielzeug. Die Feder glättet den Rest.
            val atem by animateFloatAsState(
                targetValue = if (laeuft) 1f + pegel.coerceIn(0f, 1f) * ATEM_ANTEIL else 1f,
                animationSpec = Bewegung.wirkung(),
                label = "atem"
            )
            Blobflaeche(
                pegel = pegel,
                laeuft = laeuft,
                farbeA = blob.a,
                farbeB = blob.b,
                farbeC = blob.c,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = atem; scaleY = atem }
            )
            // Mikrofon und Stopp wechseln überblendet statt hart --
            // ein Schnitt mitten in der wachsenden Fläche wirkt wie ein Fehler.
            Crossfade(
                targetState = laeuft,
                animationSpec = Bewegung.wirkung(),
                label = "aufnahmesymbol"
            ) { laeuftGerade ->
                Symbol(
                    zeichnung = if (laeuftGerade) {
                        R.drawable.nb_ic_stopp
                    } else {
                        R.drawable.nb_ic_mikrofon
                    },
                    beschreibung = null,
                    groesse = Mass.symbolGross,
                    farbe = blob.symbol
                )
            }
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
        // Was Nibra bisher verstanden hat -- läuft ruhig mit. Beim
        // Dauerdiktat stehen hier auch die bereits fertigen Sätze, sonst
        // wäre der Bildschirm nach jedem Satz wieder leer.
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

/** Das fertige Diktat unter der Fläche: lesen, kopieren, einfügen, öffnen. */
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
        // Statt eines Kreisels läuft eine Welle durch das Wort: sie sagt
        // "es geht weiter", ohne einen Fortschritt vorzutäuschen, den
        // Nibra nicht kennt -- und sie lässt sich lesen.
        Wanderschrift(
            text = stringResource(R.string.sw_aufnahme_wandelt_titel),
            stil = MaterialTheme.typography.headlineSmall,
            farbe = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.sw_aufnahme_wandelt_hinweis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Abstand.klein)
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
    Fehlerart.KEIN_ERGEBNIS -> R.string.sw_fehler_kein_ergebnis
    Fehlerart.NICHTS_GEHOERT -> R.string.sw_fehler_nichts_gehoert
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

/**
 * Die Art eines Aufnahmezustands, ohne seine Daten.
 *
 * Sie ist der Schlüssel der Überblendung: `Aufnahmezustand.Laeuft` trägt
 * Pegel und Dauer und ändert sich zehnmal je Sekunde -- die Art ändert sich
 * nur bei einem echten Wechsel.
 */
private enum class Zustandsart { BEREIT, LAEUFT, WANDELT, FEHLER }

private fun zustandsart(zustand: Aufnahmezustand): Zustandsart = when (zustand) {
    is Aufnahmezustand.Bereit -> Zustandsart.BEREIT
    is Aufnahmezustand.Laeuft -> Zustandsart.LAEUFT
    is Aufnahmezustand.Wandelt -> Zustandsart.WANDELT
    is Aufnahmezustand.Fehler -> Zustandsart.FEHLER
}

/**
 * Unterhalb dieser Fensterhöhe stehen Fläche und Text nebeneinander.
 *
 * 420 dp, weil die Fläche 200 dp misst und darüber die Kopfzeile sitzt --
 * darunter bleibt für zwei Zeilen Text und Rand nichts mehr übrig. Ein
 * Querformat-Telefon hat rund 384 dp.
 */
/**
 * Wie stark die Aufnahmefläche mit der Stimme atmet, als Anteil ihrer
 * Größe. Sechs Prozent sind bei voller Lautstärke deutlich zu sehen und
 * bleiben doch ruhig.
 */
private const val ATEM_ANTEIL = 0.06f

private const val ENGE_HOEHE_DP = 420

/** Wie weit die Aufnahmefläche unter dem Finger nachgibt. */
private const val DRUCK_MASS = 0.96f

/** Wie weit sie während der Aufnahme steht -- groß genug, um es zu sehen. */
private const val AUFNAHME_MASS = 1.05f
