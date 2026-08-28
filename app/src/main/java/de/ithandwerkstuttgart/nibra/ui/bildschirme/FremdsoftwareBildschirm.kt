package de.ithandwerkstuttgart.nibra.ui.bildschirme

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.ithandwerkstuttgart.nibra.R
import de.ithandwerkstuttgart.nibra.ui.bausteine.Kachel
import de.ithandwerkstuttgart.nibra.ui.bausteine.Kopfzeile
import de.ithandwerkstuttgart.nibra.ui.gestalt.Abstand
import de.ithandwerkstuttgart.nibra.ui.gestalt.NibraTheme

/** Ein Lizenzhinweis, wie ihn der Bildschirm anzeigt. */
private data class Fremdsoftware(
    @StringRes val name: Int,
    @StringRes val lizenz: Int,
    @StringRes val text: Int
)

private val fremdsoftware = listOf(
    Fremdsoftware(
        name = R.string.sw_fremdsoftware_aidictation_name,
        lizenz = R.string.sw_fremdsoftware_aidictation_lizenz,
        text = R.string.sw_fremdsoftware_aidictation_text
    ),
    Fremdsoftware(
        name = R.string.sw_fremdsoftware_erkennung_name,
        lizenz = R.string.sw_fremdsoftware_erkennung_lizenz,
        text = R.string.sw_fremdsoftware_erkennung_text
    ),
    Fremdsoftware(
        name = R.string.sw_fremdsoftware_fraunces_name,
        lizenz = R.string.sw_fremdsoftware_fraunces_lizenz,
        text = R.string.sw_fremdsoftware_fraunces_text
    ),
    Fremdsoftware(
        name = R.string.sw_fremdsoftware_inter_name,
        lizenz = R.string.sw_fremdsoftware_inter_lizenz,
        text = R.string.sw_fremdsoftware_inter_text
    )
)

/**
 * Verwendete Fremdsoftware: die Arbeiten, auf denen Nibra aufbaut, mit
 * Lizenz und einem Satz dazu, wofuer sie in der App stehen.
 */
@Composable
fun FremdsoftwareBildschirm(
    aufZurueck: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Kopfzeile(titel = R.string.sw_fremdsoftware_titel, aufZurueck = aufZurueck)
        }
    ) { raender ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(raender)
                .padding(horizontal = Abstand.normal),
            contentPadding = PaddingValues(bottom = Abstand.gross),
            verticalArrangement = Arrangement.spacedBy(Abstand.klein)
        ) {
            item(key = "hinweis") {
                Text(
                    text = stringResource(R.string.sw_fremdsoftware_hinweis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Abstand.klein)
                )
            }
            items(fremdsoftware, key = { it.name }) { eintrag ->
                Kachel {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(eintrag.name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(eintrag.lizenz),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Abstand.winzig)
                        )
                        Text(
                            text = stringResource(eintrag.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Abstand.klein)
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Fremdsoftware", showBackground = true)
@Composable
private fun VorschauFremdsoftware() {
    NibraTheme {
        FremdsoftwareBildschirm(aufZurueck = {})
    }
}
