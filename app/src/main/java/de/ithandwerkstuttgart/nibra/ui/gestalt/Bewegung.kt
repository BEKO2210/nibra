package de.ithandwerkstuttgart.nibra.ui.gestalt

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Die Bewegungssprache von Nibra.
 *
 * **Warum eigene Federn und nicht Materials `MotionScheme`?** In der
 * ausgelieferten `material3 1.4.0` ist die gesamte Bewegungs-Schnittstelle
 * `internal` -- weder `MaterialTheme.motionScheme` noch
 * `MaterialExpressiveTheme` sind von außen erreichbar. Eine Alpha-Fassung
 * kommt für eine Anwendung, die in den Laden soll, nicht in Frage.
 *
 * Diese Schicht ist deshalb bewusst so geschnitten wie `MotionScheme`: vier
 * Stufen, jede als `FiniteAnimationSpec`. Sobald Materials Schnittstelle offen
 * ist, tauscht man den Rumpf von [spezifikation] gegen die Material-Werte --
 * kein Bildschirm muss angefasst werden.
 *
 * ## Die vier Stufen
 *
 * Sie unterscheiden sich nicht in der Dauer, sondern in der **Physik**:
 * Dämpfung sagt, wie stark etwas nachschwingt, Härte, wie schnell es zieht.
 *
 * | Stufe | Dämpfung | Härte | Wofür |
 * |---|---|---|---|
 * | [Stufe.WIRKUNG] | 1,0 | 1400 | Deckung, Farbe, Größe eines Symbols. Kein Nachschwingen -- man soll sie nicht bemerken |
 * | [Stufe.RAUM] | 0,9 | 500 | Etwas bewegt sich von A nach B. Ein Hauch Nachschwingen, gerade an der Wahrnehmungsschwelle |
 * | [Stufe.AUFTRITT] | 0,75 | 320 | Das Große: die Aufnahmefläche, die Blase. Sichtbares, aber gehaltenes Überschwingen |
 * | [Stufe.RUHE] | 1,0 | 180 | Große Flächen, die sich Zeit nehmen dürfen. Nie Überschwingen |
 *
 * Überschwingen gibt es **nur** in [Stufe.AUFTRITT] und dort mit 0,75 --
 * spürbar, aber nicht verspielt. Alles andere dämpft auf 1,0 und kommt
 * ohne Nachwippen an. Ein Wippen an jeder Kachel wäre billig.
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
 * Notlösung, sondern die richtige Antwort: wer Bewegung abschaltet, will kein
 * langsameres Wackeln, sondern gar keins.
 */
val LokaleBewegungAus = staticCompositionLocalOf { false }

/**
 * Die eine Stelle, an der aus einer Stufe eine Federkennlinie wird.
 *
 * Rein rechnend und ohne Compose -- damit sie geprüft werden kann.
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
 * Bewegung braucht und keine der vier Stufen passt, ändert **hier** etwas --
 * dann bewegt sich die ganze App weiter nach einem System.
 */
object Bewegung {

    /** Kleine Reaktion: Deckung, Farbe, Symbolgröße. */
    @Composable
    fun <T> wirkung(): FiniteAnimationSpec<T> = fuer(Stufe.WIRKUNG)

    /** Ortsveränderung: etwas kommt herein, geht hinaus, rückt zur Seite. */
    @Composable
    fun <T> raum(): FiniteAnimationSpec<T> = fuer(Stufe.RAUM)

    /** Das Große: Aufnahmefläche, Blase, Zustandswechsel des Kerns. */
    @Composable
    fun <T> auftritt(): FiniteAnimationSpec<T> = fuer(Stufe.AUFTRITT)

    /** Große Flächen, die sich Zeit nehmen dürfen. */
    @Composable
    fun <T> ruhe(): FiniteAnimationSpec<T> = fuer(Stufe.RUHE)

    @Composable
    private fun <T> fuer(stufe: Stufe): FiniteAnimationSpec<T> =
        spezifikation(stufe, LokaleBewegungAus.current)

    /**
     * Der Wechsel zwischen zwei **verschiedenen** Texten an derselben Stelle.
     *
     * Die einzige Stelle im Haus, an der Dauern statt einer Feder stehen --
     * und zwar aus einem Grund: eine Feder kann nicht warten. Blenden zwei
     * Texte gleichzeitig ein und aus, liegen sie übereinander und sind
     * beide unlesbar. Genau das war auf dem Gerät zu sehen: „Wird in Text
     * gewandelt" stand doppelt, der alte Text grau über dem neuen.
     *
     * Also nacheinander: erst geht der alte ganz weg, dann kommt der neue.
     * Die Verzögerung des Einblendens ist genau die Dauer des Ausblendens.
     *
     * Für eine Überblendung zwischen zwei Zuständen **desselben** Textes
     * ist das der falsche Weg -- da gehört eine Feder hin.
     */
    fun textwechsel(bewegungAus: Boolean): ContentTransform {
        // Kein @Composable: die Angabe wird im transitionSpec von
        // AnimatedContent gebraucht, und der ist kein Composable-Kontext.
        // Deshalb wird „Bewegung aus" von außen hereingegeben.
        val groesse = SizeTransform(clip = false) { _, _ ->
            spezifikation(Stufe.RAUM, bewegungAus)
        }
        if (bewegungAus) {
            return ContentTransform(
                targetContentEnter = fadeIn(snap()),
                initialContentExit = fadeOut(snap()),
                sizeTransform = groesse
            )
        }
        return ContentTransform(
            targetContentEnter = fadeIn(tween(EINBLENDEN_MS, delayMillis = AUSBLENDEN_MS)),
            initialContentExit = fadeOut(tween(AUSBLENDEN_MS)),
            sizeTransform = groesse
        )
    }

    /** Kurz genug, dass niemand wartet, lang genug, dass es kein Sprung ist. */
    private const val AUSBLENDEN_MS = 110

    /** Etwas länger als das Ausblenden: der neue Text soll ankommen, nicht aufblitzen. */
    private const val EINBLENDEN_MS = 190
}

/**
 * Die Grenzen, unterhalb derer eine Feder als angekommen gilt.
 *
 * Ohne sie zittert eine Feder auf einer Größe in Bildpunkten ewig um ihr
 * Ziel: die Voreinstellung ist auf Werte um 1 ausgelegt, nicht auf Hunderte.
 */
object Federgrenze {
    /** Für Deckung und andere Anteile zwischen 0 und 1. */
    const val ANTEIL = 1f / 1000f

    /** Für Werte in Bildpunkten. */
    const val PUNKT = 0.5f

    /** Materials Vorgabe, hier nur benannt, damit sie nicht geraten wird. */
    const val VORGABE = Spring.DefaultDisplacementThreshold
}
