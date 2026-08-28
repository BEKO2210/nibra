package de.ithandwerkstuttgart.nibra.forschung

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.concurrent.thread

/**
 * Der Messplatz der Forschungsauspraegung.
 *
 * Bewusst eine schlichte Ansicht mit einem Textfeld -- sie ist ein Werkzeug,
 * kein Produkt. Alles, was hier haesslich ist, ist es absichtlich: die
 * Aufmerksamkeit gehoert den Zahlen.
 *
 * Der Bericht geht zugleich ins Protokoll und in eine Datei, damit er sich
 * ohne Abtippen vom Rechner holen laesst.
 */
class ForschungActivity : ComponentActivity() {

    private lateinit var ausgabe: TextView

    override fun onCreate(zustand: Bundle?) {
        super.onCreate(zustand)
        ausgabe = TextView(this).apply {
            textSize = 10f
            setPadding(24, 24, 24, 24)
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        setContentView(ScrollView(this).apply { addView(ausgabe) })

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            zeige("Mikrofonrecht fehlt. Ohne das misst hier nichts.")
            return
        }
        zeige("Messung laeuft. Bitte gleichmaessig sprechen ...")
        thread {
            val bericht = buildString {
                appendLine(Mikrofonbefund.erhebe(this@ForschungActivity))
                appendLine()
                appendLine("=".repeat(60))
                appendLine()
                appendLine(Nebenlaufversuch(this@ForschungActivity).fuehreDurch())
            }
            val datei = File(getExternalFilesDir(null), "audiobefund.txt")
            runCatching { datei.writeText(bericht) }
            // In Bloecken protokollieren -- logcat schneidet lange Zeilen ab.
            bericht.lineSequence().forEach { Log.i(MARKE, it) }
            runOnUiThread { zeige(bericht + "\n\nAbgelegt: ${datei.absolutePath}") }
        }
    }

    private fun zeige(text: String) {
        ausgabe.text = text
    }

    private companion object {
        const val MARKE = "NibraBefund"
    }
}
