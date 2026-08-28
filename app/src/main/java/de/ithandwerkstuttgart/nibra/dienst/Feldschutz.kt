package de.ithandwerkstuttgart.nibra.dienst

import android.text.InputType

/**
 * Entscheidet, ob in ein Feld geschrieben werden darf.
 *
 * Reines Rechnen, ohne Android-Knoten -- damit die Regel geprueft werden
 * kann, statt nur behauptet zu werden (AUFTRAG.md, Antwort 9:
 * "Passwortfelder bleiben ausgenommen").
 *
 * `AccessibilityNodeInfo.isPassword` allein reicht nicht: Felder in
 * WebViews und manche selbst gebauten Eingaben setzen die Kennzeichnung
 * nicht, tragen die Absicht aber im `inputType`. Darum werden beide
 * Angaben geprueft, und im Zweifel wird nicht geschrieben.
 */
object Feldschutz {

    /**
     * Die Eingabearten, hinter denen ein Geheimnis steht. `TYPE_MASK_VARIATION`
     * blendet Klasse und Kennzeichen aus, sodass nur die Spielart bleibt.
     */
    private val GESCHUETZTE_TEXTARTEN = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    )

    private val GESCHUETZTE_ZAHLENARTEN = setOf(
        InputType.TYPE_NUMBER_VARIATION_PASSWORD
    )

    /**
     * Wahr, wenn Nibra dieses Feld unberuehrt lassen muss.
     *
     * @param alsPasswortGemeldet was der Knoten selbst angibt
     *        (`AccessibilityNodeInfo.isPassword`)
     * @param eingabeart der `inputType` des Knotens; 0, wenn unbekannt
     */
    fun istGeschuetzt(alsPasswortGemeldet: Boolean, eingabeart: Int): Boolean {
        if (alsPasswortGemeldet) return true
        val spielart = eingabeart and InputType.TYPE_MASK_VARIATION
        return when (eingabeart and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> spielart in GESCHUETZTE_TEXTARTEN
            InputType.TYPE_CLASS_NUMBER -> spielart in GESCHUETZTE_ZAHLENARTEN
            else -> false
        }
    }

    /**
     * Der Text, der wirklich im Feld steht.
     *
     * `AccessibilityNodeInfo.text` liefert bei einem leeren Feld haeufig den
     * grauen Hinweistext zurueck -- "Nachricht eingeben", "Google fragen".
     * Wer das fuer Inhalt haelt, haengt das Diktat dahinter an, und im Feld
     * steht anschliessend der Platzhalter mit im Satz.
     *
     * @param zeigtHinweis `AccessibilityNodeInfo.isShowingHintText` -- seit
     *        Android 8 verfuegbar und genau dafuer da
     * @param text was der Knoten als Text meldet
     */
    fun inhalt(zeigtHinweis: Boolean, text: CharSequence?): String =
        if (zeigtHinweis) "" else text?.toString().orEmpty()
}
