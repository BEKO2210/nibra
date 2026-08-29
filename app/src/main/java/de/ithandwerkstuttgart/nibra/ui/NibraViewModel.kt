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
import de.ithandwerkstuttgart.nibra.erkennung.Erkennungsprotokoll
import de.ithandwerkstuttgart.nibra.erkennung.Ladestand
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

/** Kurze Rückmeldung an den Nutzer, im Bildschirm als Einblendung. */
enum class Meldung {
    KOPIERT,
    EINGEFUEGT,
    NICHT_EINGEFUEGT,
    DIKTAT_GELOESCHT,
    DIKTAT_ZURUECKGEHOLT,
    DIKTAT_GESICHERT,
    BAUSTEIN_GESICHERT,
    BAUSTEIN_GELOESCHT,
    SPRACHE_WIRD_GELADEN
}

/** Alles, was die Oberfläche von Nibra zu einem Zeitpunkt anzeigt. */
data class NibraZustand(
    val geladen: Boolean = false,
    val eingerichtet: Boolean = false,
    val aufnahme: Aufnahmezustand = Aufnahmezustand.Bereit,
    val diktate: List<Diktat> = emptyList(),
    val suchbegriff: String = "",
    val textbausteine: List<Textbaustein> = emptyList(),
    val sprachen: List<Diktatsprache> = emptyList(),
    /** Wahr, solange das Gerät nach seinen Sprachen gefragt wird. */
    val sprachenLaden: Boolean = false,

    /** Laufende Paketladungen, nach Sprachcode. */
    val paketladungen: Map<String, Ladestand> = emptyMap(),
    /** Wahr, sobald der Verlauf einmal aus der Ablage kam. */
    val verlaufGeladen: Boolean = false,
    val gewaehlterSprachCode: String = "",
    val stoppBeiStille: Boolean = true,
    val mikrofonzustand: Mikrofonzustand = Mikrofonzustand.NICHT_ERTEILT,
    val dienstzustand: Dienstzustand = Dienstzustand.NICHT_EINGERICHTET,
    /** Kennung des zuletzt fertig erkannten Diktats -- es bleibt auf der
     *  Aufnahmefläche stehen, bis das nächste beginnt. */
    val letztesDiktatId: String? = null,
    val meldung: Meldung? = null,
    /** Wahr, solange das zuletzt gelöschte Diktat zurückgeholt werden kann. */
    val kannZurueckholen: Boolean = false
) {
    val gruppen: List<VerlaufGruppe>
        get() = ordneVerlauf(suche(diktate, suchbegriff), System.currentTimeMillis())

    /**
     * Sprachliste mit Markierung "zuletzt genutzt": die aktuelle Wahl und
     * die Sprachen der jüngsten Diktate stehen oben.
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

    /** Während "Wandelt" darf die Aufnahmefläche nichts Neues starten. */
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

    /** Wacht darüber, dass „Wandelt" nicht ewig stehen bleibt. */
    private var wandlungsWache: Job? = null
    private var startMillis: Long = 0L

    /** Das zuletzt gelöschte Diktat, solange es zurückgeholt werden kann. */
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
     * Ein Tipp auf die Aufnahmefläche. Starten geht nur aus [Aufnahmezustand.Bereit]
     * oder aus einem Fehler, Beenden nur aus [Aufnahmezustand.Laeuft]. Während
     * der Umwandlung passiert nichts -- sonst ginge das Ergebnis verloren.
     */
    fun aufnahmeUmschalten() {
        when (_zustand.value.aufnahme) {
            is Aufnahmezustand.Laeuft -> {
                erkenner.stoppen()
                uhrAuftrag?.cancel()
                beginneUmwandlung()
            }

            is Aufnahmezustand.Wandelt -> Unit

            is Aufnahmezustand.Bereit,
            is Aufnahmezustand.Fehler -> starteAufnahme()
        }
    }

    /**
     * Setzt den Zustand „Wandelt" -- und stellt im selben Atemzug sicher,
     * dass er wieder verlassen wird.
     *
     * **Das ist die einzige Stelle, an der dieser Zustand entsteht.** Der
     * Grund steht in [Fehlerart.KEIN_ERGEBNIS]: schweigt der Erkenner nach
     * `stopListening` einfach, kam vorher nie wieder ein Ereignis. Die
     * Oberfläche stand dann dauerhaft auf „Wird in Text gewandelt", und aus
     * diesem Zustand lässt sich kein neues Diktat starten -- die App war
     * festgefahren.
     *
     * Die Wache ist **nicht** die Behebung der Ursache, sondern das Netz
     * darunter. Sie sorgt dafür, dass ein solcher Zustand nicht mehr
     * unbegrenzt bestehen kann, gleich welcher Erkenner darunter liegt.
     */
    private fun beginneUmwandlung() {
        Erkennungsprotokoll.zustand("WANDELT")
        _zustand.update { it.copy(aufnahme = Aufnahmezustand.Wandelt) }
        wandlungsWache?.cancel()
        wandlungsWache = viewModelScope.launch {
            delay(UMWANDLUNG_GRENZE_MILLIS)
            // Inzwischen weitergezogen? Dann hat alles funktioniert.
            if (_zustand.value.aufnahme !is Aufnahmezustand.Wandelt) return@launch
            Erkennungsprotokoll.zustand("WACHE des Modells greift -> FEHLER KEIN_ERGEBNIS")
            aufnahmeAuftrag?.cancel()
            uhrAuftrag?.cancel()
            erkenner.gibFrei()
            _zustand.update {
                it.copy(aufnahme = Aufnahmezustand.Fehler(Fehlerart.KEIN_ERGEBNIS))
            }
        }
    }

    /**
     * Holt das Sprachpaket auf das Gerät und meldet den Fortschritt.
     *
     * Vorher konnte Nibra nur sagen, dass ein Paket fehlt -- laden musste
     * der Nutzer es sich selbst in den Systemeinstellungen, ohne dass
     * irgendwo stand, wo. Jetzt genügt ein Tippen.
     */
    fun ladeSprachpaket(code: String) {
        if (_zustand.value.paketladungen[code] is Ladestand.Laeuft) return
        viewModelScope.launch {
            erkenner.ladeSprachpaket(code).collect { stand ->
                _zustand.update { it.copy(paketladungen = it.paketladungen + (code to stand)) }
            }
            // Nach einer erfolgreichen Ladung stimmt die Sprachliste nicht
            // mehr -- das Paket liegt jetzt auf dem Gerät.
            if (_zustand.value.paketladungen[code] is Ladestand.Fertig) ladeSprachen()
        }
    }

    /** Nach einem Fehler: derselbe Versuch noch einmal, sofort. */
    fun erneutVersuchen() {
        starteAufnahme()
    }

    fun fehlerZuruecksetzen() {
        _zustand.update { it.copy(aufnahme = Aufnahmezustand.Bereit) }
    }

    private fun starteAufnahme(sprachCode: String? = null) {
        Erkennungsprotokoll.beginne("Diktat im Hauptbildschirm")
        aufnahmeAuftrag?.cancel()
        uhrAuftrag?.cancel()
        startMillis = System.currentTimeMillis()
        val code = sprachCode?.takeIf { it.isNotBlank() } ?: _zustand.value.gewaehlterSprachCode
        val stoppBeiStille = _zustand.value.stoppBeiStille

        // **Vorher prüfen, statt ins Leere sprechen zu lassen.**
        //
        // Bis hierher hat Nibra das Diktat begonnen, sechs Sekunden lang
        // aufgenommen und erst hinterher gemerkt, dass das Sprachpaket
        // fehlt -- weil die Erkenntnis an einer gemeldeten Störung hing.
        // Auf dem Pixel 9 kam nicht einmal die: dort ist nur en-US auf dem
        // Gerät, Nibra fragt de-DE ohne Netz, und der Dienst schweigt
        // schlicht. Der Nutzer sah eine App, die zuhört und nichts
        // versteht.
        //
        // Ist bekannt, dass die Sprache nicht auf dem Gerät liegt, wird
        // das Diktat gar nicht erst begonnen: klare Auskunft, Paket wird
        // angestoßen, der Nutzer verliert kein Wort.
        val gewaehlteSprache = _zustand.value.sprachen.firstOrNull { it.code == code }
        if (gewaehlteSprache != null &&
            gewaehlteSprache.verfuegbarkeitBekannt &&
            !gewaehlteSprache.aufGeraetVerfuegbar
        ) {
            Erkennungsprotokoll.zustand("Sprachpaket fehlt vor dem Start: $code")
            ladeSprachpaket(code)
            ladeSprachen()
            zeigeMeldung(Meldung.SPRACHE_WIRD_GELADEN)
            _zustand.update {
                it.copy(aufnahme = Aufnahmezustand.Fehler(Fehlerart.SPRACHE_NICHT_AUF_GERAET))
            }
            return
        }

        _zustand.update {
            it.copy(
                aufnahme = Aufnahmezustand.Laeuft(
                    pegel = 0f,
                    dauerSekunden = 0,
                    verlauf = emptyList()
                ),
                letztesDiktatId = null
            )
        }

        uhrAuftrag = viewModelScope.launch {
            // Die Uhr läuft höchstens bis zur Obergrenze; danach beendet
            // Nibra die Aufnahme selbst, statt ewig weiterzuzählen.
            var sekunden = 0
            while (sekunden < HOECHSTDAUER_SEKUNDEN) {
                delay(1_000)
                val laufend = _zustand.value.aufnahme as? Aufnahmezustand.Laeuft ?: return@launch
                // Jeder Takt zählt mindestens eine Sekunde weiter. Die Uhr des
                // Geräts darf den Zähler nach vorn holen -- etwa wenn Nibra im
                // Hintergrund war und Takte ausgefallen sind -- aber nie
                // zurückhalten. Ohne diese Untergrenze dreht die Schleife
                // endlos, sobald `delay` schneller läuft als die Uhr.
                sekunden = maxOf(
                    sekunden + 1,
                    ((System.currentTimeMillis() - startMillis) / 1000).toInt()
                )
                _zustand.update { it.copy(aufnahme = laufend.copy(dauerSekunden = sekunden)) }
            }
            if (_zustand.value.aufnahme is Aufnahmezustand.Laeuft) {
                erkenner.stoppen()
                beginneUmwandlung()
            }
        }

        aufnahmeAuftrag = viewModelScope.launch {
            // Ohne "Stopp bei Stille" hört Nibra nach jedem Satz weiter zu,
            // bis der Nutzer beendet -- sonst wäre nach einem Satz Schluss.
            val dauerdiktat = !stoppBeiStille
            // Beim Dauerdiktat wachsen weitere Sätze an denselben Eintrag.
            var sammelId: String? = null
            var leereDurchgaenge = 0
            var weiter = true
            // Über das **ganze** Diktat, nicht je Abschnitt. `etwasVerstanden`
            // wird je Abschnitt zurückgesetzt -- deshalb wusste der letzte,
            // leere Abschnitt nichts von den gelungenen Sätzen davor und
            // meldete "Das hat nicht geklappt" nach einem vollen Diktat.
            var jemalsVerstanden = false

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
                        jemalsVerstanden = true
                        // Beim Dauerdiktat bleibt der Bildschirm auf "Läuft":
                        // zwischen zwei Sätzen auf "Wandelt" zu springen und
                        // sofort zurück lässt die Anzeige flackern, obwohl
                        // ohne Unterbrechung weiter aufgenommen wird.
                        if (!dauerdiktat) {
                            uhrAuftrag?.cancel()
                            beginneUmwandlung()
                        }
                        // Schlägt das Sichern fehl, darf das Diktat nicht
                        // stillschweigend verschwinden.
                        val gesichert = runCatching {
                            sichereErgebnis(ereignis.text, code, sammelId)
                        }.getOrNull()
                        if (gesichert == null) {
                            weiter = false
                            _zustand.update {
                                it.copy(
                                    aufnahme = Aufnahmezustand.Fehler(Fehlerart.UNBEKANNT)
                                )
                            }
                            return@collect
                        }
                        // Weitere Sätze wachsen an denselben Eintrag an.
                        sammelId = gesichert.id
                        _zustand.update { zustand ->
                            zustand.copy(
                                aufnahme = if (dauerdiktat) {
                                    // Der fertige Satz rückt in den festen
                                    // Text; der nächste Satz beginnt leer.
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
                                letztesDiktatId = gesichert.id
                            )
                        }
                    }

                    is Erkennungsereignis.Fehlgeschlagen -> {
                        Erkennungsprotokoll.zustand("Fehler gemeldet: ${ereignis.art}")
                        // Nur ausbleibende Sprache ist eine Pause. "Gehört, aber
                        // nicht verstanden" ist ein Fehler und muss auch so
                        // gemeldet werden -- sonst verschwindet Gesprochenes still.
                        if (dauerdiktat && ereignis.art == Fehlerart.NICHTS_GEHOERT) {
                            // Beim Dauerdiktat ist das nur eine Sprechpause.
                            leereDurchgaenge += 1
                            return@collect
                        }

                        // Der Nutzer hat beendet (Zustand „wandelt"), und der
                        // **letzte Abschnitt** war leer -- er hatte längst
                        // aufgehört zu sprechen, als er auf Stopp tippte.
                        // Wenn vorher Sätze angekommen sind, ist das ein
                        // normales Ende und kein Fehlschlag. Auf dem Gerät
                        // stand sonst nach einem gelungenen Diktat: „Das hat
                        // nicht geklappt" -- eine Ohrfeige zum Abschied.
                        val nutzerHatBeendet =
                            _zustand.value.aufnahme is Aufnahmezustand.Wandelt
                        val leererSchluss = ereignis.art == Fehlerart.NICHTS_VERSTANDEN ||
                            ereignis.art == Fehlerart.NICHTS_GEHOERT
                        if (nutzerHatBeendet && leererSchluss && jemalsVerstanden) {
                            Erkennungsprotokoll.zustand("leerer Schluss nach Stopp -> FERTIG")
                            uhrAuftrag?.cancel()
                            weiter = false
                            _zustand.update { it.copy(aufnahme = Aufnahmezustand.Bereit) }
                            return@collect
                        }
                        uhrAuftrag?.cancel()
                        weiter = false

                        // „Nichts verstanden" ist die falsche Auskunft, wenn
                        // das Sprachpaket schlicht fehlt. Nibra bittet den
                        // Erkenner ausdrücklich, ohne Netz zu arbeiten -- ohne
                        // Paket **kann** er dann nichts erkennen und meldet
                        // NO_MATCH, als hätte der Nutzer genuschelt.
                        //
                        // Auf dem Emulator nachgestellt: Sprache kommt an
                        // (onBeginningOfSpeech), Erkenner meldet trotzdem
                        // Code 7. Kein Paket installiert.
                        val gewaehlte = _zustand.value.sprachen.firstOrNull { it.code == code }
                        val paketFehlt = ereignis.art == Fehlerart.NICHTS_VERSTANDEN &&
                            gewaehlte != null &&
                            gewaehlte.verfuegbarkeitBekannt &&
                            !gewaehlte.aufGeraetVerfuegbar
                        val art =
                            if (paketFehlt) Fehlerart.SPRACHE_NICHT_AUF_GERAET else ereignis.art

                        if (art == Fehlerart.SPRACHE_NICHT_AUF_GERAET) {
                            // Android das fehlende Paket holen lassen; beim
                            // nächsten Versuch liegt es auf dem Gerät. Über
                            // denselben Weg wie der Knopf in der Sprachliste,
                            // damit der Fortschritt auch hier sichtbar wird.
                            ladeSprachpaket(code)
                            ladeSprachen()
                            zeigeMeldung(Meldung.SPRACHE_WIRD_GELADEN)
                        }
                        _zustand.update { it.copy(aufnahme = Aufnahmezustand.Fehler(art)) }
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
            // Das Diktat ist zu Ende -- erst jetzt die Bindung an den
            // Systemdienst lösen. Zwischen zwei Sätzen bleibt sie stehen.
            erkenner.gibFrei()
            if (_zustand.value.aufnahme is Aufnahmezustand.Laeuft ||
                _zustand.value.aufnahme is Aufnahmezustand.Wandelt
            ) {
                _zustand.update { it.copy(aufnahme = Aufnahmezustand.Bereit) }
            }
        }
    }

    /**
     * Was nach dem Sichern feststeht: der Eintrag und sein vollständiger
     * Text. Das Dauerdiktat zeigt diesen Text weiter an, während es schon
     * den nächsten Satz hört.
     */
    private data class Gesichert(val id: String, val text: String)

    /** Sichert das Ergebnis und liefert Kennung und Gesamttext des Eintrags. */
    private suspend fun sichereErgebnis(
        roherText: String,
        sprachCode: String,
        /** Beim Dauerdiktat der Eintrag, an den weitere Sätze anwachsen. */
        sammelId: String?
    ): Gesichert {
        val bausteine = textbausteinDao.alleEinmalig()
            .map { Textbaustein(it.id, it.kuerzel, it.ersatz) }
        // Erst gesprochene Satzzeichen, dann die eigenen Ersetzungen.
        val gesprochen = setzeSatzzeichen(roherText.trim(), sprachCode)
        val text = wendeBausteineAn(gesprochen, bausteine)
        val dauer = ((System.currentTimeMillis() - startMillis) / 1000).toInt()
        if (sammelId != null) {
            // Der nächste Satz des Dauerdiktats hängt sich an den bisherigen.
            val bisher = _zustand.value.diktate.firstOrNull { it.id == sammelId }
            val zusammen = if (bisher == null || bisher.text.isBlank()) {
                text
            } else {
                bisher.text.trimEnd() + " " + text
            }
            diktatDao.aktualisiere(sammelId, zusammen, sprachCode)
            return Gesichert(sammelId, zusammen)
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
     * Löscht und meldet erst danach -- die Oberfläche wartet darauf.
     *
     * Der Eintrag wird vorher beiseitegelegt, damit [holeGeloeschtesZurueck]
     * ihn zurückholen kann (Roadmap, Lauf 4.1). Ein Diktat ist gesprochene
     * Arbeit; ein Fehlgriff darf sie nicht endgültig vernichten.
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
     * Holt das zuletzt gelöschte Diktat zurück. Danach ist nichts mehr
     * zurückzuholen -- es gibt genau einen Schritt, keinen Stapel.
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

    /**
     * Sichert den von Hand geänderten Text eines Diktats (Roadmap, Lauf 4.2).
     *
     * Ein Erkenner hört sich gelegentlich an einem Namen fest. Dafür das
     * ganze Diktat noch einmal zu sprechen, ist zu viel verlangt -- ein
     * Wort zu tippen genügt. Die Sprache des Eintrags bleibt, wie sie war.
     */
    fun sichereText(id: String, text: String) {
        val bereinigt = text.trim()
        val bisher = _zustand.value.diktate.firstOrNull { it.id == id } ?: return
        if (bereinigt.isEmpty() || bereinigt == bisher.text) return
        viewModelScope.launch {
            diktatDao.aktualisiere(id, bereinigt, bisher.sprachCode)
            zeigeMeldung(Meldung.DIKTAT_GESICHERT)
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


    /** Wählt die Sprache für neue Diktate. */
    fun waehleSprache(sprache: Diktatsprache) {
        _zustand.update { it.copy(gewaehlterSprachCode = sprache.code) }
        viewModelScope.launch { ablage.setzeDiktatSprache(sprache.code) }
    }

    /** Wählt die Sprache eines einzelnen Eintrags, ohne die Voreinstellung
     *  für neue Diktate zu verändern. */
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
     * Baut die Anzeige der Einstellungen aus dem übergebenen Zustand --
     * bewusst mit Parameter, damit die Oberfläche den Zustand liest und
     * bei jeder Änderung neu zeichnet.
     */
    fun einstellungenFuer(zustand: NibraZustand): Einstellungen = Einstellungen(
        stoppBeiStille = zustand.stoppBeiStille,
        dienstzustand = zustand.dienstzustand,
        mikrofonzustand = zustand.mikrofonzustand,
        oberflaechenspracheName = anzeigeSprache()
            .getDisplayName(anzeigeSprache())
            .replaceFirstChar { it.uppercase(anzeigeSprache()) },
        diktatspracheName = sprachName(zustand.gewaehlterSprachCode)
    )

    /**
     * Nur Sprache und Land -- Erweiterungen wie "-u-fw-mon", die manche
     * Geräte in ihrer Systemsprache tragen, lehnt die Erkennung ab.
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

        /** Länger als zehn Minuten am Stück nimmt Nibra nicht auf. */
        const val HOECHSTDAUER_SEKUNDEN = 600

        /** So oft darf beim Dauerdiktat nichts kommen, bevor es endet. */
        const val STILLE_DURCHGAENGE = 3

        /**
         * Wie lange „Wandelt" höchstens stehen darf, bevor Nibra aufgibt.
         *
         * **Nicht gemessen.** Ein Erkenner auf dem Gerät braucht nach
         * `stopListening` erfahrungsgemäß deutlich unter drei Sekunden;
         * zwanzig liegen sicher über der Wache im Erkenner (15 s) und
         * sind trotzdem weit von „unendlich" entfernt. Sobald die Messstrecke
         * wieder misst, gehört hier eine echte Verteilung hin statt einer
         * gutgemeinten Zahl.
         */
        const val UMWANDLUNG_GRENZE_MILLIS = 20_000L
    }
}
