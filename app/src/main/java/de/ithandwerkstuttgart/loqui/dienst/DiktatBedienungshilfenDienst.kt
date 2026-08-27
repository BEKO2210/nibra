package de.ithandwerkstuttgart.loqui.dienst

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import de.ithandwerkstuttgart.loqui.R

/**
 * Bedienungshilfen-Dienst von Loqui: uebernimmt aus der MIT-Vorlage
 * aidictation (Herkunft und Lizenz siehe FREMDSOFTWARE.md) ausschliesslich
 * den Ansatz -- eine schwebende Aufnahmeflaeche ueber
 * fremden Apps, die erkannten Text an der Cursorposition einfuegt. Er liest
 * nie mit und laesst Passwortfelder unberuehrt (AUFTRAG.md, Antwort 9).
 *
 * Die eigentliche Spracherkennung (SpeechRecognizer.createOnDeviceSpeechRecognizer
 * ab API 33, sonst SpeechRecognizer mit EXTRA_PREFER_OFFLINE, siehe Nachtrag
 * "Spracherkennung -- Entscheidung" in AUFTRAG.md) haengt sich hier ein,
 * sobald Station 4 sie verdrahtet -- dieser Dienst stellt bereits die
 * Fensterverwaltung, Fokuserkennung und Einfuege-Mechanik bereit.
 */
class DiktatBedienungshilfenDienst : AccessibilityService() {

    private lateinit var fensterVerwaltung: WindowManager
    private var blaseAnsicht: ImageButton? = null
    private var blaseParameter: WindowManager.LayoutParams? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        fensterVerwaltung = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> aktualisiereBlase()

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> verbergeBlase()
        }
    }

    override fun onInterrupt() {
        verbergeBlase()
    }

    override fun onDestroy() {
        verbergeBlase()
        super.onDestroy()
    }

    /** Zeigt die Aufnahmeblase nur ueber einem fokussierten, editierbaren
     * Feld -- und nie ueber einem Passwortfeld. */
    private fun aktualisiereBlase() {
        val fokussiertesFeld = fokussiertesEingabefeld()
        if (fokussiertesFeld == null || fokussiertesFeld.isPassword) {
            verbergeBlase()
            return
        }
        zeigeBlase()
    }

    /** Nur ein fokussiertes, editierbares Feld zaehlt -- ein fokussierter
     * Knopf oder Text ist keine Diktatstelle. */
    private fun fokussiertesEingabefeld(): AccessibilityNodeInfo? {
        val wurzel = rootInActiveWindow ?: return null
        val fokus = wurzel.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        return if (fokus.isEditable) fokus else null
    }

    private fun zeigeBlase() {
        if (blaseAnsicht != null) return
        val ansicht = ImageButton(this).apply {
            setImageResource(R.drawable.lq_ic_mikrofon)
            contentDescription = getString(R.string.sw_aufnahme_starten)
            setOnClickListener { aufBlaseGetippt() }
        }
        val parameter = WindowManager.LayoutParams(
            inDp(TIPPZIEL_DP),
            inDp(TIPPZIEL_DP),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = inDp(RAND_DP)
            y = inDp(RAND_DP)
        }
        fensterVerwaltung.addView(ansicht, parameter)
        blaseAnsicht = ansicht
        blaseParameter = parameter
    }

    private fun verbergeBlase() {
        val ansicht = blaseAnsicht ?: return
        runCatching { fensterVerwaltung.removeView(ansicht) }
        blaseAnsicht = null
        blaseParameter = null
    }

    /** Tippen auf die Blase startet/stoppt die Aufnahme -- die eigentliche
     * Erkennung wird von Station 4 an dieser Stelle eingehaengt. */
    private fun aufBlaseGetippt() {
        // TODO(Station 4): SpeechRecognizer.createOnDeviceSpeechRecognizer
        // starten, Ergebnis ueber [fuegeTextEin] am Cursor einfuegen.
    }

    /** Fuegt Text an der Cursorposition des fokussierten Feldes ein, ohne
     * ueber die Zwischenablage zu gehen. Passwortfelder werden hier nicht
     * mehr erreicht, weil die Blase dafuer nie angezeigt wird. */
    fun fuegeTextEin(text: String): Boolean {
        val feld = fokussiertesEingabefeld() ?: return false
        if (feld.isPassword) return false
        val argumente = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return feld.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, argumente)
    }

    private fun inDp(wert: Int): Int =
        (wert * resources.displayMetrics.density).toInt()

    private companion object {
        /** Mindestmass eines antippbaren Feldes, wie in der Oberflaeche. */
        const val TIPPZIEL_DP = 48

        /** Abstand der Blase zum Bildschirmrand, aus der Abstandsskala. */
        const val RAND_DP = 16
    }
}
