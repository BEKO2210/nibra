package de.ithandwerkstuttgart.nibra.ui.bausteine

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import de.ithandwerkstuttgart.nibra.R
import de.ithandwerkstuttgart.nibra.ui.gestalt.Abstand
import de.ithandwerkstuttgart.nibra.ui.gestalt.Mass

/**
 * Gemeinsame Bausteine aller Bildschirme. Sie halten Raster, Formen und
 * Schriftrollen an einer Stelle zusammen: kein Bildschirm baut eine eigene
 * Kopfzeile, eine eigene Kachel oder einen eigenen Leerzustand.
 */

/** Ein Markensymbol aus `res/drawable`. Nie ein Schriftzeichen, nie ein Emoji. */
@Composable
fun Symbol(
    @DrawableRes zeichnung: Int,
    @StringRes beschreibung: Int?,
    modifier: Modifier = Modifier,
    groesse: Dp = Mass.symbol,
    farbe: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Icon(
        painter = painterResource(zeichnung),
        contentDescription = beschreibung?.let { stringResource(it) },
        modifier = modifier.size(groesse),
        tint = farbe
    )
}

/** Antippbares Symbol. Immer mindestens ein volles Tippziel groß. */
@Composable
fun Symbolknopf(
    @DrawableRes zeichnung: Int,
    @StringRes beschreibung: Int,
    aufTippen: () -> Unit,
    modifier: Modifier = Modifier,
    farbe: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(
        onClick = aufTippen,
        modifier = modifier.sizeIn(minWidth = Mass.tippziel, minHeight = Mass.tippziel)
    ) {
        Symbol(zeichnung = zeichnung, beschreibung = beschreibung, farbe = farbe)
    }
}

/**
 * Die eine Kopfzeile der App: Titel mittig, links der Rückweg, rechts
 * Handlungen (AUFTRAG.md: "Alles mittig und symmetrisch").
 *
 * Der Titel bleibt einzeilig. Der längste Titel der App braucht bei der
 * Systemschriftgröße 285,5 dp von 288 dp verfügbarer Breite; wer die
 * Schrift größer stellt, bekommt darum Auslassungspunkte statt eines
 * zerbrochenen Rasters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Kopfzeile(
    @StringRes titel: Int,
    modifier: Modifier = Modifier,
    aufZurueck: (() -> Unit)? = null,
    handlungen: @Composable () -> Unit = {}
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(titel),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (aufZurueck != null) {
                Symbolknopf(
                    zeichnung = R.drawable.nb_ic_zurueck,
                    beschreibung = R.string.sw_zurueck,
                    aufTippen = aufZurueck
                )
            }
        },
        actions = { handlungen() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

/**
 * Der Leerzustand jeder Liste: Symbol, kurzer Titel, ein Satz Erklärung und
 * die Einladung zur ersten Handlung. Nie eine leere Fläche ohne Erklärung.
 */
@Composable
fun Leerzustand(
    @DrawableRes zeichnung: Int,
    @StringRes titel: Int,
    @StringRes text: Int,
    modifier: Modifier = Modifier,
    @StringRes handlung: Int? = null,
    aufHandlung: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Abstand.gross, vertical = Abstand.gross),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(zeichnung),
            contentDescription = null,
            modifier = Modifier.size(Mass.symbolGross),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(titel),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Abstand.weit)
        )
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Abstand.klein)
        )
        if (handlung != null && aufHandlung != null) {
            Button(
                onClick = aufHandlung,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .padding(top = Abstand.weit)
                    .sizeIn(minHeight = Mass.tippziel)
            ) {
                Text(
                    text = stringResource(handlung),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Die eine Kachel: erhobene Fläche, Formskala, Grundpolsterung. Ist
 * [aufTippen] gesetzt, ist die ganze Kachel ein Tippziel und der Druck bleibt
 * auf die Kachelform beschnitten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Kachel(
    modifier: Modifier = Modifier,
    aufTippen: (() -> Unit)? = null,
    inhalt: @Composable () -> Unit
) {
    if (aufTippen == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Box(modifier = Modifier.padding(Abstand.normal)) { inhalt() }
        }
    } else {
        Surface(
            onClick = aufTippen,
            modifier = modifier
                .fillMaxWidth()
                .sizeIn(minHeight = Mass.tippziel),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Box(modifier = Modifier.padding(Abstand.normal)) { inhalt() }
        }
    }
}

/** Überschrift einer Gruppe innerhalb eines Bildschirms. */
@Composable
fun Abschnittstitel(
    @StringRes titel: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(titel),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            start = Abstand.winzig,
            top = Abstand.weit,
            bottom = Abstand.klein
        )
    )
}

/** Überschrift einer Gruppe, deren Titel zur Laufzeit entsteht (z. B. ein Datum). */
@Composable
fun Abschnittstitel(
    titel: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = titel,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            start = Abstand.winzig,
            top = Abstand.weit,
            bottom = Abstand.klein
        )
    )
}

/**
 * Eine Kachelzeile mit Schalter — für die Einstellungen.
 *
 * Die **ganze Kachel** schaltet, nicht nur der Schalter rechts: die
 * Wertzeilen daneben sind ebenfalls ganzflächig antippbar, und wer die
 * Beschriftung trifft, erwartet dieselbe Wirkung. Der Schalter selbst
 * bekommt darum `onCheckedChange = null` und ist nur noch Anzeige --
 * sonst läge ein zweites Tippziel im ersten und die Sprachausgabe
 * meldete die Zeile zweimal.
 */
@Composable
fun Schalterzeile(
    @StringRes titel: Int,
    @StringRes text: Int,
    an: Boolean,
    aufUmschalten: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Kachel(
        modifier = modifier
            // Zuerst auf die Kachelform beschneiden, damit die Druckwelle
            // nicht über die runden Ecken hinausläuft.
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = an, role = Role.Switch, onValueChange = aufUmschalten)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = Mass.tippziel),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titel),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Abstand.winzig)
                )
            }
            Switch(
                checked = an,
                // Die Kachel schaltet; der Schalter zeigt nur den Stand.
                onCheckedChange = null,
                modifier = Modifier.padding(start = Abstand.schmal)
            )
        }
    }
}

/**
 * Eine antippbare Kachelzeile: Titel, aktueller Wert und ein Weiter-Zeichen.
 * Der Wert ist bereits fertig formatierter Text aus dem Zustand.
 */
@Composable
fun Wertzeile(
    @StringRes titel: Int,
    wert: String,
    aufTippen: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes zeichnung: Int? = null
) {
    Kachel(
        modifier = modifier,
        aufTippen = aufTippen
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = Mass.tippziel),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (zeichnung != null) {
                Symbol(
                    zeichnung = zeichnung,
                    beschreibung = null,
                    modifier = Modifier.padding(end = Abstand.schmal),
                    farbe = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titel),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (wert.isNotEmpty()) {
                    Text(
                        text = wert,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Abstand.winzig)
                    )
                }
            }
            Symbol(
                zeichnung = R.drawable.nb_ic_weiter,
                beschreibung = null,
                modifier = Modifier.padding(start = Abstand.schmal)
            )
        }
    }
}

/**
 * Klickbarkeit mit Rolle Schaltfläche und garantiertem Tippziel — an einer
 * Stelle, damit jedes antippbare Element der App gleich groß und gleich
 * angesagt ist.
 */
fun Modifier.klickbar(aufTippen: () -> Unit): Modifier = this
    .sizeIn(minWidth = Mass.tippziel, minHeight = Mass.tippziel)
    .clickable(role = Role.Button, onClick = aufTippen)
