package de.ithandwerkstuttgart.nibra.forschung

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Der Pegel ueber die Zeit, in Faechern von 100 ms.
 *
 * Ein einzelner Groesstwert ueber eine ganze Aufnahme verbirgt genau das, was
 * hier interessiert: ob ein Sprung **kurz** ist (ein Startton) oder **bleibt**
 * (eine umgeschaltete Verstaerkung). Deshalb wird der Verlauf gefuehrt und
 * nicht nur die Summe.
 */
class Pegelverlauf(private val abtastrate: Int) {

    /** Ein Zeitfach mit dem, was in ihm ankam. */
    data class Fach(
        val abMillis: Long,
        val rahmen: Int,
        val spitze: Int,
        val effektivwert: Double,
        val stilleRahmen: Int,
        val uebersteuerteRahmen: Int
    )

    /** Ein benannter Zeitpunkt im Verlauf, z. B. „Erkenner gestartet". */
    data class Marke(val beiMillis: Long, val was: String)

    private val faecher = mutableListOf<Fach>()
    private val marken = mutableListOf<Marke>()

    private var fachAb = 0L
    private var fachRahmen = 0
    private var fachSpitze = 0
    private var fachQuadrate = 0.0
    private var fachStille = 0
    private var fachUebersteuert = 0
    private var rahmenGesamt = 0L

    val rahmen: Long get() = rahmenGesamt

    // Sampleverlust laesst sich nicht an den Abtastwerten selbst ablesen --
    // die Zeitachse ist ja aus ihnen berechnet und kann deshalb gar nicht von
    // ihnen abweichen. Es braucht eine zweite, unabhaengige Uhr.
    private var uhrStart = 0L
    private var uhrEnde = 0L
    private var rahmenBeiUhrStart = 0L

    /** Setzt den Bezugspunkt, sobald der erste Block wirklich da ist. */
    fun beginneZeitmessung(uhrMillis: Long) {
        uhrStart = uhrMillis
        rahmenBeiUhrStart = rahmenGesamt
    }

    fun beendeZeitmessung(uhrMillis: Long) {
        uhrEnde = uhrMillis
    }

    /**
     * Wie viele Millisekunden Signal gegenueber der Uhr fehlen.
     *
     * Positiv heisst: die Uhr ist weiter gelaufen als Abtastwerte angekommen
     * sind -- es ist Ton verloren gegangen. Um null herum heisst: der Strom
     * ist luekenlos.
     */
    fun verlustMillis(): Long? {
        if (uhrStart == 0L || uhrEnde == 0L) return null
        val nachUhr = uhrEnde - uhrStart
        val nachAbtastwerten = (rahmenGesamt - rahmenBeiUhrStart) * 1000 / abtastrate
        return nachUhr - nachAbtastwerten
    }

    fun uhrdauerMillis(): Long? =
        if (uhrStart == 0L || uhrEnde == 0L) null else uhrEnde - uhrStart
    val zeitfaecher: List<Fach> get() = faecher.toList()
    val zeitmarken: List<Marke> get() = marken.toList()

    /** Verstrichene Zeit seit Aufnahmebeginn, aus der Anzahl der Abtastwerte. */
    fun millisJetzt(): Long = rahmenGesamt * 1000 / abtastrate

    fun merke(was: String) {
        marken += Marke(millisJetzt(), was)
    }

    /**
     * Nimmt einen gelesenen Block auf. Die Zeit wird aus der Anzahl der
     * Abtastwerte berechnet, nicht aus der Uhr -- das ist die einzige
     * Zeitachse, die zum Signal selbst gehoert und nicht davon abweichen kann,
     * wenn der Faden einmal spaeter drankommt.
     */
    fun nimm(block: ShortArray, anzahl: Int) {
        for (i in 0 until anzahl) {
            val wert = block[i].toInt()
            val betrag = abs(wert)
            if (betrag > fachSpitze) fachSpitze = betrag
            if (betrag <= 2) fachStille++
            if (betrag >= 32_000) fachUebersteuert++
            fachQuadrate += wert.toDouble() * wert
            fachRahmen++
            rahmenGesamt++

            if (fachRahmen >= abtastrate / 10) schliesseFach()
        }
    }

    fun schliesseAb() {
        if (fachRahmen > 0) schliesseFach()
    }

    private fun schliesseFach() {
        faecher += Fach(
            abMillis = fachAb,
            rahmen = fachRahmen,
            spitze = fachSpitze,
            effektivwert = sqrt(fachQuadrate / fachRahmen),
            stilleRahmen = fachStille,
            uebersteuerteRahmen = fachUebersteuert
        )
        fachAb = millisJetzt()
        fachRahmen = 0
        fachSpitze = 0
        fachQuadrate = 0.0
        fachStille = 0
        fachUebersteuert = 0
    }

    /** Zusammenfassung eines Zeitabschnitts zwischen zwei Marken. */
    data class Abschnitt(
        val name: String,
        val vonMillis: Long,
        val bisMillis: Long,
        val faecher: Int,
        val spitze: Int,
        val effektivwertMittel: Double,
        val stilleAnteil: Double,
        val uebersteuertAnteil: Double
    )

    fun abschnitt(name: String, vonMillis: Long, bisMillis: Long): Abschnitt {
        val teil = faecher.filter { it.abMillis in vonMillis until bisMillis }
        val rahmenSumme = teil.sumOf { it.rahmen }.toDouble()
        return Abschnitt(
            name = name,
            vonMillis = vonMillis,
            bisMillis = bisMillis,
            faecher = teil.size,
            spitze = teil.maxOfOrNull { it.spitze } ?: 0,
            // Ueber die Faecher gemittelt, nach ihrer Laenge gewichtet --
            // sonst zaehlte ein angebrochenes Fach am Rand so viel wie ein volles.
            effektivwertMittel = if (rahmenSumme == 0.0) 0.0
            else teil.sumOf { it.effektivwert * it.rahmen } / rahmenSumme,
            stilleAnteil = if (rahmenSumme == 0.0) 0.0
            else teil.sumOf { it.stilleRahmen.toDouble() } / rahmenSumme,
            uebersteuertAnteil = if (rahmenSumme == 0.0) 0.0
            else teil.sumOf { it.uebersteuerteRahmen.toDouble() } / rahmenSumme
        )
    }

    /**
     * Der Verlauf als Textbild -- ein Balken je Fach, damit ein Sprung im
     * Bericht **sichtbar** ist und nicht in einer Zahlenkolonne verschwindet.
     */
    fun alsBild(breite: Int = 46): String {
        if (faecher.isEmpty()) return "  (kein Verlauf)"
        val groesster = faecher.maxOf { it.effektivwert }.coerceAtLeast(1.0)
        val markenNach = zeitmarken.groupBy { it.beiMillis / 100 }
        return faecher.mapIndexed { stelle, fach ->
            val laenge = (fach.effektivwert / groesster * breite).toInt()
            val hinweis = markenNach[fach.abMillis / 100]
                ?.joinToString(" ") { "<< ${it.was}" }
                .orEmpty()
            "  %6d ms |%s%s %6.1f %s".format(
                fach.abMillis,
                "#".repeat(laenge),
                " ".repeat((breite - laenge).coerceAtLeast(0)),
                fach.effektivwert,
                hinweis
            )
        }.joinToString("\n")
    }
}
