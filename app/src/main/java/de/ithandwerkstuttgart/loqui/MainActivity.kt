package de.ithandwerkstuttgart.loqui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.ithandwerkstuttgart.loqui.ui.bildschirme.AufnahmeBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.DatenschutzBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.DiktatDetailBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.DiktatspracheBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.EinrichtungBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.EinstellungenBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.FremdsoftwareBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.TextbausteineBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.VerlaufBildschirm
import de.ithandwerkstuttgart.loqui.dienst.DiktatBedienungshilfenDienst
import de.ithandwerkstuttgart.loqui.ui.gestalt.LoquiTheme
import de.ithandwerkstuttgart.loqui.ui.modell.Aufnahmezustand
import de.ithandwerkstuttgart.loqui.ui.modell.Diktat
import de.ithandwerkstuttgart.loqui.ui.modell.Diktatsprache
import de.ithandwerkstuttgart.loqui.ui.modell.Dienstzustand
import de.ithandwerkstuttgart.loqui.ui.modell.Einstellungen
import de.ithandwerkstuttgart.loqui.ui.modell.Gruppenschluessel
import de.ithandwerkstuttgart.loqui.ui.modell.Mikrofonzustand
import de.ithandwerkstuttgart.loqui.ui.modell.Textbaustein
import de.ithandwerkstuttgart.loqui.ui.modell.VerlaufGruppe

/**
 * Einstiegspunkt von Loqui. Bindet die von Station 4 geschriebenen
 * Bildschirme (rein zustandslose Composables) ueber eine Navigation
 * zusammen. Der Zustand liegt hier noch in `remember` -- lokale
 * Persistenz (Room/DataStore) und die echte Spracherkennung sind noch
 * nicht verdrahtet, siehe TODOs an den jeweiligen Stellen.
 */
class MainActivity : ComponentActivity() {

    private val mikrofonAnfrage = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { erteilt -> onMikrofonErgebnis?.invoke(erteilt) }

    private var onMikrofonErgebnis: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoquiTheme {
                LoquiApp(
                    mikrofonErteilt = { hatMikrofonRecht() },
                    dienstAktiv = { istBedienungshilfenDienstAktiv() },
                    mikrofonAnfordern = { rueckruf ->
                        onMikrofonErgebnis = rueckruf
                        mikrofonAnfrage.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    bedienungshilfenOeffnen = { oeffneBedienungshilfenEinstellungen() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun hatMikrofonRecht(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun istBedienungshilfenDienstAktiv(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        val dienstklasse = DiktatBedienungshilfenDienst::class.java.name
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val dienstInfo = info.resolveInfo?.serviceInfo ?: return@any false
                val klassenname = if (dienstInfo.name.startsWith('.')) {
                    dienstInfo.packageName + dienstInfo.name
                } else {
                    dienstInfo.name
                }
                dienstInfo.packageName == packageName && klassenname == dienstklasse
            }
    }

    private fun oeffneBedienungshilfenEinstellungen() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

private object Route {
    const val EINRICHTUNG = "einrichtung"
    const val AUFNAHME = "aufnahme"
    const val VERLAUF = "verlauf"
    const val TEXTBAUSTEINE = "textbausteine"
    const val EINSTELLUNGEN = "einstellungen"
    const val DATENSCHUTZ = "datenschutz"
    const val FREMDSOFTWARE = "fremdsoftware"
    const val DIKTATSPRACHE = "diktatsprache"
    const val DETAIL = "diktat"
    const val DETAIL_ARGUMENT = "diktatId"
    const val DETAIL_MUSTER = "$DETAIL/{$DETAIL_ARGUMENT}"

    fun detail(diktatId: String) = "$DETAIL/$diktatId"
}

private fun baueGruppen(diktate: List<Diktat>, suchbegriff: String): List<VerlaufGruppe> =
    diktate
        .filter { suchbegriff.isBlank() || it.text.contains(suchbegriff, ignoreCase = true) }
        .groupBy { it.datum }
        .map { (datum, eintraege) ->
            VerlaufGruppe(
                schluessel = Gruppenschluessel.AELTER,
                eigenesDatum = datum,
                diktate = eintraege.sortedByDescending(Diktat::zeitpunktMillis)
            )
        }

@androidx.compose.runtime.Composable
private fun LoquiApp(
    mikrofonErteilt: () -> Boolean,
    dienstAktiv: () -> Boolean,
    mikrofonAnfordern: ((Boolean) -> Unit) -> Unit,
    bedienungshilfenOeffnen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController: NavHostController = rememberNavController()
    var mikrofonZustand by remember {
        mutableStateOf(
            if (mikrofonErteilt()) Mikrofonzustand.ERTEILT else Mikrofonzustand.NICHT_ERTEILT
        )
    }
    var dienstZustand by remember {
        mutableStateOf(
            if (dienstAktiv()) Dienstzustand.EINGERICHTET
            else Dienstzustand.NICHT_EINGERICHTET
        )
    }
    var eingerichtet by remember { mutableStateOf(false) }
    var aufnahmezustand by remember { mutableStateOf<Aufnahmezustand>(Aufnahmezustand.Bereit) }
    var textbausteine by remember { mutableStateOf<List<Textbaustein>>(emptyList()) }
    // Frisch installiert ist beides leer: keine Diktate, und die
    // Sprachliste meldet erst etwas, wenn Station 4 das Geraet befragt.
    // TODO(Station 4): Diktate aus der lokalen Ablage laden.
    var diktate by remember { mutableStateOf<List<Diktat>>(emptyList()) }
    var suchbegriff by remember { mutableStateOf("") }
    // TODO(Station 4): verfuegbare Sprachen ueber
    // RecognitionSupport / getSupportedLanguages beim Geraet erfragen.
    val sprachen = remember { emptyList<Diktatsprache>() }
    var gewaehlterSprachCode by remember { mutableStateOf("") }
    var einstellungen by remember {
        mutableStateOf(
            Einstellungen(
                dienstzustand = dienstZustand,
                mikrofonzustand = mikrofonZustand,
                oberflaechenspracheName = "Deutsch",
                diktatspracheName = "Deutsch"
            )
        )
    }
    val zustaendeAktualisieren = {
        mikrofonZustand =
            if (mikrofonErteilt()) Mikrofonzustand.ERTEILT else Mikrofonzustand.NICHT_ERTEILT
        dienstZustand =
            if (dienstAktiv()) Dienstzustand.EINGERICHTET else Dienstzustand.NICHT_EINGERICHTET
    }

    NavHost(
        navController = navController,
        startDestination = if (eingerichtet) Route.AUFNAHME else Route.EINRICHTUNG,
        modifier = modifier
    ) {
        composable(Route.EINRICHTUNG) {
            EinrichtungBildschirm(
                mikrofonzustand = mikrofonZustand,
                dienstzustand = dienstZustand,
                aufZustaendeAktualisieren = zustaendeAktualisieren,
                aufMikrofonErlauben = {
                    mikrofonAnfordern { erteilt ->
                        mikrofonZustand =
                            if (erteilt) Mikrofonzustand.ERTEILT else Mikrofonzustand.NICHT_ERTEILT
                    }
                },
                aufDienstAktivieren = { bedienungshilfenOeffnen() },
                aufSpaeter = {
                    eingerichtet = true
                    navController.navigate(Route.AUFNAHME)
                },
                aufFertig = {
                    eingerichtet = true
                    navController.navigate(Route.AUFNAHME)
                }
            )
        }
        composable(Route.AUFNAHME) {
            AufnahmeBildschirm(
                zustand = aufnahmezustand,
                aufAufnahmeUmschalten = {
                    // TODO(Station 4): SpeechRecognizer.createOnDeviceSpeechRecognizer
                    // (API 33+) bzw. SpeechRecognizer mit EXTRA_PREFER_OFFLINE
                    // verdrahten (AUFTRAG.md, Nachtrag "Spracherkennung").
                    aufnahmezustand = when (aufnahmezustand) {
                        is Aufnahmezustand.Bereit -> Aufnahmezustand.Laeuft(
                            pegel = 0f, dauerSekunden = 0, verlauf = emptyList()
                        )
                        else -> Aufnahmezustand.Bereit
                    }
                },
                aufErneutVersuchen = { aufnahmezustand = Aufnahmezustand.Bereit },
                aufVerlauf = { navController.navigate(Route.VERLAUF) },
                aufEinstellungen = { navController.navigate(Route.EINSTELLUNGEN) }
            )
        }
        composable(Route.VERLAUF) {
            VerlaufBildschirm(
                gruppen = baueGruppen(diktate, suchbegriff),
                suchbegriff = suchbegriff,
                aufSuchbegriff = { suchbegriff = it },
                aufDiktat = { diktat -> navController.navigate(Route.detail(diktat.id)) },
                aufErstesDiktat = { navController.navigate(Route.AUFNAHME) },
                aufZurueck = { navController.popBackStack() }
            )
        }
        composable(Route.DETAIL_MUSTER) { eintrag ->
            val id = eintrag.arguments?.getString(Route.DETAIL_ARGUMENT)
            val diktat = diktate.firstOrNull { it.id == id }
            if (diktat == null) {
                // Der Eintrag wurde inzwischen geloescht -- zurueck zum Verlauf.
                LaunchedEffect(id) { navController.popBackStack() }
            } else {
                DiktatDetailBildschirm(
                    diktat = diktat,
                    erneuteErkennungLaeuft = false,
                    // TODO(Station 4): Zwischenablage, Teilen-Absicht und das
                    // Einfuegen ueber den Bedienungshilfen-Dienst verdrahten.
                    aufKopieren = {},
                    aufEinfuegen = {},
                    aufTeilen = {},
                    aufLoeschen = {
                        diktate = diktate.filterNot { it.id == diktat.id }
                        navController.popBackStack()
                    },
                    aufSpracheUmschalten = { navController.navigate(Route.DIKTATSPRACHE) },
                    aufErneutErkennen = {},
                    aufZurueck = { navController.popBackStack() }
                )
            }
        }
        composable(Route.DIKTATSPRACHE) {
            DiktatspracheBildschirm(
                sprachen = sprachen,
                gewaehlterCode = gewaehlterSprachCode,
                aufSprache = { sprache ->
                    gewaehlterSprachCode = sprache.code
                    einstellungen = einstellungen.copy(diktatspracheName = sprache.name)
                    navController.popBackStack()
                },
                aufZurueck = { navController.popBackStack() }
            )
        }
        composable(Route.TEXTBAUSTEINE) {
            TextbausteineBildschirm(
                bausteine = textbausteine,
                aufSichern = { baustein ->
                    textbausteine = textbausteine
                        .filterNot { it.id == baustein.id } + baustein
                },
                aufLoeschen = { baustein ->
                    textbausteine = textbausteine.filterNot { it.id == baustein.id }
                },
                aufZurueck = { navController.popBackStack() }
            )
        }
        composable(Route.EINSTELLUNGEN) {
            EinstellungenBildschirm(
                // Mikrofon- und Dienstzustand kommen immer frisch aus dem
                // laufenden Zustand, nicht aus der beim Start gebauten Kopie.
                einstellungen = einstellungen.copy(
                    mikrofonzustand = mikrofonZustand,
                    dienstzustand = dienstZustand
                ),
                aufZustaendeAktualisieren = zustaendeAktualisieren,
                aufStoppBeiStille = { an ->
                    einstellungen = einstellungen.copy(stoppBeiStille = an)
                },
                aufAufnahmenBehalten = { an ->
                    einstellungen = einstellungen.copy(aufnahmenBehalten = an)
                },
                // TODO(Station 5): Oberflaechensprache ueber die
                // App-Sprachen des Systems umstellen.
                aufOberflaechensprache = {},
                aufDiktatsprache = { navController.navigate(Route.DIKTATSPRACHE) },
                aufMikrofonErlauben = {
                    mikrofonAnfordern { erteilt ->
                        mikrofonZustand =
                            if (erteilt) Mikrofonzustand.ERTEILT else Mikrofonzustand.NICHT_ERTEILT
                    }
                },
                aufDienstEinrichten = { bedienungshilfenOeffnen() },
                aufTextbausteine = { navController.navigate(Route.TEXTBAUSTEINE) },
                aufDatenschutz = { navController.navigate(Route.DATENSCHUTZ) },
                aufFremdsoftware = { navController.navigate(Route.FREMDSOFTWARE) },
                aufZurueck = { navController.popBackStack() }
            )
        }
        composable(Route.DATENSCHUTZ) {
            DatenschutzBildschirm(aufZurueck = { navController.popBackStack() })
        }
        composable(Route.FREMDSOFTWARE) {
            FremdsoftwareBildschirm(aufZurueck = { navController.popBackStack() })
        }
    }
}
