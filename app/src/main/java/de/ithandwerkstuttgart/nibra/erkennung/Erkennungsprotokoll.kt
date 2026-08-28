package de.ithandwerkstuttgart.nibra.erkennung

import android.os.SystemClock
import android.util.Log

/**
 * Schreibt mit, was der Erkenner meldet -- und **nur das**.
 *
 * Als die Oberfläche dauerhaft auf „wandelt" stand, ließ sich nicht sagen,
 * welcher Rückruf der letzte war. Genau diese Frage beantwortet das hier:
 * eine Folge von Ereignisnamen mit monotonen Zeitstempeln, über `adb logcat`
 * zu lesen.
 *
 * **Niemals gesprochener Inhalt.** Nicht der erkannte Text, nicht die
 * Teiltexte, nicht die Alternativen. Ein Diktat kann alles enthalten --
 * Krankheiten, Passwörter, fremde Namen -- und ein Protokoll überlebt die
 * App. Was hier steht, sind Namen von Rückrufen, Zeiten und Anzahlen.
 *
 * Die Uhr ist [SystemClock.elapsedRealtime], nicht die Wanduhr: sie läuft
 * gleichmäßig und springt nicht, wenn das Gerät seine Zeit stellt.
 */
object Erkennungsprotokoll {

    private const val MARKE = "NibraDiktat"

    @Volatile
    private var nullpunkt = 0L

    /** Beginnt eine neue Aufnahme. Ab hier zählen die Zeiten. */
    fun beginne(anlass: String) {
        nullpunkt = SystemClock.elapsedRealtime()
        Log.i(MARKE, "===== $anlass =====")
        schreibe("Beginn")
    }

    /** Ein Rückruf des Erkenners. */
    fun rueckruf(name: String, zusatz: String = "") = schreibe("<- $name", zusatz)

    /** Ein Aufruf **an** den Erkenner. */
    fun aufruf(name: String, zusatz: String = "") = schreibe("-> $name", zusatz)

    /** Ein Wechsel des fachlichen Zustands. */
    fun zustand(name: String) = schreibe("[$name]")

    private fun schreibe(was: String, zusatz: String = "") {
        val seit = if (nullpunkt == 0L) 0 else SystemClock.elapsedRealtime() - nullpunkt
        Log.i(MARKE, "%7d ms  %s%s".format(seit, was, if (zusatz.isBlank()) "" else "  $zusatz"))
    }
}
