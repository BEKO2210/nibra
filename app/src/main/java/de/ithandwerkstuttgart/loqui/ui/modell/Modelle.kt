package de.ithandwerkstuttgart.loqui.ui.modell

import androidx.compose.runtime.Immutable

/** Ein gespeichertes Diktat, wie es der Verlauf und das Detail anzeigen. */
@Immutable
data class Diktat(
    val id: String,
    val text: String,
    val zeitpunktMillis: Long,
    /** Uhrzeit, bereits fuer die Oberflaechensprache formatiert. */
    val uhrzeit: String,
    /** Datum, bereits fuer die Oberflaechensprache formatiert. */
    val datum: String,
    val sprachCode: String,
    /** Sprachname in der Oberflaechensprache, z. B. "Deutsch". */
    val sprachName: String,
    val dauerSekunden: Int
)

/** Grobe Datumsgruppe im Verlauf. Der Titel kommt aus `strings.xml`. */
enum class Gruppenschluessel { HEUTE, GESTERN, DIESE_WOCHE, AELTER }

@Immutable
data class VerlaufGruppe(
    val schluessel: Gruppenschluessel,
    /** Nur bei [Gruppenschluessel.AELTER] gesetzt: das formatierte Datum. */
    val eigenesDatum: String? = null,
    val diktate: List<Diktat>
)

/** Eine waehlbare Diktatsprache. */
@Immutable
data class Diktatsprache(
    val code: String,
    /** Name in der Oberflaechensprache, z. B. "Franzoesisch". */
    val name: String,
    /** Eigenname, z. B. "Francais". */
    val eigenName: String,
    val aufGeraetVerfuegbar: Boolean,
    val zuletztGenutzt: Boolean = false
)

/** Eine eigene Ersetzung: Kuerzel im Diktat wird zum vollen Text. */
@Immutable
data class Textbaustein(
    val id: String,
    val kuerzel: String,
    val ersatz: String
)

/** Fehler, die der Nutzer im Klartext erklaert bekommt — nie als Code. */
enum class Fehlerart {
    KEIN_MIKROFON_RECHT,
    ERKENNUNG_NICHT_VERFUEGBAR,
    SPRACHE_NICHT_AUF_GERAET,
    NICHTS_VERSTANDEN,
    UNBEKANNT
}

/** Zustand der Aufnahme, gemeinsam fuer Hauptbildschirm und Aufnahme-Blatt. */
@Immutable
sealed interface Aufnahmezustand {
    data object Bereit : Aufnahmezustand

    data class Laeuft(
        /** Aktueller Pegel, 0f bis 1f, bereits geglaettet. */
        val pegel: Float,
        val dauerSekunden: Int,
        /** Juengste Pegelwerte, aeltester zuerst — Grundlage der Kurve. */
        val verlauf: List<Float>,
        /** Wahr, wenn gerade eine Sprechpause laeuft und der Stopp naht. */
        val stilleErkannt: Boolean = false
    ) : Aufnahmezustand

    data object Wandelt : Aufnahmezustand

    data class Fehler(val art: Fehlerart) : Aufnahmezustand
}

/** Zustand des Bedienungshilfen-Dienstes. */
enum class Dienstzustand { EINGERICHTET, NICHT_EINGERICHTET }

/** Zustand der Mikrofon-Berechtigung. */
enum class Mikrofonzustand { ERTEILT, NICHT_ERTEILT }

/** Einstellungen, wie sie der Einstellungen-Bildschirm anzeigt. */
@Immutable
data class Einstellungen(
    val stoppBeiStille: Boolean = true,
    val aufnahmenBehalten: Boolean = false,
    val dienstzustand: Dienstzustand = Dienstzustand.NICHT_EINGERICHTET,
    val mikrofonzustand: Mikrofonzustand = Mikrofonzustand.NICHT_ERTEILT,
    /** Name der aktuellen Oberflaechensprache in ihrer eigenen Sprache. */
    val oberflaechenspracheName: String,
    /** Name der aktuellen Diktatsprache. */
    val diktatspracheName: String
)
