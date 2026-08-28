package de.ithandwerkstuttgart.nibra.forschung

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import kotlin.math.abs

/**
 * Gate 2, zweiter Teil: **trägt der Transportweg über eine Viertelstunde?**
 *
 * Der erste Teil ([Dauerversuch]) speist Ton ein und fragt, wie lange die
 * *Sitzung* des Erkenners trägt. Dabei bleibt [Tonstrecke] außen vor --
 * und damit genau die Zahlen, die den Transport beschreiben: gelesene
 * gegen erwartete Rahmen, Drift, verworfene Blöcke, Tiefe der
 * Warteschlange.
 *
 * Hier läuft deshalb der **echte Weg**: `AudioRecord` liest das Mikrofon,
 * die Strecke schiebt in ein Rohr. Der Erkenner fehlt mit Absicht -- er
 * würde nichts zum Transport beitragen, aber die Läufe von seinem eigenen
 * Verhalten abhängig machen.
 *
 * **Was aufgenommen wird, ist gleichgültig.** Gemessen wird der Weg, nicht
 * der Inhalt: ein stiller Raum liefert dieselben Rahmenzahlen wie ein
 * lauter. Der Ton wird gelesen, gezählt und verworfen -- nichts davon
 * verlässt den Arbeitsspeicher.
 *
 * Die Gegenseite des Rohrs wird mitgelesen und weggeworfen. Ohne Leser
 * liefe das Rohr nach wenigen Sekunden voll, und gemessen wäre dann die
 * Größe des Rohrpuffers statt der Strecke.
 */
class Streckendauerlauf(
    @Suppress("unused") private val zusammenhang: Context,
    private val aufStand: (String) -> Unit
) {

    data class Lauf(
        val dauerMillis: Long,
        val befund: Tonstrecke.Befund,
        val vorher: Prozessbefund.Stand,
        val nachher: Prozessbefund.Stand,
        /**
         * Alle Prozessstände über den Lauf. Anfang und Ende allein
         * verbergen einen Ausschlag dazwischen -- eine App, die
         * zwischendurch auf das Dreifache geht und rechtzeitig wieder
         * aufräumt, sähe genauso aus wie eine ruhige.
         */
        val staende: List<Prozessbefund.Stand>,
        val verschlungeneBytes: Long
    ) {
        /** Wie viele Rahmen bei dieser Laufzeit hätten kommen müssen. */
        val erwarteteRahmen: Long get() = befund.laufzeitMillis * ABTASTRATE / 1000

        /** Dasselbe für den eingeschwungenen Teil, ohne die Ränder. */
        val erwarteteTaktRahmen: Long get() = befund.taktMillis * ABTASTRATE / 1000

        /**
         * Abweichung des Takts in Teilen je Million, **ohne die Ränder**.
         * Prozent wären hier zu grob: 0,1 % über eine Viertelstunde ist
         * knapp eine Sekunde Versatz.
         */
        val driftJeMillion: Long
            get() = if (erwarteteTaktRahmen == 0L) 0
            else (befund.taktRahmen - erwarteteTaktRahmen) * 1_000_000 / erwarteteTaktRahmen

        val rechenzeitMillis: Long?
            get() = if (vorher.rechenzeitMillis == null || nachher.rechenzeitMillis == null) null
            else nachher.rechenzeitMillis - vorher.rechenzeitMillis

        /** Anteil einer Kernlast, in Promille. */
        val lastPromille: Long?
            get() = rechenzeitMillis?.let {
                if (befund.laufzeitMillis == 0L) null else it * 1000 / befund.laufzeitMillis
            }

        val zeigerRest: Int?
            get() = if (vorher.offeneZeiger == null || nachher.offeneZeiger == null) null
            else nachher.offeneZeiger - vorher.offeneZeiger

        val faedenRest: Int get() = nachher.faeden - vorher.faeden

        val speicherHoechst: Long get() = (staende.map { it.speicherKb } + vorher.speicherKb).max()
        val faedenHoechst: Int get() = (staende.map { it.faeden } + vorher.faeden).max()
        val zeigerHoechst: Int?
            get() = (staende.mapNotNull { it.offeneZeiger } + listOfNotNull(vorher.offeneZeiger))
                .maxOrNull()

        /**
         * Ratenabweichung je Teilfenster, in Teilen je Million.
         *
         * **Ursache ausdrücklich offen.** Gemessen ist, wie viele
         * Abtastwerte je Zeiteinheit ankommen, verglichen mit der
         * Nennrate und unserer eigenen Uhr. Ob die Abweichung aus dem
         * Audiotakt der Hardware stammt, aus dem Audiotreiber, aus einer
         * Umtastung im Treiber, aus der Einteilung der Rechenzeit oder aus
         * unserer Referenzuhr, ist damit **nicht** entschieden.
         *
         * Wichtig ist etwas anderes: bleibt sie über die Fenster hinweg
         * ungefähr gleich, oder wandert sie? Eine ruhige kleine Abweichung
         * ist harmlos, eine wachsende nicht -- und am Endwert allein sind
         * die beiden nicht zu unterscheiden.
         */
        val fensterAbweichung: List<Pair<Long, Long>>
            get() = Ratenverlauf.jeFenster(
                zeitenMillis = befund.proben.map { it.zeitMillis },
                rahmen = befund.proben.map { it.rahmen },
                abtastrate = ABTASTRATE
            )

        /** Größter Rückstand in der Warteschlange, in Millisekunden Ton. */
        val groessterRueckstandMillis: Long
            get() = befund.groessteWarteschlange.toLong() *
                Tonstrecke.BLOCK_BYTES / 2 * 1000 / ABTASTRATE
    }

    fun fuehreDurch(dauern: List<Long>): String = buildString {
        appendLine("TRANSPORTDAUERLAUF -- ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("AudioRecord -> Tonstrecke -> Rohr, ohne Erkenner. Gemessen wird der")
        appendLine("Weg, nicht der Inhalt: ein stiller Raum liefert dieselben Rahmen-")
        appendLine("zahlen wie ein lauter. Der Ton wird gezählt und verworfen.")
        appendLine("Rechenzeit in Takten zu ${1000 / Prozessbefund.TAKTE_JE_SEKUNDE} ms -- feiner geht es nicht.")
        appendLine()

        // Kontrollfall vor der eigentlichen Messung: ein kurzer Lauf mit
        // bekanntem Ausgang. Bleibt hier etwas leer oder steht die Drift
        // auf einem unmöglichen Wert, ist das Messmittel kaputt und die
        // langen Läufe wären verlorene Zeit.
        aufStand("Kontrollfall (5 s)")
        val probe = lauf(KONTROLLE_MILLIS)
        appendLine("KONTROLLFALL (5 s, bekannter Ausgang)")
        val kontrolleOk = probe.befund.geleseneRahmen > 0 &&
            abs(probe.driftJeMillion) < KONTROLLE_DRIFT_GRENZE &&
            probe.befund.fehler == null
        appendLine("  gelesene Rahmen ${probe.befund.geleseneRahmen}, " +
            "davon eingeschwungen ${probe.befund.taktRahmen} in ${probe.befund.taktMillis} ms")
        appendLine("  Drift ${probe.driftJeMillion} ppm, " +
            "Fehler ${probe.befund.fehler ?: "keiner"}")
        appendLine("  ${if (kontrolleOk) "in Ordnung -- die Messung darf gelten"
            else "FEHLGESCHLAGEN -- die langen Läufe wären nicht auswertbar"}")
        appendLine()
        if (!kontrolleOk) return@buildString

        dauern.forEach { dauer ->
            aufStand("Transportlauf ${dauer / 1000} s")
            schreibe(lauf(dauer))
        }
    }

    private fun StringBuilder.schreibe(l: Lauf) {
        val b = l.befund
        appendLine("LAUF ${l.dauerMillis / 1000} s")
        appendLine("  Laufzeit               ${b.laufzeitMillis} ms")
        appendLine("  gelesene Rahmen        ${b.geleseneRahmen}")
        appendLine("  erwartete Rahmen       ${l.erwarteteRahmen}")
        appendLine("  Randversatz            ${b.geleseneRahmen - l.erwarteteRahmen} Rahmen " +
            "(Anlauf und Ende, kein Takt)")
        appendLine("  Takt, eingeschwungen   ${b.taktRahmen} Rahmen in ${b.taktMillis} ms")
        appendLine("  Drift                  ${l.driftJeMillion} ppm " +
            "(${b.taktRahmen - l.erwarteteTaktRahmen} Rahmen)")
        appendLine("  Verlust gegen die Uhr  ${b.verlustMillis} ms " +
            if (b.luekenlos) "(lückenlos)" else "(ES FEHLT TON)")
        appendLine("  verworfene Blöcke      ${b.verworfeneBloecke}")
        appendLine("  größte Warteschlange   ${b.groessteWarteschlange} von 64")
        appendLine("  blockierte Schreiber   ${b.blockierteSchreibversuche}")
        appendLine("  Lesefehler             ${b.leseFehler}")
        appendLine("  an das Rohr            ${b.gesendeteBytes} Bytes")
        appendLine("  am Rohr abgeholt       ${l.verschlungeneBytes} Bytes")
        appendLine("  größter Ausschlag      ${b.spitze}/32767")
        appendLine("  Fehler                 ${b.fehler ?: "keiner"}")
        appendLine("  Rechenzeit             ${l.rechenzeitMillis?.let { "$it ms" } ?: "nicht gemessen"}" +
            (l.lastPromille?.let { " = ${it / 10},${it % 10} % eines Kerns" } ?: ""))
        appendLine("  größter Rückstand      ${l.groessterRueckstandMillis} ms Ton in der Schlange")
        appendLine("  Speicher               ${l.vorher.speicherKb} / höchstens " +
            "${l.speicherHoechst} / ${l.nachher.speicherKb} KB")
        appendLine("  Dateizeiger            ${l.vorher.offeneZeiger} / höchstens " +
            "${l.zeigerHoechst} / ${l.nachher.offeneZeiger} " +
            "(${l.zeigerRest?.let { vorzeichen(it) } ?: "nicht gemessen"})")
        appendLine("  Fäden                  ${l.vorher.faeden} / höchstens " +
            "${l.faedenHoechst} / ${l.nachher.faeden} (${vorzeichen(l.faedenRest)})")
        appendLine()
        appendLine("  VERLAUF (alle ${Tonstrecke.PROBENABSTAND_MILLIS / 1000} s)")
        appendLine("    %-8s %-12s %-8s %-10s %s".format(
            "Zeit", "Rahmen", "Schlange", "verworfen", "Abweichung"))
        val abweichungen = l.fensterAbweichung.toMap()
        l.befund.proben.forEach { probe ->
            appendLine("    %-8s %-12s %-8s %-10s %s".format(
                "${probe.zeitMillis / 1000} s", probe.rahmen,
                probe.warteschlangeTiefe, probe.verworfeneBloecke,
                abweichungen[probe.zeitMillis]?.let { "$it ppm" } ?: "-"))
        }
        val ppm = l.fensterAbweichung.map { it.second }
        if (ppm.size >= 2) {
            appendLine("  Abweichung über die Fenster: " +
                "kleinste ${ppm.min()} ppm, größte ${ppm.max()} ppm, " +
                "Spanne ${ppm.max() - ppm.min()} ppm")
            // Wandert die Abweichung, unterscheiden sich die erste und die
            // letzte Haelfte deutlich. Bleibt sie ruhig, nicht.
            val ersteHaelfte = ppm.take(ppm.size / 2)
            val zweiteHaelfte = ppm.drop(ppm.size / 2)
            val a = ersteHaelfte.average()
            val b = zweiteHaelfte.average()
            appendLine("  erste Hälfte %.0f ppm, zweite Hälfte %.0f ppm, Unterschied %.0f ppm"
                .format(a, b, b - a))
            appendLine("  " + when (Ratenverlauf.wandert(ppm, WANDERGRENZE_PPM)) {
                false -> "Die Abweichung bleibt über den Lauf ungefähr gleich."
                true -> "**Die Abweichung wandert.** Kein ruhiger Versatz, sondern " +
                    "etwas, das sich über die Zeit ändert -- das gehört verstanden."
                null -> "Zu wenige Fenster für eine Aussage über den Verlauf."
            })
        }
        appendLine()
    }

    private fun lauf(dauerMillis: Long): Lauf {
        val vorher = Prozessbefund.nimmAuf()
        val staende = java.util.Collections.synchronizedList(mutableListOf<Prozessbefund.Stand>())
        val messenLaeuft = java.util.concurrent.atomic.AtomicBoolean(true)
        val messer = Thread {
            while (messenLaeuft.get()) {
                staende += Prozessbefund.nimmAuf()
                Thread.sleep(Tonstrecke.PROBENABSTAND_MILLIS)
            }
        }.also { it.isDaemon = true; it.start() }
        val strecke = Tonstrecke(ABTASTRATE, 1_500)
        var verschlungen = 0L
        strecke.starte()
        val (lesen, schreiben) = ParcelFileDescriptor.createPipe()

        // Der Verschlinger. Ohne ihn läuft das Rohr voll und gemessen wäre
        // die Größe des Rohrpuffers statt der Strecke.
        val verschlinger = Thread {
            runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(lesen).use { strom ->
                    val eimer = ByteArray(4096)
                    while (true) {
                        val n = strom.read(eimer)
                        if (n < 0) break
                        verschlungen += n
                    }
                }
            }
        }
        verschlinger.start()

        strecke.speiseIn(schreiben, mitVorlauf = true)
        Thread.sleep(dauerMillis)
        strecke.beendeEinspeisung()
        val befund = strecke.halteAn()
        runCatching { schreiben.close() }
        verschlinger.join(5_000)
        // Aufräumen abwarten, bevor gezählt wird -- sonst gälten Fäden, die
        // gerade enden, fälschlich als liegen geblieben.
        Thread.sleep(2_000)

        messenLaeuft.set(false)
        messer.interrupt()
        return Lauf(
            dauerMillis, befund, vorher, Prozessbefund.nimmAuf(),
            staende.toList(), verschlungen
        )
    }

    private fun vorzeichen(wert: Int) = if (wert > 0) "+$wert" else "$wert"

    companion object {
        const val ABTASTRATE = 16_000
        const val KONTROLLE_MILLIS = 5_000L

        /**
         * Ein Mikrofon darf ein paar hundert ppm neben der Nennrate liegen;
         * ein Prozent wäre ein Fehler im Aufbau, kein Quarz.
         */
        const val KONTROLLE_DRIFT_GRENZE = 10_000L

        /**
         * Ab diesem Unterschied zwischen erster und zweiter Hälfte gilt die
         * Abweichung als wandernd statt ruhig.
         */
        const val WANDERGRENZE_PPM = 500.0
    }
}
