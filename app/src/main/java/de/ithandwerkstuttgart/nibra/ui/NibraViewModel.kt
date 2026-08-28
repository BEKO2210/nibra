package de.ithandwerkstuttgart.nibra.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ithandwerkstuttgart.nibra.daten.DiktatDao
import de.ithandwerkstuttgart.nibra.daten.DiktatEintrag
import de.ithandwerkstuttgart.nibra.daten.EinstellungenAblage
import de.ithandwerkstuttgart.nibra.daten.TextbausteinDao
import de.ithandwerkstuttgart.nibra.daten.TextbausteinEintrag
import de.ithandwerkstuttgart.nibra.erkennung.Erkennerquelle
import de.ithandwerkstuttgart.nibra.erkennung.Erkennungsereignis
import de.ithandwerkstuttgart.nibra.erkennung.Spracherkenner
import de.ithandwerkstuttgart.nibra.erkennung.Sprachverzeichnis
import de.ithandwerkstuttgart.nibra.erkennung.setzeSatzzeichen
import de.ithandwerkstuttgart.nibra.erkennung.wendeBausteineAn
import de.ithandwerkstuttgart.nibra.ui.modell.Aufnahmezustand
import de.ithandwerkstuttgart.nibra.ui.modell.Dienstzustand
import de.ithandwerkstuttgart.nibra.ui.modell.Diktat
import de.ithandwerkstuttgart.nibra.ui.modell.Diktatsprache
import de.ithandwerkstuttgart.nibra.ui.modell.Einstellungen
import de.ithandwerkstuttgart.nibra.ui.modell.Fehlerart
import de.ithandwerkstuttgart.nibra.ui.modell.Mikrofonzustand
import de.ithandwerkstuttgart.nibra.ui.modell.Textbaustein
import de.ithandwerkstuttgart.nibra.ui.modell.VerlaufGruppe
import de.ithandwerkstuttgart.nibra.verlauf.ordneVerlauf
import de.ithandwerkstuttgart.nibra.verlauf.suche
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** Kurze Rueckmeldung an den Nutzer, im Bildschirm als Einblendung. */
enum class Meldung {
    KOPIERT,
    EINGEFUEGT,
    NICHT_EINGEFUEGT,
    DIKTAT_GELOESCHT,
    DIKTAT_ZURUECKGEHOLT,
    BAUSTEIN_GESICHERT,
    BAUSTEIN_GELOESCHT,
    SPRACHE_WIRD_GELADEN
}

/** Alles, was die Oberflaeche von Nibra zu einem Zeitpunkt anzeigt. */
data class NibraZustand(
    val geladen: Boolean = false,
    val eingerichtet: Boolean = false,
    val aufnahme: Aufnahmezustand = Aufnahmezustand.Bereit,
    val diktate: List<Diktat> = emptyList(),
    val suchbegriff: String = "",
    val textbausteine: List<Textbaustein> = emptyList(),
    val sprachen: List<Diktatsprache> = emptyList(),
    /** Wahr, solange das Geraet nach seinen Sprachen gefragt wird. */
    val sprachenLaden: Boolean = false,
    /** Wahr, sobald der Verlauf einmal aus der Ablage kam. */
    val verlaufGeladen: Boolean = false,
    val gewaehlterSprachCode: String = "",
    val stoppBeiStille: Boolean = true,
    val aufnahmenBehalten: Boolean = false,
    val mikrofonzustand: Mikrofonzustand = Mikrofonzustand.NICHT_ERTEILT,
    val dienstzustand: Dienstzustand = Dienstzustand.NICHT_EINGERICHTET,
    /** Gesetzt, solange ein bestehender Eintrag neu diktiert wird. */
    val erneuteErkennungFuer: String? = null,
    /** Kennung des zuletzt fertig erkannten Diktats -- es bleibt auf der
     *  Aufnahmeflaeche stehen, bis das naechste beginnt. */
    val letztesDiktatId: String? = null,
    val meldung: Meldung? = null,
    /** Wahr, solange das zuletzt geloeschte Diktat zurueckgeholt werden kann. */
    val kannZurueckholen: Boolean = false
) {
    val gruppen: List<VerlaufGruppe>
        get() = ordneVerlauf(suche(diktate, suchbegriff), System.currentTimeMillis())

    /**
     * Sprachliste mit Markierung "zuletzt genutzt": die aktuelle Wahl und
     * die Sprachen der juengsten Diktate stehen oben.
     */
    val sprachenMitVerlauf: List<Diktatsprache>
        get() {
            val juengste = diktate.asSequence()
                .map { it.sprachCode }
                .filter { it.isNotBlank() }
                .distinct()
                .take(JUENGSTE_SPRACHEN)
                .toMutableSet()
            if (gewaehlterSprachCode.isNotBlank()) juengste += gewaehlterSprachCode
            return sprachen.map { it.copy(zuletztGenutzt = it.code in juengste) }
        }

    /** Das zuletzt fertige Diktat, frisch aus der Ablage. */
    val letztesDiktat: Diktat?
        get() = letztesDiktatId?.let { kennung -> diktate.firstOrNull { it.id == kennung } }

    /** Waehrend "Wandelt" darf die Aufnahmeflaeche nichts Neues starten. */
    val aufnahmeflaecheAktiv: Boolean
        get() = aufnahme !is Aufnahmezustand.Wandelt

    private companion object {
        const val JUENGSTE_SPRACHEN = 3
    }
}

@HiltViewModel
class NibraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diktatDao: DiktatDao,
    private val textbausteinDao: TextbausteinDao,
    private val ablage: EinstellungenAblage,
    private val erkenner: Erkennerquelle,
    private val sprachverzeichnis: Sprachverzeichnis
) : ViewModel() {

    private val _zustand = MutableStateFlow(NibraZustand())
    val zustand: StateFlow<NibraZustand> = _zustand.asStateFlow()

    private var aufnahmeAuftrag: Job? = null
    private var uhrAuftrag: Job? = null
    private var startMillis: Long = 0L

    /** Eintrag, der gerade neu diktiert (also ersetzt) wird. */
    private var erneutErkannt: String? = null

    /** Das zuletzt geloeschte Diktat, solange es zurueckgeholt werden kann. */
    private var zuletztGeloescht: DiktatEintrag? = null

    init {
        viewModelScope.launch {
            diktatDao.alle().collect { eintraege ->
                _zustand.update {
                    it.copy(diktate = eintraege.map(::zuDiktat), verlaufGeladen = true)
                }
            }
        }
        viewModelScope.launch {
            textbausteinDao.alle().collect { eintraege ->
                _zustand.update { zustand ->
                    zustand.copy(
                        textbausteine = eintraege.map {
                            Textbaustein(it.id, it.kuerzel, it.ersatz)
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            ablage.fluss.collectLatest { gespeichert ->
                _zustand.update { zustand ->
                    zustand.copy(
                        geladen = true,
                        eingerichtet = gespeichert.eingerichtet,
                        stoppBeiStille = gespeichert.stoppBeiStille,
                        aufnahmenBehalten = gespeichert.aufnahmenBehalten,
                        gewaehlterSprachCode = gespeichert.diktatSprachCode.ifBlank {
                            schlichterSprachcode(anzeigeSprache())
                        }
                    )
                }
            }
        }
        ladeSprachen()
    }

    // ---------------------------------------------------------------- Rechte

    fun meldeZustaende(mikrofonErteilt: Boolean, dienstAktiv: Boolean) {
        _zustand.update { zustand ->
            zustand.copy(
                mikrofonzustand =
                    if (mikrofonErteilt) Mikrofonzustand.ERTEILT else Mikrofonzustand.NICHT_ERTEILT,
                dienstzustand =
                    if (dienstAktiv) Dienstzustand.EINGERICHTET else Dienstzustand.NICHT_EINGERICHTET
            )
        }
    }

    fun merkeEingerichtet() {
        viewModelScope.launch { ablage.setzeEingerichtet(true) }
    }

    // -------------------------------------------------------------- Aufnahme

    /**
     * Ein Tipp auf die Aufnahmeflaeche. Starten geht nur aus [Aufnahmezustand.Bereit]
     * oder aus einem Fehler, Beenden nur aus [Aufnahmezustand.Laeuft]. Waehrend
     * der Umwandlung passiert nichts -- sonst ginge das Ergebnis verloren.
     */
    fun aufnahmeUmschalten() {
        when (_zustand.value.aufnahme) {
            is Aufnahmezustand.Laeuft -> {
                erkenner.stoppen()
                uhrAuftrag?.cancel()
                _zustand.update { it.copy(aufnahme = Aufnahmezustand.Wandelt) }
            }

            is Aufnahmezustand.Wandelt -> Unit

            is Aufnahmezustand.Bereit,
            is Aufnahmezustand.Fehler -> starteAufnahme(ersetzeDiktatId = null)
        }
    }

    /** Nach einem Fehler: derselbe Versuch noch einmal, sofort. */
    fun erneutVersuchen() {
        val ersetzt = _zustand.value.erneuteErkennungFuer
        starteAufnahme(ersetzeDiktatId = ersetzt)
    }

    /**
     * Nimmt einen bestehenden Eintrag neu auf -- in der Sprache, die am
     * Eintrag steht. Der Bildschirm mit Dauer, Pegel und Stopp ist die
     * Aufnahmeflaeche; dorthin fuehrt die Oberflaeche nach diesem Aufruf.
     */
    fun erneutErkennen(diktatId: String) {
        val diktat = _zustand.value.diktate.firstOrNull { it.id == diktatId }
        erneutErkannt = diktatId
        starteAufnahme(ersetzeDiktatId = diktatId, sprachCode = diktat?.sprachCode)
    }

    fun fehlerZuruecksetzen() {
        _zustand.update { it.copy(aufnahme = Aufnahmezustand.Bereit, erneuteErkennungFuer = null) }
    }

    private fun starteAufnahme(ersetzeDiktatId: String?, sprachCode: String? = null) {
        if (ersetzeDiktatId == null) erneutErkannt = null
        aufnahmeAuftrag?.cancel()
        uhrAuftrag?.cancel()
        startMillis = System.currentTimeMillis()
        val code = sprachCode?.takeIf { it.isNotBlank() } ?: _zustand.value.gewaehlterSprachCode
        val stoppBeiStille = _zustand.value.stoppBeiStille
        _zustand.update {
            it.copy(
                aufnahme = Aufnahmezustand.Laeuft(
                    pegel = 0f,
                    dauerSekunden = 0,
                    verlauf = emptyList()
                ),
                erneuteErkennungFuer = ersetzeDiktatId,
                letztesDiktatId = null
            )
        }

        uhrAuftrag = viewModelScope.launch {
            // Die Uhr laeuft hoechstens bis zur Obergrenze; danach beendet
            // Nibra die Aufnahme selbst, statt ewig weiterzuzaehlen.
            var sekunden = 0
            while (sekunden < HOECHSTDAUER_SEKUNDEN) {
                delay(1_000)
                val laufend = _zustand.value.aufnahme as? Aufnahmezustand.Laeuft ?: return@launch
                // Jeder Takt zaehlt mindestens eine Sekunde weiter. Die Uhr des
                // Geraets darf den Zaehler nach vorn holen -- etwa wenn Nibra im
                // Hintergrund war und Takte ausgefallen sind -- aber nie
                // zurueckhalten. Ohne diese Untergrenze dreht die Schleife
                // endlos, sobald `delay` schneller laeuft als die Uhr.
                sekunden = maxOf(
                    sekunden + 1,
                    ((System.currentTimeMillis() - startMillis) / 1000).toInt()
                )
                _zustand.update { it.copy(aufnahme = laufend.copy(dauerSekunden = sekunden)) }
            }
            if (_zustand.value.aufnahme is Aufnahmezustand.Laeuft) {
                erkenner.stoppen()
                _zustand.update { it.copy(aufnahme = Aufnahmezustand.Wandelt) }
            }
        }

        aufnahmeAuftrag = viewModelScope.launch {
            // Ohne "Stopp bei Stille" hoert Nibra nach jedem Satz weiter zu,
            // bis der Nutzer beendet -- sonst waere nach einem Satz Schluss.
            val dauerdiktat = !stoppBeiStille
            var sammelId: String? = ersetzeDiktatId
            var leereDurchgaenge = 0
            var weiter = true

            while (weiter) {
                var etwasVerstanden = false
                erkenner.erkenne(code, stoppBeiStille).collect { ereignis ->
                when (ereignis) {
                    is Erkennungsereignis.Hoert -> Unit

                    is Erkennungsereignis.Pegel -> _zustand.update { zustand ->
                        val laufend = zustand.aufnahme as? Aufnahmezustand.Laeuft
                            ?: return@update zustand
                        zustand.copy(
                            aufnahme = laufend.copy(
                                pegel = ereignis.wert,
                                verlauf = (laufend.verlauf + ereignis.wert).takeLast(KURVENPUNKTE)
                            )
                        )
                    }

                    is Erkennungsereignis.Stille -> _zustand.update { zustand ->
                        val laufend = zustand.aufnahme as? Aufnahmezustand.Laeuft
                            ?: return@update zustand
                        zustand.copy(aufnahme = laufend.copy(stilleErkannt = true))
                    }

                    is Erkennungsereignis.Teiltext -> _zustand.update { zustand ->
                        val laufend = zustand.aufnahme as? Aufnahmezustand.Laeuft
                            ?: return@update zustand
                        zustand.copy(aufnahme = laufend.copy(teiltext = ereignis.text))
                    }

                    is Erkennungsereignis.Ergebnis -> {
                        etwasVerstanden = true
                        // Beim Dauerdiktat bleibt der Bildschirm auf "Laeuft":
                        // zwischen zwei Saetzen auf "Wandelt" zu springen und
                        // sofort zurueck laesst die Anzeige flackern, obwohl
                        // ohne Unterbrechung weiter aufgenommen wird.
                        if (!dauerdiktat) {
                            uhrAuftrag?.cancel()
                            _zustand.update { it.copy(aufnahme = Aufnahmezustand.Wandelt) }
                        }
                        // Schlaegt das Sichern fehl, darf das Diktat nicht
                        // stillschweigend verschwinden.
                        val gesichert = runCatching {
                            sichereErgebnis(ereignis.text, code, sammelId)
                        }.getOrNull()
                        if (gesichert == null) {
                            weiter = false
                            _zustand.update {
                                it.copy(
                                    aufnahme = Aufnahmezustand.Fehler(Fehlerart.UNBEKANNT),
                                    erneuteErkennungFuer = null
                                )
                            }
                            return@collect
                        }
                        // Weitere Saetze wachsen an denselben Eintrag an.
                        sammelId = gesichert.id
                        _zustand.update { zustand ->
                            zustand.copy(
                                aufnahme = if (dauerdiktat) {
                                    // Der fertige Satz rueckt in den festen
                                    // Text; der naechste Satz beginnt leer.
                                    // Pegel und Kurve fangen von vorn an,
                                    // Dauer und Text laufen weiter.
                                    val laufend = zustand.aufnahme as? Aufnahmezustand.Laeuft
                                    Aufnahmezustand.Laeuft(
                                        pegel = 0f,
                                        dauerSekunden = laufend?.dauerSekunden ?: 0,
                                        verlauf = emptyList(),
                                        stilleErkannt = false,
                                        teiltext = "",
                                        festerText = gesichert.text
                                    )
                                } else {
                                    Aufnahmezustand.Bereit
                                },
                                erneuteErkennungFuer = null,
                                letztesDiktatId = gesichert.id
                            )
                        }
                    }

                    is Erkennungsereignis.Fehlgeschlagen -> {
                        if (dauerdiktat && ereignis.art == Fehlerart.NICHTS_VERSTANDEN) {
                            // Beim Dauerdiktat ist das nur eine Sprechpause.
                            leereDurchgaenge += 1
                            return@collect
                        }
                        uhrAuftrag?.cancel()
                        weiter = false
                        if (ereignis.art == Fehlerart.SPRACHE_NICHT_AUF_GERAET) {
                            // Android das fehlende Paket holen lassen; beim
                            // naechsten Versuch liegt es auf dem Geraet.
                            (erkenner as? Spracherkenner)?.ladeSprachmodell(code)
                            ladeSprachen()
                            zeigeMeldung(Meldung.SPRACHE_WIRD_GELADEN)
                        }
                        _zustand.update { it.copy(aufnahme = Aufnahmezustand.Fehler(ereignis.art)) }
                    }
                }
                }

                if (etwasVerstanden) leereDurchgaenge = 0
                // Der Nutzer hat beendet, oder es kam mehrfach nichts mehr.
                val beendet = _zustand.value.aufnahme !is Aufnahmezustand.Laeuft
                if (!dauerdiktat || beendet || leereDurchgaenge >= STILLE_DURCHGAENGE) {
                    weiter = false
                }
            }
            uhrAuftrag?.cancel()
            if (_zustand.value.aufnahme is Aufnahmezustand.Laeuft ||
                _zustand.value.aufnahme is Aufnahmezustand.Wandelt
            ) {
                _zustand.update { it.copy(aufnahme = Aufnahmezustand.Bereit) }
            }
        }
    }

    /**
     * Was nach dem Sichern feststeht: der Eintrag und sein vollstaendiger
     * Text. Das Dauerdiktat zeigt diesen Text weiter an, waehrend es schon
     * den naechsten Satz hoert.
     */
    private data class Gesichert(val id: String, val text: String)

    /** Sichert das Ergebnis und liefert Kennung und Gesamttext des Eintrags. */
    private suspend fun sichereErgebnis(
        roherText: String,
        sprachCode: String,
        ersetzeDiktatId: String?
    ): Gesichert {
        val bausteine = textbausteinDao.alleEinmalig()
            .map { Textbaustein(it.id, it.kuerzel, it.ersatz) }
        // Erst gesprochene Satzzeichen, dann die eigenen Ersetzungen.
        val gesprochen = setzeSatzzeichen(roherText.trim(), sprachCode)
        val text = wendeBausteineAn(gesprochen, bausteine)
        val dauer = ((System.currentTimeMillis() - startMillis) / 1000).toInt()
        if (ersetzeDiktatId != null) {
            val bisher = _zustand.value.diktate.firstOrNull { it.id == ersetzeDiktatId }
            val zusammen = when {
                // Erneute Erkennung ersetzt; ein weiterer Satz im Dauerdiktat
                // haengt sich an.
                erneutErkannt == ersetzeDiktatId || bisher == null -> text
                bisher.text.isBlank() -> text
                else -> bisher.text.trimEnd() + " " + text
            }
            diktatDao.aktualisiere(ersetzeDiktatId, zusammen, sprachCode)
            return Gesichert(ersetzeDiktatId, zusammen)
        }
        val kennung = UUID.randomUUID().toString()
        diktatDao.sichere(
            DiktatEintrag(
                id = kennung,
                text = text,
                zeitpunktMillis = System.currentTimeMillis(),
                sprachCode = sprachCode,
                dauerSekunden = dauer
            )
        )
        return Gesichert(kennung, text)
    }

    override fun onCleared() {
        aufnahmeAuftrag?.cancel()
        uhrAuftrag?.cancel()
        super.onCleared()
    }

    // --------------------------------------------------------------- Verlauf

    fun setzeSuchbegriff(begriff: String) {
        _zustand.update { it.copy(suchbegriff = begriff) }
    }

    /**
     * Loescht und meldet erst danach -- die Oberflaeche wartet darauf.
     *
     * Der Eintrag wird vorher beiseitegelegt, damit [holeGeloeschtesZurueck]
     * ihn zurueckholen kann (Roadmap, Lauf 4.1). Ein Diktat ist gesprochene
     * Arbeit; ein Fehlgriff darf sie nicht endgueltig vernichten.
     */
    fun loescheDiktat(id: String, danach: () -> Unit = {}) {
        viewModelScope.launch {
            zuletztGeloescht = diktatDao.nachId(id)
            diktatDao.loescheNachId(id)
            _zustand.update { it.copy(kannZurueckholen = zuletztGeloescht != null) }
            zeigeMeldung(Meldung.DIKTAT_GELOESCHT)
            danach()
        }
    }

    /**
     * Holt das zuletzt geloeschte Diktat zurueck. Danach ist nichts mehr
     * zurueckzuholen -- es gibt genau einen Schritt, keinen Stapel.
     */
    fun holeGeloeschtesZurueck() {
        val eintrag = zuletztGeloescht ?: return
        zuletztGeloescht = null
        viewModelScope.launch {
            diktatDao.sichere(eintrag)
            _zustand.update { it.copy(kannZurueckholen = false) }
            zeigeMeldung(Meldung.DIKTAT_ZURUECKGEHOLT)
        }
    }

    // ---------------------------------------------------------- Textbausteine

    fun sichereBaustein(baustein: Textbaustein) {
        viewModelScope.launch {
            textbausteinDao.sichere(
                TextbausteinEintrag(
                    id = baustein.id.ifBlank { UUID.randomUUID().toString() },
                    kuerzel = baustein.kuerzel.trim(),
                    ersatz = baustein.ersatz.trim()
                )
            )
            zeigeMeldung(Meldung.BAUSTEIN_GESICHERT)
        }
    }

    fun loescheBaustein(baustein: Textbaustein) {
        viewModelScope.launch {
            textbausteinDao.loescheNachId(baustein.id)
            zeigeMeldung(Meldung.BAUSTEIN_GELOESCHT)
        }
    }

    // ---------------------------------------------------------- Einstellungen

    fun setzeStoppBeiStille(an: Boolean) {
        // Erst den sichtbaren Zustand, dann die Ablage -- der Schalter darf
        // nicht auf das Schreiben warten.
        _zustand.update { it.copy(stoppBeiStille = an) }
        viewModelScope.launch { ablage.setzeStoppBeiStille(an) }
    }

    fun setzeAufnahmenBehalten(an: Boolean) {
        _zustand.update { it.copy(aufnahmenBehalten = an) }
        viewModelScope.launch { ablage.setzeAufnahmenBehalten(an) }
    }

    /** Waehlt die Sprache fuer neue Diktate. */
    fun waehleSprache(sprache: Diktatsprache) {
        _zustand.update { it.copy(gewaehlterSprachCode = sprache.code) }
        viewModelScope.launch { ablage.setzeDiktatSprache(sprache.code) }
    }

    /** Waehlt die Sprache eines einzelnen Eintrags, ohne die Voreinstellung
     *  fuer neue Diktate zu veraendern. */
    fun setzeSpracheDesDiktats(diktatId: String, sprache: Diktatsprache) {
        viewModelScope.launch {
            val diktat = _zustand.value.diktate.firstOrNull { it.id == diktatId } ?: return@launch
            diktatDao.aktualisiere(diktatId, diktat.text, sprache.code)
        }
    }

    fun ladeSprachen() {
        viewModelScope.launch {
            _zustand.update { it.copy(sprachenLaden = true) }
            val sprachen = sprachverzeichnis.verfuegbareSprachen(anzeigeSprache())
            _zustand.update { it.copy(sprachen = sprachen, sprachenLaden = false) }
        }
    }

    // ------------------------------------------------------------- Meldungen

    fun zeigeMeldung(meldung: Meldung) {
        _zustand.update { it.copy(meldung = meldung) }
    }

    fun meldungGezeigt() {
        _zustand.update { it.copy(meldung = null) }
    }

    // ------------------------------------------------------------- Abbildung

    /**
     * Baut die Anzeige der Einstellungen aus dem uebergebenen Zustand --
     * bewusst mit Parameter, damit die Oberflaeche den Zustand liest und
     * bei jeder Aenderung neu zeichnet.
     */
    fun einstellungenFuer(zustand: NibraZustand): Einstellungen = Einstellungen(
        stoppBeiStille = zustand.stoppBeiStille,
        aufnahmenBehalten = zustand.aufnahmenBehalten,
        dienstzustand = zustand.dienstzustand,
        mikrofonzustand = zustand.mikrofonzustand,
        oberflaechenspracheName = anzeigeSprache()
            .getDisplayName(anzeigeSprache())
            .replaceFirstChar { it.uppercase(anzeigeSprache()) },
        diktatspracheName = sprachName(zustand.gewaehlterSprachCode)
    )

    /**
     * Nur Sprache und Land -- Erweiterungen wie "-u-fw-mon", die manche
     * Geraete in ihrer Systemsprache tragen, lehnt die Erkennung ab.
     */
    private fun schlichterSprachcode(locale: Locale): String =
        if (locale.country.isNotBlank()) "${locale.language}-${locale.country}"
        else locale.language

    private fun anzeigeSprache(): Locale =
        context.resources.configuration.locales[0] ?: Locale.getDefault()

    private fun sprachName(code: String): String {
        if (code.isBlank()) return ""
        val anzeige = anzeigeSprache()
        return Locale.forLanguageTag(code.replace('_', '-'))
            .getDisplayName(anzeige)
            .ifBlank { code }
            .replaceFirstChar { it.uppercase(anzeige) }
    }

    private fun zuDiktat(eintrag: DiktatEintrag): Diktat {
        val anzeige = anzeigeSprache()
        val zeitpunkt = Instant.ofEpochMilli(eintrag.zeitpunktMillis)
            .atZone(ZoneId.systemDefault())
        return Diktat(
            id = eintrag.id,
            text = eintrag.text,
            zeitpunktMillis = eintrag.zeitpunktMillis,
            uhrzeit = zeitpunkt.format(
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(anzeige)
            ),
            datum = zeitpunkt.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(anzeige)
            ),
            sprachCode = eintrag.sprachCode,
            sprachName = sprachName(eintrag.sprachCode),
            dauerSekunden = eintrag.dauerSekunden
        )
    }

    private companion object {
        /** So viele Pegelwerte zeigt die Kurve. */
        const val KURVENPUNKTE = 64

        /** Laenger als zehn Minuten am Stueck nimmt Nibra nicht auf. */
        const val HOECHSTDAUER_SEKUNDEN = 600

        /** So oft darf beim Dauerdiktat nichts kommen, bevor es endet. */
        const val STILLE_DURCHGAENGE = 3
    }
}
