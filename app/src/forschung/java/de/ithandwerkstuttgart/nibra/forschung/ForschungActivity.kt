package de.ithandwerkstuttgart.nibra.forschung

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

        setContent {
            NibraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val jetzt = sicht) {
                        is Sicht.Bereit -> BereitSicht(
                            aufSprachlauf = ::starteSprachlauf,
                            aufMikrofonbefund = ::starteMikrofonbefund
                        )
                        is Sicht.Läuft -> LaufSicht(jetzt.stand)
                        is Sicht.Fertig -> BerichtSicht(jetzt.bericht, jetzt.pfad) {
                            sicht = Sicht.Bereit
                        }
                        is Sicht.Fehlt -> MeldungSicht(jetzt.grund)
                    }
                }
            }
        }
    }

    private fun starteSprachlauf() = thread {
        val lauf = Sprachlauf(this) { stand -> sicht = Sicht.Läuft(stand) }
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
}

@Composable
private fun BereitSicht(aufSprachlauf: () -> Unit, aufMikrofonbefund: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
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
        Text("Das ist der Text:", style = MaterialTheme.typography.labelLarge)
        Text(
            Sprachlauf.BEZUGSTEXT,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 26.sp
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = aufSprachlauf, modifier = Modifier.fillMaxWidth()) {
            Text("Sprachlauf starten")
        }
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
private fun LaufSicht(stand: Sprachlauf.Stand) {
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
            Sprachlauf.BEZUGSTEXT,
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
