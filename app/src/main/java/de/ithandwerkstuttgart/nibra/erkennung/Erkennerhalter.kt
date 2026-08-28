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
     * Laufende Nummer der aktuellen Ausleihe.
     *
     * Sie ist die Antwort auf einen am Gerät gemessenen Wettlauf: das
     * Dauerdiktat startet den nächsten Satz, **bevor** der vorige seine
     * Ausleihe zurückgegeben hat -- beides läuft über `post` auf denselben
     * Faden, die Reihenfolge ist nicht garantiert. Ohne Nummer räumte die
     * verspätete Rückgabe des alten Satzes die Ausleihe des neuen ab:
     *
     * ```
     * <- onResults  lesarten=2
     * -> Vorrang  Diktat verdrängt Diktat
     * <- onError  code=11
     * ```
     *
     * Mit Nummer ist eine verspätete Rückgabe ein stilles Nichts.
     */
    private var laufendeMarke = 0L

    /** Eine Ausleihe: der Erkenner und die Marke, die zur Rückgabe gehört. */
    class Ausleihe internal constructor(
        val erkenner: SpeechRecognizer,
        internal val marke: Long
    )

    /**
     * Leiht den Erkenner aus.
     *
     * @param vorrang wahr für das Diktat: es wartet nicht. Hält derselbe
     *        Zweck ihn noch (der vorige Satz), wird die Sitzung übernommen
     *        statt zerstört -- der Erkenner bleibt warm, und genau dafür
     *        gab es ihn ursprünglich. Ein fremder Zweck wird verdrängt.
     * @return `null`, wenn er belegt ist (ohne Vorrang) oder sich nicht
     *         anlegen lässt. `null` ist eine Auskunft: der Aufrufer meldet
     *         dann einen ehrlichen Zustand, statt zu warten.
     */
    fun leihe(zweck: String, vorrang: Boolean = false): Ausleihe? {
        pruefeFaden()
        entliehenAn?.let { anderer ->
            if (!vorrang) {
                Erkennungsprotokoll.aufruf("leihe abgelehnt", "$zweck, belegt von $anderer")
                return null
            }
            if (anderer == zweck) {
                // Der nächste Satz desselben Diktats: Sitzung übernehmen,
                // Erkenner behalten. Zerstören und sofort neu anlegen
                // quittiert der Systemdienst mit SERVER_DISCONNECTED.
                Erkennungsprotokoll.aufruf("Übernahme", zweck)
                runCatching { erkenner?.setRecognitionListener(null) }
                runCatching { erkenner?.cancel() }
            } else {
                // Ein fremder Zweck hält ihn -- etwa die Sprachabfrage, die
                // auf dem S23 Ultra nie eine Antwort bekommt. Das Diktat
                // wartet nicht: wegwerfen, frisch anfangen.
                Erkennungsprotokoll.aufruf("Vorrang", "$zweck verdrängt $anderer")
                val alter = erkenner
                erkenner = null
                runCatching { alter?.setRecognitionListener(null) }
                runCatching { alter?.cancel() }
                runCatching { alter?.destroy() }
            }
            entliehenAn = null
        }
        val vorhanden = erkenner ?: baue() ?: return null
        erkenner = vorhanden
        entliehenAn = zweck
        laufendeMarke += 1
        Erkennungsprotokoll.aufruf("Erkenner verliehen", "$zweck (Schein ${laufendeMarke})")
        return Ausleihe(vorhanden, laufendeMarke)
    }

    /**
     * Gibt eine Ausleihe zurück.
     *
     * Eine **verspätete** Rückgabe -- der Schein gehört nicht mehr zur
     * laufenden Ausleihe -- ist ein stilles Nichts. Das ist kein Randfall,
     * sondern der Normalfall beim Dauerdiktat: der neue Satz leiht, bevor
     * der alte zurückgibt.
     *
     * @param wegwerfen wahr, wenn der Erkenner unbrauchbar geworden ist.
     */
    fun gibZurueck(ausleihe: Ausleihe, wegwerfen: Boolean = false) {
        pruefeFaden()
        if (ausleihe.marke != laufendeMarke || entliehenAn == null) {
            Erkennungsprotokoll.aufruf(
                "verspätete Rückgabe", "Schein ${ausleihe.marke}, still verworfen"
            )
            return
        }
        val zweck = entliehenAn
        entliehenAn = null
        val vorhanden = erkenner
        runCatching { vorhanden?.setRecognitionListener(null) }
        if (wegwerfen && vorhanden != null) {
            erkenner = null
            runCatching { vorhanden.cancel() }
            runCatching { vorhanden.destroy() }
        }
        Erkennungsprotokoll.aufruf(
            "Erkenner zurück", "$zweck (Schein ${ausleihe.marke})" +
                if (wegwerfen) ", verworfen" else ""
        )
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
