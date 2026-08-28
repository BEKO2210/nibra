package de.ithandwerkstuttgart.nibra

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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.unit.IntOffset
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
import de.ithandwerkstuttgart.nibra.dienst.DiktatBedienungshilfenDienst
import de.ithandwerkstuttgart.nibra.dienst.Dienstbruecke
import de.ithandwerkstuttgart.nibra.ui.NibraViewModel
import de.ithandwerkstuttgart.nibra.ui.Meldung
import de.ithandwerkstuttgart.nibra.ui.bildschirme.AufnahmeBildschirm
import de.ithandwerkstuttgart.nibra.ui.bildschirme.DatenschutzBildschirm
import de.ithandwerkstuttgart.nibra.ui.bildschirme.DiktatDetailBildschirm
import de.ithandwerkstuttgart.nibra.ui.bildschirme.DiktatspracheBildschirm
import de.ithandwerkstuttgart.nibra.ui.bildschirme.EinrichtungBildschirm
import de.ithandwerkstuttgart.nibra.ui.bildschirme.EinstellungenBildschirm
import de.ithandwerkstuttgart.nibra.ui.bildschirme.FremdsoftwareBildschirm
import de.ithandwerkstuttgart.nibra.ui.bildschirme.TextbausteineBildschirm
import de.ithandwerkstuttgart.nibra.ui.bildschirme.VerlaufBildschirm
import de.ithandwerkstuttgart.nibra.ui.gestalt.Bewegung
import de.ithandwerkstuttgart.nibra.ui.gestalt.NibraTheme

/**
 * Einstiegspunkt von Nibra. Verbindet die zustandslosen Bildschirme mit
 * [NibraViewModel]: lokale Ablage (Room/DataStore), Geraete-Erkennung,
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
            NibraTheme {
                NibraApp(
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
    const val DETAIL = "diktat"
    const val DETAIL_ARGUMENT = "diktatId"
    const val DETAIL_MUSTER = "$DETAIL/{$DETAIL_ARGUMENT}"

    fun detail(diktatId: String) = "$DETAIL/$diktatId"
}

@Composable
private fun NibraApp(
    mikrofonErteilt: () -> Boolean,
    dienstAktiv: () -> Boolean,
    mikrofonAnfordern: ((Boolean) -> Unit) -> Unit,
    bedienungshilfenOeffnen: () -> Unit,
    oberflaechenspracheOeffnen: () -> Unit,
    modifier: Modifier = Modifier,
    modell: NibraViewModel = hiltViewModel()
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
            ablage?.setPrimaryClip(ClipData.newPlainText("Nibra", text))
            // Ab Android 13 bestaetigt das System selbst; darunter meldet Nibra.
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
                // Zwischenablage, und Nibra sagt das auch.
                val ablage = context.getSystemService(ClipboardManager::class.java)
                ablage?.setPrimaryClip(ClipData.newPlainText("Nibra", text))
                modell.zeigeMeldung(Meldung.NICHT_EINGEFUEGT)
            }
        }
    }

    val meldungstext = zustand.meldung?.let { stringResource(meldungText(it)) }
    // Nur das Loeschen laesst sich zurueckholen -- und nur, solange der
    // Eintrag noch beiseiteliegt (Roadmap, Lauf 4.1).
    val rueckgaengigText = stringResource(R.string.sw_rueckgaengig)
    val bietetRueckgaengig =
        zustand.meldung == Meldung.DIKTAT_GELOESCHT && zustand.kannZurueckholen
    LaunchedEffect(zustand.meldung, meldungstext, bietetRueckgaengig) {
        val text = meldungstext ?: return@LaunchedEffect
        val antwort = meldungen.showSnackbar(
            message = text,
            actionLabel = if (bietetRueckgaengig) rueckgaengigText else null,
            withDismissAction = false,
            duration = SnackbarDuration.Short
        )
        if (antwort == SnackbarResult.ActionPerformed) modell.holeGeloeschtesZurueck()
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
    // Ein Bildschirm schiebt sich herein, der vorige tritt zurueck -- an
    // einer Stelle fuer alle Ziele. Das Schieben nimmt die raeumliche Feder,
    // das Ein- und Ausblenden die schnelle: Bewegung darf man sehen, ein
    // Farbwechsel soll nur wirken.
    //
    // Nur ein Drittel der Breite, nicht die ganze: ein Bildschirm, der von
    // ganz aussen hereinfaehrt, wirkt bei einer Feder trage.
    val schub = { breite: Int -> breite / 3 }
    // Die Uebergangs-Lambdas des NavHost sind nicht zusammensetzbar --
    // die Federn werden hier gelesen und hineingereicht.
    val schieben = Bewegung.raum<IntOffset>()
    val blenden = Bewegung.wirkung<Float>()
    NavHost(
        navController = navController,
        startDestination = startZiel,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            slideInHorizontally(schieben) { schub(it) } + fadeIn(blenden)
        },
        exitTransition = {
            slideOutHorizontally(schieben) { -schub(it) } + fadeOut(blenden)
        },
        popEnterTransition = {
            slideInHorizontally(schieben) { -schub(it) } + fadeIn(blenden)
        },
        popExitTransition = {
            slideOutHorizontally(schieben) { schub(it) } + fadeOut(blenden)
        }
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
                    aufKopieren = { kopiere(diktat.text) },
                    aufEinfuegen = { fuegeEin(diktat.text) },
                    aufTeilen = { teile(diktat.text) },
                    aufLoeschen = {
                        // Erst wenn der Eintrag wirklich weg ist, zurueck.
                        modell.loescheDiktat(diktat.id) { navController.popBackStack() }
                    },
                    aufTextSichern = { text -> modell.sichereText(diktat.id, text) },
                    aufZurueck = { navController.popBackStack() }
                )
            }
        }
        composable(Route.DIKTATSPRACHE) {
            DiktatspracheBildschirm(
                sprachen = zustand.sprachenMitVerlauf,
                gewaehlterCode = zustand.gewaehlterSprachCode,
                laedt = zustand.sprachenLaden,
                aufSprache = { sprache ->
                    modell.waehleSprache(sprache)
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
                aufOberflaechensprache = { oberflaechenspracheOeffnen() },
                aufDiktatsprache = {
                    modell.ladeSprachen()
                    navController.navigate(Route.DIKTATSPRACHE) { launchSingleTop = true }
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
    Meldung.DIKTAT_ZURUECKGEHOLT -> R.string.sw_meldung_diktat_zurueckgeholt
    Meldung.DIKTAT_GESICHERT -> R.string.sw_meldung_diktat_gesichert
    Meldung.BAUSTEIN_GESICHERT -> R.string.sw_meldung_baustein_gesichert
    Meldung.BAUSTEIN_GELOESCHT -> R.string.sw_meldung_baustein_geloescht
    Meldung.SPRACHE_WIRD_GELADEN -> R.string.sw_meldung_sprache_wird_geladen
}
