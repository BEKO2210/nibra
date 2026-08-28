package de.ithandwerkstuttgart.nibra.ui.gestalt

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Die Bewegungssprache von Nibra.
 *
 * **Warum eigene Federn und nicht Materials `MotionScheme`?** In der
 * ausgelieferten `material3 1.4.0` ist die gesamte Bewegungs-Schnittstelle
 * `internal` -- weder `MaterialTheme.motionScheme` noch
 * `MaterialExpressiveTheme` sind von aussen erreichbar. Eine Alpha-Fassung
 * kommt fuer eine Anwendung, die in den Laden soll, nicht in Frage.
 *
 * Diese Schicht ist deshalb bewusst so geschnitten wie `MotionScheme`: vier
 * Stufen, jede als `FiniteAnimationSpec`. Sobald Materials Schnittstelle offen
 * ist, tauscht man den Rumpf von [spezifikation] gegen die Material-Werte --
 * kein Bildschirm muss angefasst werden.
 *
 * ## Die vier Stufen
 *
 * Sie unterscheiden sich nicht in der Dauer, sondern in der **Physik**:
 * Daempfung sagt, wie stark etwas nachschwingt, Haerte, wie schnell es zieht.
 *
 * | Stufe | Daempfung | Haerte | Wofuer |
 * |---|---|---|---|
 * | [Stufe.WIRKUNG] | 1,0 | 1400 | Deckung, Farbe, Groesse eines Symbols. Kein Nachschwingen -- man soll sie nicht bemerken |
 * | [Stufe.RAUM] | 0,9 | 500 | Etwas bewegt sich von A nach B. Ein Hauch Nachschwingen, gerade an der Wahrnehmungsschwelle |
 * | [Stufe.AUFTRITT] | 0,75 | 320 | Das Grosse: die Aufnahmeflaeche, die Blase. Sichtbares, aber gehaltenes Ueberschwingen |
 * | [Stufe.RUHE] | 1,0 | 180 | Grosse Flaechen, die sich Zeit nehmen duerfen. Nie Ueberschwingen |
 *
 * Ueberschwingen gibt es **nur** in [Stufe.AUFTRITT] und dort mit 0,75 --
 * spuerbar, aber nicht verspielt. Alles andere daempft auf 1,0 und kommt
 * ohne Nachwippen an. Ein Wippen an jeder Kachel waere billig.
 */
enum class Stufe(val daempfung: Float, val haerte: Float) {
    WIRKUNG(daempfung = 1f, haerte = 1400f),
    RAUM(daempfung = 0.9f, haerte = 500f),
    AUFTRITT(daempfung = 0.75f, haerte = 320f),
    RUHE(daempfung = 1f, haerte = 180f)
}

/**
 * Wahr, wenn der Nutzer Animationen abgeschaltet hat
 * (`ANIMATOR_DURATION_SCALE = 0`) oder das System Bewegung reduziert.
 *
 * Dann springt jede Animation sofort ans Ziel, statt zu laufen. Das ist keine
 * Notloesung, sondern die richtige Antwort: wer Bewegung abschaltet, will kein
 * langsameres Wackeln, sondern gar keins.
 */
val LokaleBewegungAus = staticCompositionLocalOf { false }

/**
 * Die eine Stelle, an der aus einer Stufe eine Federkennlinie wird.
 *
 * Rein rechnend und ohne Compose -- damit sie geprueft werden kann.
 */
fun <T> spezifikation(stufe: Stufe, bewegungAus: Boolean): FiniteAnimationSpec<T> =
    if (bewegungAus) {
        snap()
    } else {
        spring(dampingRatio = stufe.daempfung, stiffness = stufe.haerte)
    }

/**
 * Die Bewegungssprache, wie Bildschirme sie ansprechen.
 *
 * Kein Bildschirm schreibt eigene `tween`- oder `spring`-Werte. Wer eine neue
 * Bewegung braucht und keine der vier Stufen passt, aendert **hier** etwas --
 * dann bewegt sich die ganze App weiter nach einem System.
 */
object Bewegung {

    /** Kleine Reaktion: Deckung, Farbe, Symbolgroesse. */
    @Composable
    fun <T> wirkung(): FiniteAnimationSpec<T> = fuer(Stufe.WIRKUNG)

    /** Ortsveraenderung: etwas kommt herein, geht hinaus, rueckt zur Seite. */
    @Composable
    fun <T> raum(): FiniteAnimationSpec<T> = fuer(Stufe.RAUM)

    /** Das Grosse: Aufnahmeflaeche, Blase, Zustandswechsel des Kerns. */
    @Composable
    fun <T> auftritt(): FiniteAnimationSpec<T> = fuer(Stufe.AUFTRITT)

    /** Grosse Flaechen, die sich Zeit nehmen duerfen. */
    @Composable
    fun <T> ruhe(): FiniteAnimationSpec<T> = fuer(Stufe.RUHE)

    @Composable
    private fun <T> fuer(stufe: Stufe): FiniteAnimationSpec<T> =
        spezifikation(stufe, LokaleBewegungAus.current)
}

/**
 * Die Grenzen, unterhalb derer eine Feder als angekommen gilt.
 *
 * Ohne sie zittert eine Feder auf einer Groesse in Bildpunkten ewig um ihr
 * Ziel: die Voreinstellung ist auf Werte um 1 ausgelegt, nicht auf Hunderte.
 */
object Federgrenze {
    /** Fuer Deckung und andere Anteile zwischen 0 und 1. */
    const val ANTEIL = 1f / 1000f

    /** Fuer Werte in Bildpunkten. */
    const val PUNKT = 0.5f

    /** Materials Vorgabe, hier nur benannt, damit sie nicht geraten wird. */
    const val VORGABE = Spring.DefaultDisplacementThreshold
}
