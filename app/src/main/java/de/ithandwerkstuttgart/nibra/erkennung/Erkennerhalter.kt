package de.ithandwerkstuttgart.nibra.erkennung

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Der **eine** Spracherkenner dieses Prozesses.
 *
 * Vorher legten vier Stellen unabhängig voneinander einen an: das Diktat,
 * die Sprachliste, das Nachladen eines Pakets und der Bedienungshilfen-
 * Dienst. Alle im selben Prozess, denn der Dienst hat keinen eigenen.
 *
 * Ein `SpeechRecognizer` ist aber kein gewöhnliches Objekt -- er hält eine
 * Bindung an einen Systemdienst. Mehrere gleichzeitig blockieren sich: der
 * zweite bekommt keine Antwort, weder ein Ergebnis noch einen Fehler. Genau
 * das war auf dem Gerät zu sehen. Die Sprachliste blieb bei „Sprachen
 * werden abgefragt" stehen, während der Dienst seinen Erkenner hielt.
 *
 * Deshalb gibt es hier genau einen, und wer ihn braucht, leiht ihn aus.
 * Ist er gerade verliehen, bekommt der Zweite eine ehrliche Absage statt
 * eines zweiten Erkenners, der schweigt.
 *
 * **Alles hier gehört auf den Hauptfaden.** `SpeechRecognizer` verlangt
 * das, und die Reihenfolge der Ausleihen ist damit ohne Sperren geklärt.
 */
@Singleton
class Erkennerhalter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var erkenner: SpeechRecognizer? = null

    /** Wer ihn gerade hat -- für die Fehlersuche im Protokoll. */
    private var entliehenAn: String? = null

    /**
     * Leiht den Erkenner aus.
     *
     * @return `null`, wenn er schon verliehen ist oder sich nicht anlegen
     *         lässt. Ein `null` ist eine Auskunft: der Aufrufer muss dann
     *         einen ehrlichen Zustand melden, statt zu warten.
     */
    fun leihe(zweck: String, vorrang: Boolean = false): SpeechRecognizer? {
        pruefeFaden()
        entliehenAn?.let { anderer ->
            if (!vorrang) {
                Erkennungsprotokoll.aufruf("leihe abgelehnt", "$zweck, belegt von $anderer")
                return null
            }
            // Das Diktat wartet nicht. Am Gerät gemessen: die Sprachabfrage
            // hält den Erkenner auf dem S23 Ultra zwölf Sekunden lang, weil
            // sie keine Antwort bekommt. Wer in dieser Zeit auf die Fläche
            // tippt, bekam „Erkennung nicht verfügbar" -- für eine Diktier-
            // App der schlechtestmögliche Moment zu versagen.
            //
            // Also weicht die Nebensache. Der Erkenner wird dabei verworfen:
            // was der Vorgänger mit ihm angefangen hat, ist unklar.
            // Nicht zerstören, nur abbrechen und weiterreichen. Am Gerät
            // gemessen: wer den alten wegwirft und sofort einen neuen
            // startet, bekommt vom Systemdienst SERVER_DISCONNECTED (11)
            // und RECOGNIZER_BUSY (8). Der Dienst braucht seine Zeit, die
            // wir ihm hier nicht geben können -- also behalten wir den
            // vorhandenen und setzen ihn zurück.
            // Zerstören, nicht nur abbrechen. Am Gerät gemessen: ein
            // abgebrochener, aber nicht zerstörter Erkenner hält die Bindung
            // an den Systemdienst, und der nächste bekommt RECOGNIZER_BUSY
            // (8) -- auch nach einem Neustart der App, denn der Systemdienst
            // lebt ausserhalb.
            Erkennungsprotokoll.aufruf("Vorrang", "$zweck verdrängt $anderer")
            entliehenAn = null
            val alter = erkenner
            erkenner = null
            runCatching { alter?.setRecognitionListener(null) }
            runCatching { alter?.cancel() }
            runCatching { alter?.destroy() }
        }
        val vorhanden = erkenner ?: baue() ?: return null
        erkenner = vorhanden
        entliehenAn = zweck
        Erkennungsprotokoll.aufruf("Erkenner verliehen", zweck)
        return vorhanden
    }

    /**
     * Gibt den Erkenner zurück.
     *
     * @param wegwerfen wahr, wenn er unbrauchbar geworden ist. Dann wird er
     *        zerstört und beim nächsten Mal frisch angelegt.
     */
    fun gibZurueck(zweck: String, wegwerfen: Boolean = false) {
        pruefeFaden()
        if (entliehenAn != null && entliehenAn != zweck) {
            // Ein fremder Zweck gibt zurück: das ist eine verlorene
            // Ausleihe, die jemand aufräumt. Melden, nicht verschlucken.
            Erkennungsprotokoll.aufruf(
                "fremde Rückgabe", "$zweck räumt Ausleihe von $entliehenAn auf"
            )
        }
        entliehenAn = null
        val vorhanden = erkenner
        runCatching { vorhanden?.setRecognitionListener(null) }
        if (wegwerfen && vorhanden != null) {
            erkenner = null
            runCatching { vorhanden.cancel() }
            runCatching { vorhanden.destroy() }
        }
        Erkennungsprotokoll.aufruf("Erkenner zurück", zweck + if (wegwerfen) ", verworfen" else "")
    }

    /** Zerstört den Erkenner endgültig -- etwa wenn der Dienst geht. */
    fun schliesse() {
        pruefeFaden()
        val vorhanden = erkenner ?: return
        erkenner = null
        entliehenAn = null
        runCatching { vorhanden.cancel() }
        runCatching { vorhanden.destroy() }
    }

    /** Wahr, solange ihn jemand hat. */
    fun istVerliehen(): Boolean = entliehenAn != null

    private fun baue(): SpeechRecognizer? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }.getOrNull()

    /**
     * Ein Aufruf vom falschen Faden wäre ein Fehler, der sich später als
     * unerklärliches Schweigen des Erkenners zeigt. Lieber hier auffallen.
     */
    private fun pruefeFaden() {
        check(Looper.myLooper() === Looper.getMainLooper()) {
            "Erkennerhalter gehört auf den Hauptfaden"
        }
    }

    companion object {
        val hauptfaden: Handler get() = Handler(Looper.getMainLooper())
    }
}
