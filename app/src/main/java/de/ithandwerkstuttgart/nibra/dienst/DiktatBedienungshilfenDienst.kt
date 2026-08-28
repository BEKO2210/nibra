package de.ithandwerkstuttgart.nibra.dienst

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.TextView
import dagger.hilt.android.AndroidEntryPoint
import de.ithandwerkstuttgart.nibra.R
import de.ithandwerkstuttgart.nibra.daten.DiktatDao
import de.ithandwerkstuttgart.nibra.daten.DiktatEintrag
import de.ithandwerkstuttgart.nibra.daten.EinstellungenAblage
import de.ithandwerkstuttgart.nibra.daten.TextbausteinDao
import de.ithandwerkstuttgart.nibra.erkennung.Erkennungsereignis
import de.ithandwerkstuttgart.nibra.erkennung.Spracherkenner
import de.ithandwerkstuttgart.nibra.erkennung.setzeSatzzeichen
import de.ithandwerkstuttgart.nibra.erkennung.wendeBausteineAn
import de.ithandwerkstuttgart.nibra.ui.modell.Fehlerart
import de.ithandwerkstuttgart.nibra.ui.modell.Textbaustein
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

/**
 * Bedienungshilfen-Dienst von Nibra: uebernimmt aus der MIT-Vorlage
 * aidictation (Herkunft und Lizenz siehe FREMDSOFTWARE.md) ausschliesslich
 * den Ansatz -- eine schwebende Aufnahmeflaeche ueber fremden Apps, die
 * erkannten Text an der Cursorposition einfuegt.
 *
 * Er liest nie mit und laesst Passwortfelder unberuehrt (AUFTRAG.md,
 * Antwort 9). Waehrend des Sprechens wird der Zwischenstand laufend in das
 * Feld geschrieben; am Ende steht dort der endgueltige Text.
 */
@AndroidEntryPoint
class DiktatBedienungshilfenDienst : AccessibilityService() {

    @Inject lateinit var erkenner: Spracherkenner

    @Inject lateinit var diktatDao: DiktatDao

    @Inject lateinit var bausteinDao: TextbausteinDao

    @Inject lateinit var ablage: EinstellungenAblage

    private val bereich = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val hauptfaden = Handler(Looper.getMainLooper())
    private var erkennungsAuftrag: Job? = null
    private var laeuftErkennung = false

    private lateinit var fensterVerwaltung: WindowManager
    private var blaseAnsicht: ImageButton? = null
    private var blaseParameter: WindowManager.LayoutParams? = null
    private var bandAnsicht: TextView? = null

    /** Position, an der das laufende Diktat im Feld beginnt. */
    private var einfuegeStelle: Int = -1

    /** Laenge des zuletzt geschriebenen Zwischenstands. */
    private var geschriebeneLaenge: Int = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        fensterVerwaltung = getSystemService(WINDOW_SERVICE) as WindowManager
        Dienstbruecke.melde(this)
        aktualisiereBlase()
    }

    /**
     * Jedes dieser Ereignisse kann bedeuten, dass ein anderes Feld den
     * Fokus hat -- also jedes Mal neu pruefen. Frueher verschwand die Blase
     * beim Fensterwechsel und kam in fremden Apps nie wieder.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> aktualisiereBlase()
        }
    }

    override fun onInterrupt() {
        verbergeBlase()
    }

    override fun onDestroy() {
        Dienstbruecke.melde(null)
        beendeErkennung()
        bereich.cancel()
        verbergeBand()
        verbergeBlase()
        super.onDestroy()
    }

    /**
     * Zeigt die Blase, solange ein editierbares Feld den Fokus hat -- nie
     * ueber einem Passwortfeld. Waehrend einer laufenden Aufnahme bleibt sie
     * stehen, auch wenn das Feld kurz den Fokus verliert.
     */
    private fun aktualisiereBlase() {
        if (laeuftErkennung) return
        // Passwortfelder und die Bildschirmsperre filtert bereits
        // `fokussiertesEingabefeld()` heraus.
        if (fokussiertesEingabefeld() == null) {
            verbergeBlase()
            return
        }
        zeigeBlase()
    }

    /**
     * Nur ein fokussiertes, editierbares Feld zaehlt -- ein fokussierter
     * Knopf oder Text ist keine Diktatstelle.
     *
     * Hier sitzt die einzige Sperre der App (Roadmap, Lauf 2.5). Sie liefert
     * nichts, solange der Bildschirm gesperrt ist, und nichts fuer
     * Passwortfelder. Frueher stand die Passwortpruefung an vier Stellen
     * verstreut; eine vergessene Stelle haette gereicht, um in ein
     * Passwortfeld zu schreiben.
     */
    private fun fokussiertesEingabefeld(): AccessibilityNodeInfo? {
        if (bildschirmGesperrt()) return null
        val wurzel = rootInActiveWindow ?: return null
        val fokus = wurzel.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        if (!fokus.isEditable) return null
        if (Feldschutz.istGeschuetzt(fokus.isPassword, fokus.inputType)) return null
        return fokus
    }

    /**
     * Wahr, solange die Bildschirmsperre steht. Ueber einer Sperre hat die
     * Blase nichts zu suchen: was dort eingegeben wird, geht Nibra nichts an.
     */
    private fun bildschirmGesperrt(): Boolean {
        val sperre = getSystemService(KeyguardManager::class.java) ?: return false
        return sperre.isKeyguardLocked
    }

    private fun zeigeBlase() {
        if (blaseAnsicht != null) return
        val gemerkt = getSharedPreferences(BLASE_ABLAGE, Context.MODE_PRIVATE)
        val ansicht = ImageButton(this).apply {
            setImageResource(R.drawable.nb_ic_mikrofon)
            contentDescription = getString(R.string.sw_aufnahme_starten)
            background = getDrawable(R.drawable.nb_blase_hintergrund)
            // Helles Symbol auf der Akzentfarbe -- gleicher Kontrast wie in der App.
            imageTintList = android.content.res.ColorStateList.valueOf(
                getColor(R.color.marke_papier)
            )
            setPadding(inDp(BLASE_INNEN_DP))
            elevation = inDp(BLASE_SCHATTEN_DP).toFloat()
        }
        val parameter = WindowManager.LayoutParams(
            inDp(BLASE_DP),
            inDp(BLASE_DP),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = gemerkt.getInt(SCHLUESSEL_X, inDp(RAND_DP))
            // Standardplatz auf halber Hoehe: dort liegt die Blase weder auf
            // der Tastatur noch auf dem Eingabefeld.
            y = gemerkt.getInt(SCHLUESSEL_Y, resources.displayMetrics.heightPixels / 2)
        }
        ansicht.setOnTouchListener(blasenGriff(parameter, gemerkt))
        runCatching { fensterVerwaltung.addView(ansicht, parameter) }
            .onFailure { return }
        blaseAnsicht = ansicht
        blaseParameter = parameter
    }

    /**
     * Die Blase laesst sich verschieben. Eine kurze Beruehrung ohne
     * nennenswerte Bewegung gilt als Tippen und startet das Diktat.
     */
    private fun blasenGriff(
        parameter: WindowManager.LayoutParams,
        gemerkt: android.content.SharedPreferences
    ) = object : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var fingerX = 0f
        private var fingerY = 0f
        private var gezogen = false

        override fun onTouch(ansicht: View, ereignis: MotionEvent): Boolean {
            when (ereignis.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = parameter.x
                    startY = parameter.y
                    fingerX = ereignis.rawX
                    fingerY = ereignis.rawY
                    gezogen = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val abstandX = ereignis.rawX - fingerX
                    val abstandY = ereignis.rawY - fingerY
                    if (!gezogen &&
                        abs(abstandX) < inDp(ZIEHSCHWELLE_DP) &&
                        abs(abstandY) < inDp(ZIEHSCHWELLE_DP)
                    ) {
                        return true
                    }
                    gezogen = true
                    // Die Blase haengt unten rechts -- deshalb umgekehrtes Vorzeichen.
                    parameter.x = (startX - abstandX).toInt().coerceAtLeast(0)
                    parameter.y = (startY - abstandY).toInt().coerceAtLeast(0)
                    runCatching { fensterVerwaltung.updateViewLayout(ansicht, parameter) }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (gezogen) {
                        gemerkt.edit()
                            .putInt(SCHLUESSEL_X, parameter.x)
                            .putInt(SCHLUESSEL_Y, parameter.y)
                            .apply()
                    } else {
                        ansicht.performClick()
                        aufBlaseGetippt()
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun verbergeBlase() {
        val ansicht = blaseAnsicht ?: return
        runCatching { fensterVerwaltung.removeView(ansicht) }
        blaseAnsicht = null
        blaseParameter = null
    }

    /**
     * Tippen auf die Blase startet die Erkennung, ein zweites Tippen
     * beendet sie. Zwischenstaende laufen direkt in das Feld -- man sieht
     * beim Sprechen, was ankommt.
     */
    private fun aufBlaseGetippt() {
        if (laeuftErkennung) {
            erkenner.stoppen()
            return
        }
        val feld = fokussiertesEingabefeld()
        if (feld == null) {
            melde(R.string.sw_meldung_kein_feld)
            return
        }
        // Ab hier wird an dieser Stelle geschrieben, bis das Diktat endet.
        einfuegeStelle = feld.textSelectionEnd
            .takeIf { it >= 0 }
            ?: feld.text?.length
            ?: 0
        geschriebeneLaenge = 0
        laeuftErkennung = true
        setzeBlasenbild(R.drawable.nb_ic_stopp, R.string.sw_aufnahme_beenden)

        erkennungsAuftrag = bereich.launch {
            // Blase und App diktieren in derselben Sprache und mit derselben
            // Stille-Einstellung -- beides kommt aus der gemeinsamen Ablage.
            val gespeichert = ablage.fluss.first()
            val standard = Locale.getDefault()
            val sprachCode = gespeichert.diktatSprachCode.ifBlank {
                if (standard.country.isNotBlank()) {
                    "${standard.language}-${standard.country}"
                } else {
                    standard.language
                }
            }
            val bausteine = bausteinDao.alleEinmalig()
                .map { Textbaustein(it.id, it.kuerzel, it.ersatz) }

            // Ohne "Stopp bei Stille" laeuft das Diktat weiter: nach jedem
            // Satz hoert Nibra von selbst wieder zu, bis der Nutzer die Blase
            // erneut antippt. Sonst endet es nach dem ersten Satz.
            val dauerdiktat = !gespeichert.stoppBeiStille
            var leereDurchgaenge = 0

            while (laeuftErkennung) {
                var etwasVerstanden = false
                erkenner.erkenne(sprachCode, gespeichert.stoppBeiStille).collect { ereignis ->
                    when (ereignis) {
                        is Erkennungsereignis.Teiltext -> withContext(Dispatchers.Main) {
                            // Zwischenstand ohne Bausteine: er aendert sich noch.
                            schreibeLaufend(ereignis.text)
                        }

                        is Erkennungsereignis.Ergebnis -> {
                            etwasVerstanden = true
                            val gesprochen = setzeSatzzeichen(ereignis.text.trim(), sprachCode)
                            val text = wendeBausteineAn(gesprochen, bausteine)
                            val steht = withContext(Dispatchers.Main) { schreibeLaufend(text) }
                            if (!steht) melde(R.string.sw_meldung_nicht_eingefuegt)
                            speichere(text, sprachCode)
                            // Der naechste Satz haengt sich hinten an, statt
                            // diesen zu ersetzen.
                            festschreiben()
                        }

                        is Erkennungsereignis.Fehlgeschlagen -> {
                            // Beim Dauerdiktat ist "nichts verstanden" nur eine
                            // Sprechpause; alles andere beendet das Diktat.
                            if (dauerdiktat && ereignis.art == Fehlerart.NICHTS_VERSTANDEN) {
                                leereDurchgaenge += 1
                            } else {
                                melde(fehlertext(ereignis.art))
                                laeuftErkennung = false
                            }
                        }

                        else -> Unit
                    }
                }

                if (etwasVerstanden) leereDurchgaenge = 0
                if (!dauerdiktat || leereDurchgaenge >= STILLE_DURCHGAENGE) break
            }
            beendeErkennung()
        }
    }

    /**
     * Schliesst den geschriebenen Satz ab: der naechste beginnt dahinter.
     */
    private fun festschreiben() {
        einfuegeStelle += geschriebeneLaenge
        geschriebeneLaenge = 0
    }

    /**
     * Klartext-Hinweis direkt an der Blase. Ein Hinweisband statt einer
     * Kurzmeldung, weil Android die Kurzmeldungen einer App unterdrueckt,
     * sobald deren Benachrichtigungen aus sind -- dann saehe der Nutzer gar
     * nichts.
     */
    private fun melde(text: Int) {
        hauptfaden.post {
            verbergeBand()
            val band = TextView(this).apply {
                setText(text)
                setTextColor(getColor(R.color.marke_papier))
                background = getDrawable(R.drawable.nb_band_hintergrund)
                textSize = BAND_SCHRIFT_SP
                val luft = inDp(BAND_INNEN_DP)
                setPadding(luft, luft, luft, luft)
                elevation = inDp(BLASE_SCHATTEN_DP).toFloat()
            }
            val parameter = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = inDp(RAND_DP)
                y = (blaseParameter?.y ?: (resources.displayMetrics.heightPixels / 2)) +
                    inDp(BLASE_DP + 8)
                width = fensterbreite() - inDp(2 * RAND_DP)
            }
            runCatching { fensterVerwaltung.addView(band, parameter) }
                .onSuccess {
                    bandAnsicht = band
                    hauptfaden.postDelayed({ verbergeBand() }, BAND_DAUER_MILLIS)
                }
        }
    }

    private fun verbergeBand() {
        val band = bandAnsicht ?: return
        runCatching { fensterVerwaltung.removeView(band) }
        bandAnsicht = null
    }

    private fun fensterbreite(): Int = resources.displayMetrics.widthPixels

    private fun fehlertext(art: Fehlerart): Int = when (art) {
        Fehlerart.KEIN_MIKROFON_RECHT -> R.string.sw_fehler_kein_mikrofon_recht
        Fehlerart.ERKENNUNG_NICHT_VERFUEGBAR -> R.string.sw_fehler_erkennung_nicht_verfuegbar
        Fehlerart.SPRACHE_NICHT_AUF_GERAET -> R.string.sw_fehler_sprache_nicht_auf_geraet
        Fehlerart.NICHTS_VERSTANDEN -> R.string.sw_fehler_nichts_verstanden
        Fehlerart.UNBEKANNT -> R.string.sw_fehler_unbekannt
    }

    private fun speichere(text: String, sprachCode: String) {
        bereich.launch {
            diktatDao.sichere(
                DiktatEintrag(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    zeitpunktMillis = System.currentTimeMillis(),
                    sprachCode = sprachCode,
                    dauerSekunden = 0
                )
            )
        }
    }

    private fun beendeErkennung() {
        laeuftErkennung = false
        erkennungsAuftrag?.cancel()
        erkennungsAuftrag = null
        einfuegeStelle = -1
        geschriebeneLaenge = 0
        hauptfaden.post {
            setzeBlasenbild(R.drawable.nb_ic_mikrofon, R.string.sw_aufnahme_starten)
            aktualisiereBlase()
        }
    }

    private fun setzeBlasenbild(zeichnung: Int, beschreibung: Int) {
        blaseAnsicht?.apply {
            setImageResource(zeichnung)
            contentDescription = getString(beschreibung)
        }
    }

    /**
     * Schreibt den aktuellen Stand an die gemerkte Stelle und ersetzt dabei
     * den vorherigen Stand -- so waechst der Satz im Feld mit, statt sich zu
     * wiederholen. Steht davor schon Text, kommt ein Leerzeichen dazwischen.
     */
    private fun schreibeLaufend(text: String): Boolean {
        val feld = fokussiertesEingabefeld() ?: return false
        val vorhanden = feld.text?.toString().orEmpty()
        val start = einfuegeStelle.coerceIn(0, vorhanden.length)
        val ende = (start + geschriebeneLaenge).coerceIn(start, vorhanden.length)
        val davor = vorhanden.substring(0, start)
        val einzufuegen = mitAbstand(davor, text)
        val neuerText = davor + einzufuegen + vorhanden.substring(ende)
        if (!setzeText(feld, neuerText)) return false
        geschriebeneLaenge = einzufuegen.length
        setzeCursor(feld, start + einzufuegen.length)
        return true
    }

    /**
     * Haengt ein Leerzeichen vor den neuen Text, wenn davor schon etwas steht
     * und weder dort noch am Anfang des neuen Textes eine Luecke ist. Nach
     * einem Zeilenumbruch oder einer oeffnenden Klammer bleibt es dicht.
     */
    private fun mitAbstand(davor: String, text: String): String {
        if (davor.isEmpty() || text.isEmpty()) return text
        val letztes = davor.last()
        if (letztes.isWhitespace() || letztes in OHNE_ABSTAND_DAVOR) return text
        if (text.first().isWhitespace() || text.first() in OHNE_ABSTAND_DANACH) return text
        return " " + text
    }

    /** Fuegt Text an der Cursorposition ein -- fuer die App selbst. */
    fun fuegeTextEin(text: String): Boolean {
        val feld = fokussiertesEingabefeld() ?: return false
        val vorhanden = feld.text?.toString().orEmpty()
        val anfang = feld.textSelectionStart.takeIf { it >= 0 } ?: vorhanden.length
        val ende = feld.textSelectionEnd.takeIf { it >= 0 } ?: anfang
        val von = minOf(anfang, ende).coerceIn(0, vorhanden.length)
        val bis = maxOf(anfang, ende).coerceIn(0, vorhanden.length)
        val neuerText = vorhanden.substring(0, von) + text + vorhanden.substring(bis)
        if (!setzeText(feld, neuerText)) return false
        setzeCursor(feld, von + text.length)
        return true
    }

    private fun setzeText(feld: AccessibilityNodeInfo, text: String): Boolean {
        val argumente = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return feld.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, argumente)
    }

    /**
     * Cursor hinter den geschriebenen Text. Viele Apps setzen ihn nach dem
     * Schreiben selbst auf den Anfang zurueck, und der Knoten kennt den neuen
     * Text erst nach [AccessibilityNodeInfo.refresh]. Deshalb erst
     * auffrischen, dann setzen -- und kurz darauf noch einmal, falls die App
     * dazwischenfunkt.
     */
    private fun setzeCursor(feld: AccessibilityNodeInfo, position: Int) {
        fun setzen() {
            runCatching { feld.refresh() }
            val laenge = feld.text?.length ?: position
            val ziel = position.coerceIn(0, laenge)
            val auswahl = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, ziel)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, ziel)
            }
            runCatching {
                feld.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, auswahl)
            }
        }
        setzen()
        hauptfaden.postDelayed({ setzen() }, CURSOR_NACHFASSEN_MILLIS)
    }

    private fun inDp(wert: Int): Int =
        (wert * resources.displayMetrics.density).toInt()

    private fun View.setPadding(wert: Int) = setPadding(wert, wert, wert, wert)

    private companion object {
        /** Durchmesser der Blase -- deutlich ueber dem Mindesttippziel. */
        const val BLASE_DP = 56

        /** Luft zwischen Rand und Symbol. */
        const val BLASE_INNEN_DP = 14

        /** Abstand der Blase zum Bildschirmrand, aus der Abstandsskala. */
        const val RAND_DP = 16

        /** Leichter Schatten, damit die Blase ueber fremden Apps abhebt. */
        const val BLASE_SCHATTEN_DP = 4

        /** Ab dieser Bewegung gilt es als Ziehen, nicht als Tippen. */
        const val ZIEHSCHWELLE_DP = 8

        /** Wie lange das Hinweisband stehen bleibt. */
        const val BAND_DAUER_MILLIS = 3_500L
        const val BAND_INNEN_DP = 12
        const val BAND_SCHRIFT_SP = 14f

        /** So oft darf beim Dauerdiktat nichts kommen, bevor es endet. */
        const val STILLE_DURCHGAENGE = 3

        /** Nach dieser Zeit wird die Cursorposition noch einmal gesetzt. */
        const val CURSOR_NACHFASSEN_MILLIS = 120L

        /** Nach diesen Zeichen kommt kein Leerzeichen. */
        val OHNE_ABSTAND_DAVOR = setOf('(', '[', '{', '"', '\'', '-', '/')

        /** Vor diesen Zeichen kommt kein Leerzeichen. */
        val OHNE_ABSTAND_DANACH = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}')

        const val BLASE_ABLAGE = "nibra_blase"
        const val SCHLUESSEL_X = "blase_x"
        const val SCHLUESSEL_Y = "blase_y"
    }
}
