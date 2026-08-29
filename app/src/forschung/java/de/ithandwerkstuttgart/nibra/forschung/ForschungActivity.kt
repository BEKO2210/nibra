package de.ithandwerkstuttgart.nibra.forschung

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import de.ithandwerkstuttgart.nibra.daten.EinstellungenAblage
import de.ithandwerkstuttgart.nibra.ui.gestalt.NibraTheme
import java.io.File
import kotlin.concurrent.thread

/**
 * Der Messplatz der Forschungsausprägung.
 *
 * Die Oberfläche ist schlicht, aber nicht lieblos: Belkis liest hier
 * dreißig Sekunden lang einen Text vor und schaut dabei **auf den Text**,
 * nicht auf eine Statuszeile. Deshalb ist das Signal „jetzt sprechen" kein
 * kleines Wort am Rand, sondern der ganze Bildschirmgrund — das nimmt man
 * im Augenwinkel wahr, ohne die Zeile zu verlieren.
 *
 * Der Bildschirm bleibt an, und das Gerät bleibt im Hochformat: einmal
 * drehen mitten im Lauf würde die Messung verderben.
 */
class ForschungActivity : ComponentActivity() {

    private sealed interface Sicht {
        data object Bereit : Sicht
        data class Läuft(val stand: Sprachlauf.Stand) : Sicht
        data class Fertig(val bericht: String, val pfad: String) : Sicht
        data class Fehlt(val grund: String) : Sicht
    }

    private var sicht by mutableStateOf<Sicht>(Sicht.Bereit)

    /** Für den Bereitschirm: derselbe Text, den der Lauf verwenden wird. */
    private var angezeigteSprache by mutableStateOf(
        java.util.Locale.getDefault().toLanguageTag()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            sicht = Sicht.Fehlt("Mikrofonrecht fehlt. Ohne das misst hier nichts.")
        }

        // Für den stillen Probelauf über adb: startet ohne Tippen. Im
        // echten Lauf ist das Tippen wichtig -- es beginnt erst, wenn der
        // Sprecher bereit ist.
        if (intent.getBooleanExtra("sofort", false) && sicht is Sicht.Bereit) {
            starteSprachlauf()
        }
        if (intent.getBooleanExtra("vorlauf", false) && sicht is Sicht.Bereit) {
            thread {
                val pcm = File(getExternalFilesDir(null), messSpur())
                if (!pcm.exists()) {
                    lege("vorlaufversuch.txt", "Es fehlt ${pcm.absolutePath}.")
                    return@thread
                }
                val versuch = Vorlaufversuch(this, messSprache()) { stand ->
                    sicht = Sicht.Läuft(Sprachlauf.Stand("Vorlauf", stand, false, 0))
                        meldeFortschritt("Vorlauf: $stand")
                }
                lege(
                    "vorlaufversuch.txt",
                    versuch.fuehreDurch(
                        pcm.readBytes(),
                        intent.getStringExtra("satz") ?: VORLAUFSATZ,
                        intent.getStringExtra("anker") ?: ANKERWORT
                    )
                )
            }
        }
        if (intent.getBooleanExtra("dauer", false) && sicht is Sicht.Bereit) {
            thread {
                val pcm = File(getExternalFilesDir(null), messSpur())
                if (!pcm.exists()) {
                    lege("dauerversuch.txt", "Es fehlt ${pcm.absolutePath}.")
                    return@thread
                }
                // Einzelne Dauer per --ei sekunden, sonst alle drei. Der
                // volle Satz braucht über zwanzig Minuten; für einen
                // Zwischenstand will man oft nur die kurze.
                val einzeln = intent.getIntExtra("sekunden", 0)
                val dauern = if (einzeln > 0) {
                    listOf(einzeln * 1000L)
                } else {
                    Dauerversuch.DAUERN
                }
                val versuch = Dauerversuch(this, messSprache()) { stand ->
                    sicht = Sicht.Läuft(Sprachlauf.Stand("Dauerlauf", stand, false, 0))
                        meldeFortschritt("Dauerlauf: $stand")
                }
                lege("dauerversuch.txt", versuch.fuehreDurch(pcm.readBytes(), dauern))
            }
        }
        // Fall I baut die Oberfläche neu auf. Danach läuft onCreate ein
        // zweites Mal, mit derselben Absicht -- ohne diesen Riegel liefen
        // zwei Versuche gleichzeitig und stritten um das Mikrofon. Genau
        // der Fall, den der Versuch prüfen soll, würde ihn dann sprengen.
        if (intent.getBooleanExtra("lebenslauf", false) && sicht is Sicht.Bereit &&
            laeuftSchon.compareAndSet(false, true)) {
            thread {
                val pcm = File(getExternalFilesDir(null), messSpur())
                if (!pcm.exists()) {
                    lege("lebenslauf.txt", "Es fehlt ${pcm.absolutePath}.")
                    return@thread
                }
                val versuch = Lebenslaufversuch(
                    zusammenhang = this,
                    sprache = messSprache(),
                    aufStand = { stand ->
                        sicht = Sicht.Läuft(Sprachlauf.Stand("Lebenslauf", stand, false, 0))
                        meldeFortschritt("Lebenslauf: $stand")
                    },
                    aufHintergrund = { moveTaskToBack(true) },
                    aufVordergrund = {
                        // Sich selbst wieder nach vorn holen. Über einen
                        // Neustart der eigenen Absicht, weil eine App sich
                        // sonst nicht aus dem Hintergrund holen kann.
                        startActivity(
                            Intent(this, ForschungActivity::class.java).addFlags(
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        )
                    },
                    aufNeuaufbau = { recreate() }
                )
                lege("lebenslauf.txt", versuch.fuehreDurch(
                    pcm.readBytes(), intent.getStringExtra("nur")
                ))
            }
        }
        if (intent.getBooleanExtra("verzug", false) && sicht is Sicht.Bereit) {
            thread {
                val pcm = File(getExternalFilesDir(null), messSpur())
                if (!pcm.exists()) {
                    lege("verzug.txt", "Es fehlt ${pcm.absolutePath}.")
                    return@thread
                }
                val wie = intent.getIntExtra("laeufe", Verzugsversuch.WIEDERHOLUNGEN)
                val versuch = Verzugsversuch(this, messSprache()) { stand ->
                    sicht = Sicht.Läuft(Sprachlauf.Stand("Verzug", stand, false, 0))
                        meldeFortschritt("Verzug: $stand")
                }
                lege("verzug.txt", versuch.fuehreDurch(pcm.readBytes(), wie))
            }
        }
        if (intent.getBooleanExtra("vorgabe", false) && sicht is Sicht.Bereit) {
            thread {
                val pcm = File(getExternalFilesDir(null), "biasing.pcm")
                if (!pcm.exists()) {
                    lege("vorgabe.txt", "Es fehlt ${pcm.absolutePath}.")
                    return@thread
                }
                val paare = intent.getIntExtra("paare", Biasingversuch.PAARE)
                val versuch = Biasingversuch(this) { stand ->
                    sicht = Sicht.Läuft(Sprachlauf.Stand("Vorgabeliste", stand, false, 0))
                        meldeFortschritt("Vorgabeliste: $stand")
                }
                lege("vorgabe.txt", versuch.fuehreDurch(
                    pcm.readBytes(), Biasingversuch.SATZ, Biasingversuch.NAMEN, paare
                ))
            }
        }
        if (intent.getBooleanExtra("transport", false) && sicht is Sicht.Bereit) {
            thread {
                val einzeln = intent.getIntExtra("sekunden", 0)
                val dauern = if (einzeln > 0) listOf(einzeln * 1000L) else Dauerversuch.DAUERN
                val versuch = Streckendauerlauf(this) { stand ->
                    sicht = Sicht.Läuft(Sprachlauf.Stand("Transport", stand, false, 0))
                        meldeFortschritt("Transport: $stand")
                }
                lege("transport.txt", versuch.fuehreDurch(dauern))
            }
        }
        if (intent.getBooleanExtra("sitzungen", false) && sicht is Sicht.Bereit) {
            thread {
                val pcm = File(getExternalFilesDir(null), messSpur())
                if (!pcm.exists()) {
                    lege("sitzungen.txt", "Es fehlt ${pcm.absolutePath}.")
                    return@thread
                }
                val wie = intent.getIntExtra("anzahl", Sitzungsdauerlauf.SITZUNGEN)
                val versuch = Sitzungsdauerlauf(this, messSprache()) { stand ->
                    sicht = Sicht.Läuft(Sprachlauf.Stand("Sitzungen", stand, false, 0))
                        meldeFortschritt("Sitzungen: $stand")
                }
                lege("sitzungen.txt", versuch.fuehreDurch(pcm.readBytes(), wie))
            }
        }
        if (intent.getBooleanExtra("vergleich", false) && sicht is Sicht.Bereit) {
            thread {
                val ton = File(getExternalFilesDir(null), "vergleich.wav")
                val bezugsdatei = File(getExternalFilesDir(null), "vergleich-bezug.txt")
                if (!ton.exists() || !bezugsdatei.exists()) {
                    lege("vergleich.txt", "Es fehlt ${ton.absolutePath} oder ${bezugsdatei.absolutePath}.")
                    return@thread
                }
                val paare = intent.getIntExtra("paare", Vergleichsversuch.PAARE)
                val versuch = Vergleichsversuch(this, messSprache()) { stand ->
                    sicht = Sicht.Läuft(Sprachlauf.Stand("Vergleich", stand, false, 0))
                    meldeFortschritt("Vergleich: $stand")
                }
                lege("vergleich.txt", versuch.fuehreDurch(
                    aufnahme = ton,
                    bezugstext = bezugsdatei.readText().trim(),
                    klassen = Fehlerarten.klassenAus(
                        // Nur für die Messung. Im Programm kommen diese
                        // Wörter später aus dem Wörterbuch des Nutzers.
                        eigennamen = listOf("Belkis", "Aslani", "Weinreich"),
                        fachbegriffe = listOf("Nibra", "Spracherkennung", "Konferenzraum", "Bauteilen"),
                        zahlen = listOf("vierzehn", "dreißig", "drei", "zweihundertvierzig", "achthundert")
                    ),
                    paare = paare
                ))
            }
        }
        if (intent.getBooleanExtra("livestrecke", false) && sicht is Sicht.Bereit) {
            thread {
                val versuch = Livestreckenversuch(this) { stand ->
                    sicht = Sicht.Läuft(
                        Sprachlauf.Stand(
                            "Livestrecke", stand,
                            stand.contains("JETZT"), 0
                        )
                    )
                }
                lege(
                    "livestrecke.txt",
                    versuch.fuehreDurch(VORLESESATZ, VERZOEGERUNG_MILLIS)
                )
            }
        }
        if (intent.getBooleanExtra("tonquelle", false) && sicht is Sicht.Bereit) {
            thread {
                // Die Testaufnahme liegt neben dem Bericht, damit sie sich
                // per adb hineinlegen lässt. Ohne bekannte Aufnahme wäre
                // der Versuch nicht auswertbar: es ginge nicht zu trennen,
                // ob der Erkenner den Strom gelesen oder das Mikrofon
                // geöffnet hat.
                val ordner = getExternalFilesDir(null)
                val pcm = File(ordner, "spike.pcm")
                val bezug = File(ordner, "spike-bezug.txt")
                if (!pcm.exists()) {
                    lege(
                        "tonquellenversuch.txt",
                        "Es fehlt ${pcm.absolutePath}.\n" +
                            "Roh-PCM erwartet: 16000 Hz, 16 Bit, ein Kanal, ohne Kopf."
                    )
                    return@thread
                }
                val versuch = Tonquellenversuch(this) { stand ->
                    sicht = Sicht.Läuft(
                        Sprachlauf.Stand("Tonquellenversuch", stand, false, 0)
                    )
                }
                lege(
                    "tonquellenversuch.txt",
                    versuch.fuehreDurch(pcm.readBytes(), bezug.readText().trim())
                )
            }
        }
        if (intent.getBooleanExtra("absicht", false) && sicht is Sicht.Bereit) {
            thread {
                val versuch = Absichtsversuch(this) { stand -> sicht = Sicht.Läuft(stand) }
                lege("absichtsversuch.txt", versuch.fuehreDurch())
            }
        }
        if (intent.getBooleanExtra("diagnose", false) && sicht is Sicht.Bereit) {
            thread {
                lege(
                    "erkennerdiagnose.txt",
                    Erkennerdiagnose.vergleicheAbsichten(this) + "\n\n" +
                        Erkennerdiagnose.erhebe(this)
                )
            }
        }

        setContent {
            NibraTheme {
                // Ohne das klebt die Überschrift an der Uhr und der Knopf an
                // der Gestenleiste. Auf dem S23 Ultra faellt das noch mehr auf
                // als auf dem A15.
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    when (val jetzt = sicht) {
                        is Sicht.Bereit -> BereitSicht(
                            angezeigteSprache = angezeigteSprache,
                            aufSpracheWechseln = {
                                angezeigteSprache =
                                    if (angezeigteSprache.startsWith("de")) "en-US" else "de-DE"
                            },
                            aufSprachlauf = ::starteSprachlauf,
                            aufMikrofonbefund = ::starteMikrofonbefund
                        )
                        is Sicht.Läuft -> LaufSicht(jetzt.stand, angezeigteSprache)
                        is Sicht.Fertig -> BerichtSicht(jetzt.bericht, jetzt.pfad) {
                            sicht = Sicht.Bereit
                        }
                        is Sicht.Fehlt -> MeldungSicht(jetzt.grund)
                    }
                }
            }
        }
    }

    /**
     * Die Sprache für die Messung, voreingestellt de-DE.
     *
     * Über `--es sprache en-US` umstellbar. Nötig geworden, weil das
     * Pixel 9 nur en-US auf dem Gerät hat: dort mit de-DE zu messen
     * ergäbe acht leere Durchgänge und den falschen Schluss, die Strecke
     * trage nicht.
     */
    /**
     * Schreibt jede Standmeldung zusätzlich in eine Datei.
     *
     * Ohne das ist von außen nicht zu unterscheiden, ob ein Lauf arbeitet,
     * hängt oder nie angefangen hat -- alle drei sehen gleich aus, nämlich
     * nach einer fehlenden Ergebnisdatei. Genau daran ist in dieser Nacht
     * zweimal Zeit verloren gegangen, einmal davon an einem Prozess, der
     * längst tot war.
     */
    private fun meldeFortschritt(text: String) {
        runCatching {
            File(getExternalFilesDir(null), "fortschritt.txt").writeText(
                "%s  %s".format(
                    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.GERMANY)
                        .format(java.util.Date()),
                    text
                )
            )
        }
    }

    private fun messSprache(): String = intent.getStringExtra("sprache") ?: "de-DE"

    /** Die Tonaufnahme für die Messung -- muss zur Sprache passen. */
    private fun messSpur(): String = intent.getStringExtra("spur") ?: "vorlauf.pcm"

    private fun starteSprachlauf() = thread {
        // Die im Messplatz gewählte Sprache. Der Messplatz ist eine eigene
        // App mit eigener Ablage -- er kennt Nibras Einstellung nicht und
        // darf sie auch nicht erraten. Am A15 stand das System auf en-CA,
        // Nibra auf de-DE; wer sich auf die Systemsprache verlässt, misst
        // gegen den falschen Bezugstext.
        val sprachCode = angezeigteSprache
        val lauf = Sprachlauf(this, sprachCode) { stand -> sicht = Sicht.Läuft(stand) }
        val bericht = runCatching { Sprachbericht.schreibe(lauf.fuehreDurch()) }
            .getOrElse { "Lauf abgebrochen: ${it.javaClass.simpleName} ${it.message}" }
        lege("sprachlauf.txt", bericht)
    }

    private fun starteMikrofonbefund() = thread {
        lege("audiobefund.txt", Mikrofonbefund.erhebe(this))
    }

    private fun lege(name: String, bericht: String) {
        val datei = File(getExternalFilesDir(null), name)
        runCatching { datei.writeText(bericht) }
        // Zeilenweise ins Protokoll -- logcat schneidet lange Zeilen ab.
        bericht.lineSequence().forEach { Log.i("NibraBefund", it) }
        sicht = Sicht.Fertig(bericht, datei.absolutePath)
    }

    private companion object {
        /**
         * Verhindert, dass ein Versuch nach einem Neuaufbau der Oberfläche
         * ein zweites Mal anläuft. Am Prozess festgemacht, nicht an der
         * Activity -- eine neu gebaute Activity wüsste sonst nichts davon.
         */
        val laeuftSchon = java.util.concurrent.atomic.AtomicBoolean(false)

        /**
         * Kurz und mit klarem Anfang: der Vorlauf lässt sich nur zeigen,
         * wenn man weiß, welches Wort zuerst kommt.
         */
        const val VORLESESATZ = "Guten Morgen, hier spricht Belkis Aslani."

        /**
         * So spät startet die Erkennung nach der Aufnahme. Länger als der
         * Satzanfang dauert -- sonst gäbe es nichts zu retten und der
         * Versuch zeigte nichts.
         */
        const val VERZOEGERUNG_MILLIS = 1_200L

        /**
         * Beginnt mit einem Wort, das sonst nirgends vorkommt. Fehlt es,
         * ist der Anfang abgeschnitten -- ablesbar, nicht auszulegen.
         */
        const val VORLAUFSATZ = "Zitrone guten Morgen dies ist der Vorlauftest von Nibra"
        const val ANKERWORT = "Zitrone"
    }
}

@Composable
private fun BereitSicht(
    angezeigteSprache: String,
    aufSpracheWechseln: () -> Unit,
    aufSprachlauf: () -> Unit,
    aufMikrofonbefund: () -> Unit
) {
    // Der Text ist lang, der Startknopf ist die Hauptsache. Also scrollt
    // nur der Text; die Knöpfe stehen fest am unteren Rand und sind nie
    // ausser Sicht.
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
      Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Text(
            "Messplatz",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Der Sprachlauf dauert rund 80 Sekunden: zweimal derselbe Text, " +
                "einmal mit dem Erkenner allein und einmal mit einer eigenen " +
                "Aufnahme daneben.",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            "Gerät bei beiden Läufen gleich halten. Erst tippen, wenn du " +
                "bereit bist zu lesen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        // Die Messbedingung gehört sichtbar auf den Schirm. Ein Lauf, dessen
        // Sprache man raten muss, ist nicht nachvollziehbar.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Diktatsprache: $angezeigteSprache",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = aufSpracheWechseln) { Text("wechseln") }
        }
        Text("Das ist der Text:", style = MaterialTheme.typography.labelLarge)
        Text(
            Sprachlauf.bezugstextFuer(angezeigteSprache),
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 26.sp
        )
        Spacer(Modifier.height(8.dp))
      }
      Spacer(Modifier.height(16.dp))
      Button(onClick = aufSprachlauf, modifier = Modifier.fillMaxWidth()) {
          Text("Sprachlauf starten")
      }
      Spacer(Modifier.height(8.dp))
      OutlinedButton(onClick = aufMikrofonbefund, modifier = Modifier.fillMaxWidth()) {
          Text("Nur Mikrofonbefund (ohne Sprechen)")
      }
    }
}

/**
 * Der Bildschirm während des Laufs.
 *
 * Der ganze Grund färbt sich, sobald gesprochen werden soll. Ein Fortschritts-
 * balken zeigt, wie viel Text noch übrig ist — damit lässt sich das Tempo
 * einteilen, statt am Ende zu hetzen oder zu warten.
 */
@Composable
private fun LaufSicht(stand: Sprachlauf.Stand, angezeigteSprache: String) {
    val grund by animateColorAsState(
        if (stand.sprechen) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        label = "Grundfarbe"
    )
    val schrift =
        if (stand.sprechen) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(grund)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stand.lauf,
            style = MaterialTheme.typography.labelLarge,
            color = schrift.copy(alpha = 0.7f)
        )
        Text(
            stand.anweisung,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = schrift
        )
        Text(
            "noch ${stand.restSekunden} s",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = schrift
        )
        LinearProgressIndicator(
            progress = {
                val gesamt = if (stand.sprechen) Sprachlauf.SPRECHDAUER_MS / 1000f else 0f
                if (gesamt <= 0f) 0f else 1f - stand.restSekunden / gesamt
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            Sprachlauf.bezugstextFuer(angezeigteSprache),
            // Vorlesetext: größer als Fließtext und mit viel Zeilenabstand.
            // Wer beim Lesen die Zeile verliert, macht eine Pause -- und
            // genau die verfälscht die Messung.
            fontSize = 19.sp,
            lineHeight = 30.sp,
            color = schrift,
            modifier = Modifier.verticalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun BerichtSicht(bericht: String, pfad: String, aufZurück: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Fertig", style = MaterialTheme.typography.headlineMedium)
        Text(pfad, style = MaterialTheme.typography.bodySmall)
        Button(onClick = aufZurück) { Text("Zurück") }
        Text(
            bericht,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.verticalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun MeldungSicht(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}
