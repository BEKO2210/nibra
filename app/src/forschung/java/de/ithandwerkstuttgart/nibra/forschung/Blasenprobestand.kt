package de.ithandwerkstuttgart.nibra.forschung

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import de.ithandwerkstuttgart.nibra.dienst.Blasenansicht
import java.io.File
import kotlin.math.sin

/**
 * Belastungsstand für die Blase.
 *
 * Der Absturz trat im Zeichenpfad auf, unter Dauerbewegung mit ständig
 * wechselndem Pegel. Also wird genau das erzeugt -- und zwar härter als im
 * Betrieb: **mehrere** Blasen gleichzeitig, jede mit eigenem Shader, alle
 * dauerhaft in Bewegung.
 *
 * Der Stand zählt die tatsächlich gezeichneten Bilder. „Ist nicht
 * abgestürzt" ohne Zahl dahinter wäre keine Aussage: eine Blase, die gar
 * nicht zeichnet, stürzt auch nicht ab.
 *
 * Bildschirm an/aus, Vordergrund/Hintergrund, Drehung und Dienst an/aus
 * kommen von außen über `adb` dazu.
 */
class Blasenprobestand : ComponentActivity() {

    private lateinit var anzeige: TextView
    private val blasen = mutableListOf<Blasenansicht>()
    private var begonnen = 0L

    /** Zählt die Bilder, die wirklich durch den Zeichenpfad gelaufen sind. */
    private var bilder = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        begonnen = SystemClock.elapsedRealtime()

        val spalte = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101010"))
        }
        anzeige = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(24, 48, 24, 24)
        }
        spalte.addView(anzeige)

        val reihe = FrameLayout(this)
        val kante = (resources.displayMetrics.density * 64).toInt()
        repeat(BLASEN) { stelle ->
            // Jede Blase baut ihren eigenen Shader. Vier davon nebeneinander
            // sind der Fall, den es im Betrieb nicht gibt -- genau deshalb
            // taugt er als Belastung.
            val blase = Blasenansicht(this)
            blase.setzeLaeuft(true)
            reihe.addView(
                blase,
                FrameLayout.LayoutParams(kante, kante, Gravity.TOP or Gravity.START).apply {
                    leftMargin = kante * stelle + 16
                    topMargin = 16
                }
            )
            blasen += blase
        }
        spalte.addView(reihe, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, kante + 48))
        setContentView(spalte)

        // Gezählt wird am Baum, nicht in der Ansicht: Blasenansicht ist
        // final, und das ist richtig so -- eine Klasse, deren Zeichenpfad
        // die Eigentümerschaft am Shader trägt, soll niemand überschreiben.
        // Ein Durchlauf des Baums entspricht einem Zeichenvorgang je Blase.
        spalte.viewTreeObserver.addOnDrawListener { bilder++ }

        treibe()
    }

    /**
     * Hält den Pegel in Bewegung und schreibt den Stand mit.
     *
     * Ein fester Pegel wäre die halbe Prüfung: die Uniforms würden zwar
     * gesetzt, aber immer mit demselben Wert.
     */
    private fun treibe() {
        val sinus = SystemClock.elapsedRealtime() / 400.0
        val pegel = (sin(sinus).toFloat() + 1f) / 2f
        blasen.forEach { it.setzePegel(pegel) }

        val laufMillis = SystemClock.elapsedRealtime() - begonnen
        anzeige.text = buildString {
            appendLine("BLASEN-BELASTUNGSSTAND")
            appendLine("Blasen gleichzeitig: ${blasen.size}")
            appendLine("Laufzeit: ${laufMillis / 1000} s")
            appendLine("Durchläufe des Baums: $bilder")
            appendLine("Zeichenvorgänge gesamt: ${bilder * blasen.size}")
            appendLine("Zeichenweg: " + when (blasen.firstOrNull()?.zeichnetMitShader) {
                true -> "SHADER (das ist der Weg, der abgestürzt ist)"
                false -> "RÜCKFALL ohne Grafikeinheit -- prüft NICHT den Shader"
                null -> "noch nicht gezeichnet"
            })
            appendLine("Bilder je Sekunde: " +
                if (laufMillis > 0) "%.1f".format(bilder * 1000.0 / laufMillis) else "-")
            appendLine()
            appendLine("Läuft, bis die Ansicht geschlossen wird.")
        }
        if (laufMillis % SCHREIBABSTAND_MS < TAKT_MS) schreibeStand(laufMillis)

        anzeige.postDelayed(::treibe, TAKT_MS)
    }

    /** Legt den Stand ab, damit er sich ohne Bildschirmfoto auslesen lässt. */
    private fun schreibeStand(laufMillis: Long) {
        val stand = "laufzeit_s=${laufMillis / 1000} " +
            "weg=${when (blasen.firstOrNull()?.zeichnetMitShader) {
                true -> "shader"; false -> "rueckfall"; null -> "unbekannt" }} " +
            "baumdurchlaeufe=$bilder " +
            "zeichenvorgaenge=${bilder * blasen.size} blasen=${blasen.size} " +
            "bilder_je_sekunde=%.1f".format(
                if (laufMillis > 0) bilder * 1000.0 / laufMillis else 0.0)
        runCatching {
            File(getExternalFilesDir(null), "blasenprobe.txt").writeText(stand)
        }
        Log.i("NibraProbe", stand)
    }

    override fun onDestroy() {
        blasen.forEach { it.setzeLaeuft(false) }
        anzeige.removeCallbacks(null)
        super.onDestroy()
    }

    private companion object {
        const val BLASEN = 4
        const val TAKT_MS = 16L
        const val SCHREIBABSTAND_MS = 2_000L
    }
}
