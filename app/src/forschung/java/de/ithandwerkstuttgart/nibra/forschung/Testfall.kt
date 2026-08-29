package de.ithandwerkstuttgart.nibra.forschung

import java.security.MessageDigest

/**
 * Ein Prüfsatz -- **die einzige Quelle** für Anzeige und Auswertung.
 *
 * Vorher gab es zwei: der Bildschirm zeigte einen fest eingebauten Text
 * aus [Sprachlauf], die Auswertung rechnete gegen eine eigene Liste im
 * Versuch. Beide meinten „den Prüfsatz", und niemand merkte, dass sie
 * verschiedene meinten. Belkis hat zweiundsiebzig Mal korrekt vorgelesen,
 * was dastand -- gemessen wurde es gegen etwas anderes. Zwanzig Minuten
 * für nichts.
 *
 * Eine Kopie mehr ist hier nicht bequem, sondern gefährlich: sie läuft
 * auseinander, ohne dass ein Test anschlägt, und der Fehler zeigt sich
 * erst in Zahlen, die niemand erklären kann.
 */
data class Testfall(
    val id: String,
    val text: String,
    val kategorie: Kategorie
) {
    enum class Kategorie {
        EINFACH, LANGER_ANFANG, EIGENNAME, ZAHL, PAUSE, FACHBEGRIFF
    }

    /**
     * Fingerabdruck des Textes. Läuft die Anzeige irgendwann doch wieder
     * mit einer eigenen Kopie, unterscheidet sich der Abdruck -- und der
     * Lauf bricht ab, bevor jemand spricht.
     */
    val abdruck: String by lazy { abdruckVon(text) }

    companion object {

        /**
         * Vergleicht, was die Anzeige zeigt, mit dem, was bewertet wird.
         *
         * @return `null`, wenn beides übereinstimmt, sonst der Grund.
         *
         * Eigene Funktion, damit sie ohne Gerät zu prüfen ist. Ein Riegel,
         * von dem niemand weiß, ob er zuschlägt, ist kein Riegel.
         */
        fun abgleich(
            angezeigteKennung: String?,
            angezeigterText: String?,
            bewertet: Testfall
        ): String? = when {
            angezeigterText == null -> "Die Anzeige hat noch nichts gezeichnet."
            angezeigteKennung != bewertet.id ->
                "Angezeigt wird \"$angezeigteKennung\", bewertet würde \"${bewertet.id}\"."
            abdruckVon(angezeigterText) != bewertet.abdruck ->
                "Gleiche Kennung ${bewertet.id}, aber verschiedener Text auf dem " +
                    "Bildschirm: angezeigt ${abdruckVon(angezeigterText).take(16)}, " +
                    "bewertet ${bewertet.abdruck.take(16)}."
            else -> null
        }

        /** Fingerabdruck einer beliebigen Zeichenkette, gleiche Rechnung. */
        fun abdruckVon(text: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }


        /** Der Pilotlauf: vier Sätze, vierundzwanzig Diktate. */
        val PILOT = listOf(
            Testfall(
                "A-einfach",
                "Guten Morgen, hier spricht Belkis Aslani aus Freiberg am Neckar.",
                Kategorie.EINFACH
            ),
            Testfall(
                "B-langer-anfang",
                "Wenn die Unterlagen vollständig geprüft sind und niemand mehr " +
                    "widerspricht, geben wir die Bestellung am Montagmorgen frei.",
                Kategorie.LANGER_ANFANG
            ),
            Testfall(
                "C-eigenname",
                "Die Anlage von d und b audiotechnik hat Herr Weinreich bestellt, " +
                    "zusammen mit Nibra.",
                Kategorie.EIGENNAME
            ),
            Testfall(
                "D-zahl",
                "Der Termin ist am zwölften Oktober um vierzehn Uhr dreißig, " +
                    "Kosten achthundert Euro.",
                Kategorie.ZAHL
            )
        )

        /** Der vollständige Lauf, erst nach bestandenem Pilot. */
        val VOLL = listOf(
            Testfall("01-einfach", "Guten Morgen, ich fasse die Besprechung von " +
                "gestern kurz zusammen.", Kategorie.EINFACH),
            Testfall("02-schnell", "Sofort loslegen, wir haben wenig Zeit.",
                Kategorie.EINFACH),
            Testfall("03-kurze-pause", "Die Lieferung kommt am Freitag, nicht am " +
                "Donnerstag.", Kategorie.PAUSE),
            Testfall("04-denkpause", "Ich überlege kurz. Also. Wir verschieben den " +
                "Termin auf nächste Woche.", Kategorie.PAUSE),
            Testfall("05-eigenname", "Hier spricht Belkis Aslani aus Freiberg am " +
                "Neckar.", Kategorie.EIGENNAME),
            Testfall("06-eigenname", "Bitte richte Herrn Weinreich aus, dass ich " +
                "zurückrufe.", Kategorie.EIGENNAME),
            Testfall("07-firma", "Die Anlage stammt von d und b audiotechnik.",
                Kategorie.EIGENNAME),
            Testfall("08-zahl", "Wir brauchen zweihundertvierzig Bauteile für " +
                "achthundert Euro.", Kategorie.ZAHL),
            Testfall("09-uhrzeit", "Die Besprechung beginnt um vierzehn Uhr dreißig " +
                "im Konferenzraum drei.", Kategorie.ZAHL),
            Testfall("10-datum", "Der Termin ist am zwölften Oktober " +
                "zweitausendsechsundzwanzig.", Kategorie.ZAHL),
            Testfall("11-fachbegriff", "Mach bitte ein Backup, bevor das Deployment " +
                "startet.", Kategorie.FACHBEGRIFF),
            Testfall("12-langer-anfang", "Wenn die Unterlagen vollständig geprüft " +
                "sind und niemand mehr widerspricht, geben wir die Bestellung am " +
                "Montagmorgen frei.", Kategorie.LANGER_ANFANG)
        )
    }
}
