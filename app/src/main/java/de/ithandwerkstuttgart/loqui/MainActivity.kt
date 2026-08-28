package de.ithandwerkstuttgart.loqui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.LocaleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.ithandwerkstuttgart.loqui.dienst.DiktatBedienungshilfenDienst
import de.ithandwerkstuttgart.loqui.dienst.Dienstbruecke
import de.ithandwerkstuttgart.loqui.ui.LoquiViewModel
import de.ithandwerkstuttgart.loqui.ui.Meldung
import de.ithandwerkstuttgart.loqui.ui.bildschirme.AufnahmeBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.DatenschutzBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.DiktatDetailBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.DiktatspracheBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.EinrichtungBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.EinstellungenBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.FremdsoftwareBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.TextbausteineBildschirm
import de.ithandwerkstuttgart.loqui.ui.bildschirme.VerlaufBildschirm
import de.ithandwerkstuttgart.loqui.ui.gestalt.LoquiTheme

/**
 * Einstiegspunkt von Loqui. Verbindet die zustandslosen Bildschirme mit
 * [LoquiViewModel]: lokale Ablage (Room/DataStore), Geraete-Erkennung,
 * Zwischenablage, Teilen und das Einfuegen ueber den Bedienungshilfen-Dienst.
 */
@AndroidEntryPoint
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
                    oberflaechenspracheOeffnen = { oeffneSpracheinstellungen() },
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

    /**
     * Die Oberflaechensprache stellt Android selbst um (App-Sprachen ab
     * API 33). Aeltere Geraete folgen der Systemsprache -- dort fuehrt der
     * Weg in die App-Einstellungen.
     */
    private fun oeffneSpracheinstellungen() {
        val absicht = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        }
        runCatching { startActivity(absicht) }.onFailure {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
            )
        }
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
    const val SPRACHE_ARGUMENT = "diktatId"
    const val DIKTATSPRACHE_MUSTER = "$DIKTATSPRACHE?$SPRACHE_ARGUMENT={$SPRACHE_ARGUMENT}"
    const val DETAIL = "diktat"
    const val DETAIL_ARGUMENT = "diktatId"
    const val DETAIL_MUSTER = "$DETAIL/{$DETAIL_ARGUMENT}"

    fun detail(diktatId: String) = "$DETAIL/$diktatId"

    /** Ohne Eintrag: Voreinstellung fuer neue Diktate. Mit Eintrag: nur
     *  dessen Sprache. */
    fun diktatsprache(diktatId: String? = null) =
        if (diktatId == null) DIKTATSPRACHE else "$DIKTATSPRACHE?$SPRACHE_ARGUMENT=$diktatId"
}

@Composable
private fun LoquiApp(
    mikrofonErteilt: () -> Boolean,
    dienstAktiv: () -> Boolean,
    mikrofonAnfordern: ((Boolean) -> Unit) -> Unit,
    bedienungshilfenOeffnen: () -> Unit,
    oberflaechenspracheOeffnen: () -> Unit,
    modifier: Modifier = Modifier,
    modell: LoquiViewModel = hiltViewModel()
) {
    val zustand by modell.zustand.collectAsStateWithLifecycle()
    val navController: NavHostController = rememberNavController()
    val context = LocalContext.current
    val lebenslauf = LocalLifecycleOwner.current
    val fokus = LocalFocusManager.current
    val meldungen = remember { SnackbarHostState() }

    // Rechte und Dienst koennen sich ausserhalb der App aendern -- bei jeder
    // Rueckkehr neu pruefen.
    DisposableEffect(lebenslauf) {
        val beobachter = LifecycleEventObserver { _, ereignis ->
            if (ereignis == Lifecycle.Event.ON_RESUME) {
                modell.meldeZustaende(mikrofonErteilt(), dienstAktiv())
            }
        }
        lebenslauf.lifecycle.addObserver(beobachter)
        onDispose { lebenslauf.lifecycle.removeObserver(beobachter) }
    }

    val kopiere: (String) -> Unit = remember(context, modell) {
        { text ->
            val ablage = context.getSystemService(ClipboardManager::class.java)
            ablage?.setPrimaryClip(ClipData.newPlainText("Loqui", text))
            // Ab Android 13 bestaetigt das System selbst; darunter meldet Loqui.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                modell.zeigeMeldung(Meldung.KOPIERT)
            }
        }
    }
    val teile: (String) -> Unit = remember(context) {
        { text ->
            val absicht = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(absicht, null))
        }
    }
    val fuegeEin: (String) -> Unit = remember(context, modell) {
        { text ->
            if (Dienstbruecke.fuegeEin(text)) {
                modell.zeigeMeldung(Meldung.EINGEFUEGT)
            } else {
                // Kein Feld im Zugriff: der Text liegt dann wenigstens in der
                // Zwischenablage, und Loqui sagt das auch.
                val ablage = context.getSystemService(ClipboardManager::class.java)
                ablage?.setPrimaryClip(ClipData.newPlainText("Loqui", text))
                modell.zeigeMeldung(Meldung.NICHT_EINGEFUEGT)
            }
        }
    }

    val meldungstext = zustand.meldung?.let { stringResource(meldungText(it)) }
    LaunchedEffect(zustand.meldung, meldungstext) {
        val text = meldungstext ?: return@LaunchedEffect
        meldungen.showSnackbar(text)
        modell.meldungGezeigt()
    }

    // Solange die Ablage noch antwortet, bleibt die Flaeche ruhig leer --
    // kein Aufblitzen des falschen Startbildschirms.
    if (!zustand.geladen) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    // Das Startziel steht beim ersten Zeichnen fest; spaetere Wechsel
    // laufen ueber die Navigation, nicht ueber ein neues Startziel.
    val startZiel = rememberSaveable(zustand.eingerichtet) {
        if (zustand.eingerichtet) Route.AUFNAHME else Route.EINRICHTUNG
    }

    Box(modifier = modifier) {
    NavHost(
        navController = navController,
        startDestination = startZiel,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Route.EINRICHTUNG) {
            EinrichtungBildschirm(
                mikrofonzustand = zustand.mikrofonzustand,
                dienstzustand = zustand.dienstzustand,
                aufZustaendeAktualisieren = {
                    modell.meldeZustaende(mikrofonErteilt(), dienstAktiv())
                },
                aufMikrofonErlauben = {
                    mikrofonAnfordern { _ ->
                        modell.meldeZustaende(mikrofonErteilt(), dienstAktiv())
                    }
                },
                aufDienstAktivieren = { bedienungshilfenOeffnen() },
                aufSpaeter = {
                    modell.merkeEingerichtet()
                    navController.navigate(Route.AUFNAHME) {
                        popUpTo(Route.EINRICHTUNG) { inclusive = true }
                    }
                },
                aufFertig = {
                    modell.merkeEingerichtet()
                    modell.ladeSprachen()
                    navController.navigate(Route.AUFNAHME) {
                        popUpTo(Route.EINRICHTUNG) { inclusive = true }
                    }
                }
            )
        }
        composable(Route.AUFNAHME) {
            AufnahmeBildschirm(
                zustand = zustand.aufnahme,
                aufAufnahmeUmschalten = { modell.aufnahmeUmschalten() },
                aufErneutVersuchen = { modell.erneutVersuchen() },
                aufVerlauf = { navController.navigate(Route.VERLAUF) { launchSingleTop = true } },
                aufEinstellungen = {
                    navController.navigate(Route.EINSTELLUNGEN) { launchSingleTop = true }
                },
                letztesDiktat = zustand.letztesDiktat,
                aufLetztesKopieren = { zustand.letztesDiktat?.let { kopiere(it.text) } },
                aufLetztesEinfuegen = { zustand.letztesDiktat?.let { fuegeEin(it.text) } },
                aufLetztesOeffnen = {
                    zustand.letztesDiktat?.let {
                        navController.navigate(Route.detail(it.id)) { launchSingleTop = true }
                    }
                }
            )
        }
        composable(Route.VERLAUF) {
            VerlaufBildschirm(
                gruppen = zustand.gruppen,
                suchbegriff = zustand.suchbegriff,
                aufSuchbegriff = modell::setzeSuchbegriff,
                aufDiktat = { diktat ->
                    // Tastatur und Fokus zuerst weg, sonst kommt die Tastatur
                    // beim Zurueckkehren sofort wieder hoch.
                    fokus.clearFocus()
                    navController.navigate(Route.detail(diktat.id)) { launchSingleTop = true }
                },
                aufErstesDiktat = { navController.popBackStack(Route.AUFNAHME, false) },
                aufZurueck = {
                    fokus.clearFocus()
                    modell.setzeSuchbegriff("")
                    navController.popBackStack()
                }
            )
        }
        composable(Route.DETAIL_MUSTER) { eintrag ->
            val id = eintrag.arguments?.getString(Route.DETAIL_ARGUMENT)
            val diktat = zustand.diktate.firstOrNull { it.id == id }
            if (diktat == null) {
                // Erst zuruecknavigieren, wenn der Verlauf da ist -- sonst
                // schliesst sich der Bildschirm waehrend des Ladens.
                if (zustand.verlaufGeladen) {
                    LaunchedEffect(id) { navController.popBackStack() }
                }
            } else {
                DiktatDetailBildschirm(
                    diktat = diktat,
                    erneuteErkennungLaeuft = zustand.erneuteErkennungFuer == diktat.id,
                    aufKopieren = { kopiere(diktat.text) },
                    aufEinfuegen = { fuegeEin(diktat.text) },
                    aufTeilen = { teile(diktat.text) },
                    aufLoeschen = {
                        // Erst wenn der Eintrag wirklich weg ist, zurueck.
                        modell.loescheDiktat(diktat.id) { navController.popBackStack() }
                    },
                    aufSpracheUmschalten = {
                        navController.navigate(Route.diktatsprache(diktat.id)) {
                            launchSingleTop = true
                        }
                    },
                    aufErneutErkennen = {
                        // Aufnehmen passiert auf der Aufnahmeflaeche: dort gibt
                        // es Dauer, Pegel, Stopp und Fehlertext.
                        modell.erneutErkennen(diktat.id)
                        navController.navigate(Route.AUFNAHME) { launchSingleTop = true }
                    },
                    aufZurueck = { navController.popBackStack() }
                )
            }
        }
        composable(Route.DIKTATSPRACHE_MUSTER) { eintrag ->
            val diktatId = eintrag.arguments?.getString(Route.SPRACHE_ARGUMENT)
            val diktat = diktatId?.let { kennung ->
                zustand.diktate.firstOrNull { it.id == kennung }
            }
            DiktatspracheBildschirm(
                sprachen = zustand.sprachenMitVerlauf,
                gewaehlterCode = diktat?.sprachCode ?: zustand.gewaehlterSprachCode,
                laedt = zustand.sprachenLaden,
                aufSprache = { sprache ->
                    if (diktat != null) {
                        modell.setzeSpracheDesDiktats(diktat.id, sprache)
                    } else {
                        modell.waehleSprache(sprache)
                    }
                    navController.popBackStack()
                },
                aufZurueck = { navController.popBackStack() }
            )
        }
        composable(Route.TEXTBAUSTEINE) {
            TextbausteineBildschirm(
                bausteine = zustand.textbausteine,
                aufSichern = modell::sichereBaustein,
                aufLoeschen = modell::loescheBaustein,
                aufZurueck = { navController.popBackStack() }
            )
        }
        composable(Route.EINSTELLUNGEN) {
            EinstellungenBildschirm(
                einstellungen = modell.einstellungenFuer(zustand),
                aufZustaendeAktualisieren = {
                    modell.meldeZustaende(mikrofonErteilt(), dienstAktiv())
                },
                aufStoppBeiStille = modell::setzeStoppBeiStille,
                aufAufnahmenBehalten = modell::setzeAufnahmenBehalten,
                aufOberflaechensprache = { oberflaechenspracheOeffnen() },
                aufDiktatsprache = {
                    modell.ladeSprachen()
                    navController.navigate(Route.diktatsprache()) { launchSingleTop = true }
                },
                aufMikrofonErlauben = {
                    mikrofonAnfordern { _ ->
                        modell.meldeZustaende(mikrofonErteilt(), dienstAktiv())
                    }
                },
                aufDienstEinrichten = { bedienungshilfenOeffnen() },
                aufTextbausteine = {
                    navController.navigate(Route.TEXTBAUSTEINE) { launchSingleTop = true }
                },
                aufDatenschutz = {
                    navController.navigate(Route.DATENSCHUTZ) { launchSingleTop = true }
                },
                aufFremdsoftware = {
                    navController.navigate(Route.FREMDSOFTWARE) { launchSingleTop = true }
                },
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
        SnackbarHost(
            hostState = meldungen,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Text zu einer Rueckmeldung -- immer Klartext, nie ein Code. */
private fun meldungText(meldung: Meldung): Int = when (meldung) {
    Meldung.KOPIERT -> R.string.sw_detail_kopiert
    Meldung.EINGEFUEGT -> R.string.sw_meldung_eingefuegt
    Meldung.NICHT_EINGEFUEGT -> R.string.sw_meldung_nicht_eingefuegt
    Meldung.DIKTAT_GELOESCHT -> R.string.sw_meldung_diktat_geloescht
    Meldung.BAUSTEIN_GESICHERT -> R.string.sw_meldung_baustein_gesichert
    Meldung.BAUSTEIN_GELOESCHT -> R.string.sw_meldung_baustein_geloescht
    Meldung.SPRACHE_WIRD_GELADEN -> R.string.sw_meldung_sprache_wird_geladen
}
