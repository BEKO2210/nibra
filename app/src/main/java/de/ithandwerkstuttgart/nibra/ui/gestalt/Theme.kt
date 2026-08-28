package de.ithandwerkstuttgart.nibra.ui.gestalt

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import de.ithandwerkstuttgart.nibra.R

/**
 * Die Farben der lebendigen Flaeche.
 *
 * Sie liegen neben dem Material-Farbschema, weil Material dafuer keine Rolle
 * kennt -- und sie gehoeren trotzdem in das Thema, damit kein Bildschirm sie
 * selbst zusammenstellt.
 */
val LokaleBlobfarben = staticCompositionLocalOf<Farben.Blobsatz> {
    error("Blobfarben nur innerhalb von NibraTheme")
}

/**
 * Das eine Thema der App. Kein dynamisches Farbschema, keine Zweitpalette:
 * Farben, Formen und Schriftrollen kommen aus je einer Quelle.
 *
 * `MaterialExpressiveTheme` statt `MaterialTheme`: Material hat die Bewegung
 * 2025 von Dauer und Easing auf Federn umgestellt. Der ausdrucksstarke Satz
 * ist der lebendigere der beiden und laesst sich nur so waehlen -- die
 * Fabriken `MotionScheme.standard()` und `.expressive()` sind `internal`.
 *
 * Er wirkt ueber die Bewegung hinaus: er setzt `LocalUsingExpressiveTheme`,
 * worauf einzelne Bauteile mit anderer Form antworten. Das ist gewollt.
 */
@Composable
fun NibraTheme(
    dunkel: Boolean = isSystemInDarkTheme(),
    inhalt: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LokaleBlobfarben provides blobfarben(),
        LokaleBewegungAus provides bewegungAus()
    ) {
        MaterialTheme(
            colorScheme = if (dunkel) Farben.dunkel else Farben.hell,
            typography = NibraTypografie,
            shapes = Formen.material,
            content = inhalt
        )
    }
}

/**
 * Liest die Blobfarben aus den Ressourcen. Hell und dunkel unterscheidet
 * Android selbst ueber `values-night` -- dieselbe Quelle, aus der auch der
 * Bedienungshilfen-Dienst liest.
 */
@Composable
private fun blobfarben(): Farben.Blobsatz = Farben.Blobsatz(
    a = colorResource(R.color.nb_blob_a),
    b = colorResource(R.color.nb_blob_b),
    c = colorResource(R.color.nb_blob_c),
    grund = colorResource(R.color.nb_blob_grund),
    symbol = colorResource(R.color.nb_blob_symbol)
)

/**
 * Liest einmal, ob der Nutzer Animationen abgeschaltet hat.
 *
 * `remember` ohne Schluessel ist hier richtig: die Einstellung aendert sich
 * nicht waehrend eines Bildschirms, und sie je Bild nachzuschlagen waere ein
 * Systemaufruf im Zeichenpfad.
 */
@Composable
private fun bewegungAus(): Boolean {
    val zusammenhang = LocalContext.current
    return remember(zusammenhang) {
        runCatching {
            Settings.Global.getFloat(
                zusammenhang.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}
