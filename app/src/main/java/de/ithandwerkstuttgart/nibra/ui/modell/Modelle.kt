package de.ithandwerkstuttgart.nibra.ui.modell

import androidx.compose.runtime.Immutable

/** Ein gespeichertes Diktat, wie es der Verlauf und das Detail anzeigen. */
@Immutable
data class Diktat(
    val id: String,
    val text: String,
    val zeitpunktMillis: Long,
    /** Uhrzeit, bereits für die Oberflächensprache formatiert. */
    val uhrzeit: String,
    /** Datum, bereits für die Oberflächensprache formatiert. */
    val datum: String,
    val sprachCode: String,
    /** Sprachname in der Oberflächensprache, z. B. "Deutsch". */
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

/** Eine wählbare Diktatsprache. */
@Immutable
data class Diktatsprache(
    val code: String,
    /** Name in der Oberflächensprache, z. B. "Französisch". */
    val name: String,
    /** Eigenname, z. B. "Francais". */
    val eigenName: String,
    val aufGeraetVerfuegbar: Boolean,
    val zuletztGenutzt: Boolean = false
)

/** Eine eigene Ersetzung: Kürzel im Diktat wird zum vollen Text. */
@Immutable
data class Textbaustein(
    val id: String,
    val kuerzel: String,
    val ersatz: String
)

/** Fehler, die der Nutzer im Klartext erklärt bekommt — nie als Code. */
enum class Fehlerart {
    KEIN_MIKROFON_RECHT,
    ERKENNUNG_NICHT_VERFUEGBAR,
    SPRACHE_NICHT_AUF_GERAET,

    /**
     * Es kam Sprache an, aber der Erkenner konnte sie nicht zuordnen.
     * Das ist ein echtes Scheitern.
     */
    NICHTS_VERSTANDEN,

    /**
     * Die Umwandlung hat begonnen, aber der Erkenner hat sich nie wieder
     * gemeldet -- weder mit einem Ergebnis noch mit einem Fehler.
     *
     * Das ist kein erfundener Zustand für einen theoretischen Fall: genau
     * das ist auf dem Gerät passiert. Die Oberfläche stand dauerhaft auf
     * „Wird in Text gewandelt", und weil aus diesem Zustand kein neues
     * Diktat startbar ist, war die App aus Sicht des Nutzers festgefahren.
     */
    KEIN_ERGEBNIS,

    /**
     * Es kam überhaupt keine Sprache -- der Erkenner hat auf Stille
     * gewartet und aufgegeben.
     *
     * Früher fiel das mit [NICHTS_VERSTANDEN] zusammen. Damit war eine
     * Denkpause von einem echten Fehler nicht zu unterscheiden, und im
     * Dauerdiktat konnte gesprochener Text still verschwinden.
     */
    NICHTS_GEHOERT,

    UNBEKANNT
}

/** Zustand der Aufnahme, gemeinsam für Hauptbildschirm und Aufnahme-Blatt. */
@Immutable
sealed interface Aufnahmezustand {
    data object Bereit : Aufnahmezustand

    data class Laeuft(
        /** Aktueller Pegel, 0f bis 1f, bereits geglättet. */
        val pegel: Float,
        val dauerSekunden: Int,
        /** Jüngste Pegelwerte, ältester zuerst — Grundlage der Kurve. */
        val verlauf: List<Float>,
        /** Wahr, wenn gerade eine Sprechpause läuft und der Stopp naht. */
        val stilleErkannt: Boolean = false,
        /** Was Nibra am laufenden Satz bisher versteht. Kann sich noch ändern. */
        val teiltext: String = "",
        /**
         * Beim Dauerdiktat: die Sätze, die schon feststehen und gesichert
         * sind. Ohne dieses Feld wäre der Bildschirm nach jedem Satz wieder
         * leer, obwohl weiter aufgenommen wird.
         */
        val festerText: String = ""
    ) : Aufnahmezustand {
        /** Was der Bildschirm anzeigt: Feststehendes und laufender Satz. */
        val sichtbarerText: String
            get() = listOf(festerText, teiltext)
                .filter { it.isNotBlank() }
                .joinToString(" ")
    }

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
    val dienstzustand: Dienstzustand = Dienstzustand.NICHT_EINGERICHTET,
    val mikrofonzustand: Mikrofonzustand = Mikrofonzustand.NICHT_ERTEILT,
    /** Name der aktuellen Oberflächensprache in ihrer eigenen Sprache. */
    val oberflaechenspracheName: String,
    /** Name der aktuellen Diktatsprache. */
    val diktatspracheName: String
)
