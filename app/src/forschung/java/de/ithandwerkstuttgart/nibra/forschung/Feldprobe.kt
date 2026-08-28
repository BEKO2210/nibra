package de.ithandwerkstuttgart.nibra.forschung

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Ein Eingabefeld, damit die Blase erscheint.
 *
 * Die Blase zeigt sich nur über einem beschreibbaren, nicht geschützten
 * Feld. Für den Belastungslauf braucht es also eines -- und zwar ein
 * **bekanntes**: eine beliebige fremde App wäre nicht reproduzierbar, ihre
 * Felder ändern sich mit jeder Fassung, und ob dort gerade ein Passwortfeld
 * den Fokus hat, weiß man nicht.
 *
 * Bewusst nackt. Sie ist ein Prüfstand, kein Bildschirm.
 */
class Feldprobe : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val spalte = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(32, 96, 32, 32)
        }
        spalte.addView(TextView(this).apply {
            text = "Feldprobe: die Blase soll hier erscheinen."
            textSize = 16f
        })
        spalte.addView(EditText(this).apply {
            hint = "Hier hinein schreibt Nibra"
            textSize = 18f
            requestFocus()
        })
        setContentView(spalte)
    }
}
