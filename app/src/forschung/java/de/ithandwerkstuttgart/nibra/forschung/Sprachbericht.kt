package de.ithandwerkstuttgart.nibra.forschung

import android.os.Build

/**
 * Schreibt aus einem [Sprachlauf.Ergebnis] den Bericht, auf den sich eine
 * Entscheidung stützen lässt.
 *
 * Der Bericht nennt ausdrücklich, was **nicht** belegt ist. Ein Messbericht,
 * der nur seine Treffer aufzählt, ist eine Werbeschrift.
 */
object Sprachbericht {

    fun schreibe(ergebnis: Sprachlauf.Ergebnis): String = buildString {
        appendLine("KONTROLLIERTER SPRACHLAUF -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("Diktatsprache: ${ergebnis.sprachCode}")
        appendLine("Bezugstext (${Wortvergleich.zerlege(ergebnis.bezugstext).size} Wörter):")
        appendLine(ergebnis.bezugstext.chunked(72).joinToString("\n") { "  $it" })
        appendLine()

        appendLine("=".repeat(64))
        appendLine("LAUF 1 -- ERKENNER ALLEIN")
        appendLine("=".repeat(64))
        schreibeErkenner(ergebnis.erkennerAllein)

        appendLine("=".repeat(64))
        appendLine("LAUF 2 -- ERKENNER + EIGENE AUFNAHME")
        appendLine("=".repeat(64))
        schreibeErkenner(ergebnis.erkennerNebenlauf)

        appendLine("-".repeat(64))
        appendLine("EIGENE AUFNAHME IN LAUF 2")
        appendLine("-".repeat(64))
        schreibeAufnahme(ergebnis)

        appendLine("-".repeat(64))
        appendLine("QUALITAETSVERGLEICH GEGEN DEN BEZUGSTEXT")
        appendLine("-".repeat(64))
        schreibeVergleich(ergebnis)
    }

    private fun StringBuilder.schreibeErkenner(protokoll: Sprachlauf.Erkennerprotokoll) {
        appendLine("Abschnitte: ${protokoll.abschnitte.size}")
        appendLine()
        protokoll.abschnitte.forEach { abschnitt ->
            appendLine("  Abschnitt ${abschnitt.nummer}  (Start ${abschnitt.startMillis} ms)")
            appendLine("    bereit             ${millis(abschnitt.bereitMillis)}")
            appendLine("    Sprache beginnt    ${millis(abschnitt.spracheBeginnMillis)}")
            appendLine("    erster Teiltext    ${millis(abschnitt.ersterTeiltextMillis)}" +
                "   (nach ${millis(abschnitt.bisErstemTeiltext)} ab Abschnittsstart)")
            appendLine("    Sprache endet      ${millis(abschnitt.spracheEndeMillis)}")
            appendLine("    Ergebnis           ${millis(abschnitt.ergebnisMillis)}" +
                "   (nach ${millis(abschnitt.bisErgebnis)} ab Abschnittsstart)")
            appendLine("    Fehler             ${abschnitt.fehler?.toString() ?: "keiner"}")
            if (abschnitt.nurGerettet) {
                appendLine("    ACHTUNG: Endergebnis war leer -- Zwischenstand gerettet")
                appendLine("    geretteter Text    ${abschnitt.letzterZwischenstand}")
            }
            if (abschnitt.lesarten.isEmpty()) {
                appendLine("    Lesarten           keine")
            } else {
                abschnitt.lesarten.forEachIndexed { stelle, lesart ->
                    val sicher = lesart.konfidenz
                        ?.let { "%.3f".format(it) } ?: "nicht geliefert"
                    appendLine("    Lesart ${stelle + 1} [$sicher]  ${lesart.text}")
                }
            }
            appendLine()
        }

        val neustart = protokoll.neustartlücken()
        appendLine("  Neustartlücken (Ergebnis bis wieder aufnahmebereit --")
        appendLine("  in genau diesem Fenster gehen gesprochene Wörter verloren):")
        if (neustart.isEmpty()) {
            appendLine("    keine -- nur ein Abschnitt")
        } else {
            neustart.forEachIndexed { stelle, lücke ->
                appendLine("    nach Abschnitt ${stelle + 1}: $lücke ms")
            }
            appendLine("    Summe ${neustart.sum()} ms, größte ${neustart.max()} ms")
        }
        appendLine()

        val lücken = protokoll.lücken()
        appendLine("  Abstand Ergebnis bis nächster Sprachbeginn (enthält auch die")
        appendLine("  Zeit, in der schlicht nicht gesprochen wurde):")
        if (lücken.isEmpty()) {
            appendLine("    keine -- nur ein Abschnitt")
        } else {
            lücken.forEachIndexed { stelle, lücke ->
                appendLine("    nach Abschnitt ${stelle + 1}: $lücke ms")
            }
        }
        appendLine()
        appendLine("  Volltext:")
        appendLine(umbrich(protokoll.volltext.ifBlank { "(nichts erkannt)" }))
        appendLine()
        appendLine("  Ereignisse:")
        protokoll.ereignisse.forEach { appendLine("    $it") }
        appendLine()
    }

    private fun StringBuilder.schreibeAufnahme(ergebnis: Sprachlauf.Ergebnis) {
        val verlauf = ergebnis.verlauf
        appendLine("Quelle          ${ergebnis.quelle}")
        appendLine("Abtastrate      ${ergebnis.abtastrate} Hz, 1 Kanal, PCM 16 Bit")
        // Verlustprüfung: aus dem Zeitplan ergibt sich, wie viel Signal
        // ankommen muss. Fehlende Abtastwerte wären der Grund, den Nebenlauf
        // sofort zu verwerfen -- sie fänden sich sonst nirgends.
        val erwartet = (Sprachlauf.VORLAUF_MS + Sprachlauf.ERKENNER_VORLAUF_MS +
            Sprachlauf.SPRECHDAUER_MS + Sprachlauf.NACHLAUF_MS) * ergebnis.abtastrate / 1000
        val fehlend = erwartet - verlauf.rahmen
        appendLine("Abtastwerte     ${verlauf.rahmen}" +
            "  (= ${verlauf.rahmen * 1000 / ergebnis.abtastrate} ms Signal)")
        appendLine("erwartet        $erwartet" +
            "  -> Abweichung $fehlend (%.3f %%)".format(fehlend * 100.0 / erwartet))
        appendLine("                (Abweichung an den Rändern ist Anlauf- und")
        appendLine("                 Auslaufzeit, kein Verlust im laufenden Strom)")
        val verlust = verlauf.verlustMillis()
        appendLine("Verlust         " + when {
            verlust == null -> "nicht gemessen"
            verlust <= 20 -> "$verlust ms gegen die Uhr -- lückenlos"
            else -> "$verlust ms gegen die Uhr -- es fehlt Ton"
        })
        appendLine("                (unabhängige Uhr lief ${verlauf.uhrdauerMillis()} ms)")
        appendLine("Fehler          ${ergebnis.aufnahmefehler ?: "keiner"}")
        ergebnis.aktiveMikrofone.forEach { appendLine("aktiv           $it") }
        appendLine()

        appendLine("Zeitmarken:")
        verlauf.zeitmarken.forEach { appendLine("  %6d ms  %s".format(it.beiMillis, it.was)) }
        appendLine()

        val start = marke(verlauf, "Erkenner startet")
        val sprache = marke(verlauf, "Sprechen beginnt")
        val ende = marke(verlauf, "Erkenner beendet")
        val schluss = verlauf.millisJetzt()

        if (start != null && sprache != null && ende != null) {
            // Die vier Abschnitte, die die Frage nach dem Pegelsprung
            // beantworten. Zwei davon sind still und ohne Erkenner, einer ist
            // still **mit** Erkenner -- der entscheidet.
            val abschnitte = listOf(
                verlauf.abschnitt("A  still, Erkenner aus   ", 0, start),
                verlauf.abschnitt("B  still, Erkenner an    ", start, sprache),
                verlauf.abschnitt("C  Sprache, Erkenner an  ", sprache, ende),
                verlauf.abschnitt("D  still, Erkenner aus   ", ende, schluss)
            )
            appendLine("Abschnitte:")
            appendLine("  %-26s %7s %8s %9s %9s".format(
                "", "Spitze", "Effektiv", "Stille", "Übersteu."))
            abschnitte.forEach { a ->
                appendLine("  %-26s %7d %8.1f %8.1f%% %8.2f%%".format(
                    a.name, a.spitze, a.effektivwertMittel,
                    a.stilleAnteil * 100, a.uebersteuertAnteil * 100))
            }
            appendLine()
            appendLine(deuteSprung(abschnitte, verlauf, start, sprache))
            appendLine()
        }

        appendLine("Verlauf (ein Balken je 100 ms, Effektivwert):")
        appendLine(verlauf.alsBild())
        appendLine()
    }

    /**
     * Deutet den Pegelunterschied zwischen den stillen Abschnitten.
     *
     * Die Unterscheidung, auf die es ankommt: ein **einzelnes** lautes Fach
     * direkt nach dem Erkennerstart ist ein Startton. Ein durchgehend
     * angehobener Abschnitt B, der in D wieder fällt, ist eine umgeschaltete
     * Verstärkung. Bleibt B wie A, hat der Erkenner am Aufnahmepfad nichts
     * geändert und der frühere Sprung kam schlicht von der Stimme.
     */
    private fun deuteSprung(
        abschnitte: List<Pegelverlauf.Abschnitt>,
        verlauf: Pegelverlauf,
        start: Long,
        sprache: Long
    ): String {
        val a = abschnitte[0].effektivwertMittel
        val b = abschnitte[1].effektivwertMittel
        val d = abschnitte[3].effektivwertMittel
        val ruhe = maxOf(a, 1.0)
        val anstiegB = b / ruhe

        // Wie viele Fächer in B liegen überhaupt über dem Ruhepegel?
        val faecherB = verlauf.zeitfächer.filter { it.abMillis in start until sprache }
        val lauteFächer = faecherB.count { it.effektivwert > 2 * ruhe }

        return buildString {
            appendLine("Deutung des Pegelsprungs:")
            appendLine("  A (still, kein Erkenner)   Effektivwert %.1f".format(a))
            appendLine("  B (still, Erkenner läuft) Effektivwert %.1f  = %.2f-fach von A"
                .format(b, anstiegB))
            appendLine("  D (still, Erkenner aus)    Effektivwert %.1f".format(d))
            appendLine("  laute Fächer in B: $lauteFächer von ${faecherB.size}")
            appendLine()
            // A und D sind beide still und beide ohne Erkenner. Weichen sie
            // stark voneinander ab, war der Raum während des Laufs nicht
            // ruhig -- dann trägt der Vergleich A gegen B nichts, und das
            // muss dastehen, statt eine saubere Deutung vorzutäuschen.
            val schwankung = maxOf(a, d) / maxOf(minOf(a, d), 0.1)
            if (schwankung > 2.0) {
                appendLine("  ACHTUNG: A und D sind beide still und ohne Erkenner, " +
                    "unterscheiden sich")
                appendLine("  aber um das %.1f-fache. Der Raum war während des Laufs nicht ruhig."
                    .format(schwankung))
                appendLine("  Die folgende Deutung ist damit unsicher; Lauf im ruhigen Raum " +
                    "wiederholen.")
                appendLine()
            }
            append(
                when {
                    anstiegB < 1.5 ->
                        "  Kein Sprung, solange nur der Erkenner läuft. Der früher " +
                            "gemessene Anstieg kam nicht vom Erkenner, sondern von dem, " +
                            "was zu hören war. Der Aufnahmepfad wird nicht umgeschaltet."
                    lauteFächer <= 3 ->
                        "  Der Anstieg steckt in $lauteFächer Fach/Fächern direkt nach dem " +
                            "Start -- das Muster eines Starttons, nicht einer Umschaltung. " +
                            "Ein Vorlauf von etwa ${lauteFächer * 100} ms genügt, um ihn " +
                            "aus einer Messung herauszuhalten."
                    d < b / 1.5 ->
                        "  B ist durchgehend angehoben und fällt in D zurück: der " +
                            "Aufnahmepfad wird umgeschaltet, solange der Erkenner läuft. " +
                            "Eine eigene Aufnahme daneben misst dann nicht dasselbe Signal " +
                            "wie allein. Das muss in jede spätere Auswertung hinein."
                    else ->
                        "  B ist angehoben und bleibt es auch in D. Die Ursache liegt dann " +
                            "nicht beim Erkenner. Umgebung während des Laufs prüfen."
                }
            )
        }
    }

    private fun StringBuilder.schreibeVergleich(ergebnis: Sprachlauf.Ergebnis) {
        val allein = Wortvergleich.vergleiche(
            ergebnis.bezugstext, ergebnis.erkennerAllein.volltext
        )
        val neben = Wortvergleich.vergleiche(
            ergebnis.bezugstext, ergebnis.erkennerNebenlauf.volltext
        )

        appendLine("  %-22s %10s %10s".format("", "allein", "nebenläufig"))
        appendLine("  %-22s %10d %10d".format("Bezugsworte", allein.bezugsworte, neben.bezugsworte))
        appendLine("  %-22s %10d %10d".format("erkannte Worte", allein.erkannteWorte, neben.erkannteWorte))
        appendLine("  %-22s %10d %10d".format("wörtlich getroffen", allein.gleich, neben.gleich))
        appendLine("  %-22s %10d %10d".format("ersetzt", allein.ersetzt, neben.ersetzt))
        appendLine("  %-22s %10d %10d".format("ausgelassen", allein.fehlt, neben.fehlt))
        appendLine("  %-22s %10d %10d".format("zusätzlich", allein.zusätzlich, neben.zusätzlich))
        appendLine("  %-22s %9.1f%% %9.1f%%".format(
            "Wortfehlerrate", allein.fehlerrate * 100, neben.fehlerrate * 100))
        appendLine("  %-22s %9.1f%% %9.1f%%".format(
            "Trefferquote", allein.trefferquote * 100, neben.trefferquote * 100))
        appendLine()

        appendLine("  Unterschiede allein gegen Bezugstext:")
        appendLine(allein.unterschiede())
        appendLine()
        appendLine("  Unterschiede nebenläufig gegen Bezugstext:")
        appendLine(neben.unterschiede())
        appendLine()

        val gegeneinander = Wortvergleich.vergleiche(
            ergebnis.erkennerAllein.volltext, ergebnis.erkennerNebenlauf.volltext
        )
        appendLine("  Die beiden Transkripte direkt gegeneinander:")
        if (gegeneinander.fehlerrate == 0.0) {
            appendLine("    wortgleich")
        } else {
            appendLine("    %.1f%% Abweichung".format(gegeneinander.fehlerrate * 100))
            appendLine(gegeneinander.unterschiede())
        }
        appendLine()

        appendLine("  URTEIL")
        val unterschied = (neben.fehlerrate - allein.fehlerrate) * 100
        appendLine("    Fehlerrate nebenläufig minus allein: %+.1f Prozentpunkte".format(unterschied))
        appendLine(
            when {
                ergebnis.erkennerAllein.volltext.isBlank() ||
                    ergebnis.erkennerNebenlauf.volltext.isBlank() ->
                    "    Nicht auswertbar: mindestens ein Lauf lieferte keinen Text."
                unterschied > 5 ->
                    "    Nebenlauf ist messbar schlechter. Bleibt Forschung."
                unterschied < -5 ->
                    "    Nebenlauf ist besser -- das ist unerwartet und vor jeder " +
                        "Verwendung zu wiederholen, statt es zu glauben."
                else ->
                    "    Kein messbarer Qualitätsunterschied in diesem Lauf."
            }
        )
        appendLine()
        appendLine("  Was dieser Lauf NICHT zeigt:")
        appendLine("    Ein einzelner Durchgang je Gerät. Sprechweise, Umgebungsgeräusch")
        appendLine("    und Haltung des Geräts schwanken zwischen den beiden Läufen, auch")
        appendLine("    wenn dieselbe Person denselben Text liest. Ein Unterschied von")
        appendLine("    wenigen Prozentpunkten liegt in diesem Rauschen und trägt keine")
        appendLine("    Entscheidung. Erst mehrere Durchgänge machen daraus eine Aussage.")
        appendLine("    Außerdem verzerren Schreibweisen die absolute Fehlerrate gegen den")
        appendLine("    Bezugstext (Ziffern, \"vierzehn Uhr dreißig\" gegen \"14:30\").")
        appendLine("    Der Vergleich allein gegen nebenläufig ist davon nicht betroffen.")
    }

    private fun marke(verlauf: Pegelverlauf, was: String): Long? =
        verlauf.zeitmarken.firstOrNull { it.was == was }?.beiMillis

    private fun millis(wert: Long?): String = wert?.let { "$it ms" } ?: "-"

    private fun umbrich(text: String): String =
        text.chunked(72).joinToString("\n") { "    $it" }
}
