package de.ithandwerkstuttgart.loqui.verlauf

import de.ithandwerkstuttgart.loqui.ui.modell.Diktat
import de.ithandwerkstuttgart.loqui.ui.modell.Gruppenschluessel
import de.ithandwerkstuttgart.loqui.ui.modell.VerlaufGruppe
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Ordnet Diktate in die vier Gruppen des Verlaufs. Aeltere Eintraege
 * bekommen je Tag eine eigene Gruppe mit dem formatierten Datum als Titel.
 */
fun ordneVerlauf(
    diktate: List<Diktat>,
    jetztMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): List<VerlaufGruppe> {
    if (diktate.isEmpty()) return emptyList()

    val heute = Instant.ofEpochMilli(jetztMillis).atZone(zone).toLocalDate()
    val gestern = heute.minusDays(1)
    val wochenanfang = heute.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val sortiert = diktate.sortedByDescending(Diktat::zeitpunktMillis)
    val gruppen = mutableListOf<VerlaufGruppe>()

    fun tagVon(diktat: Diktat): LocalDate =
        Instant.ofEpochMilli(diktat.zeitpunktMillis).atZone(zone).toLocalDate()

    val heutige = sortiert.filter { tagVon(it) == heute }
    val gestrige = sortiert.filter { tagVon(it) == gestern }
    val diesewoche = sortiert.filter {
        val tag = tagVon(it)
        tag < gestern && !tag.isBefore(wochenanfang)
    }
    val aeltere = sortiert.filter { tagVon(it).isBefore(minOf(wochenanfang, gestern)) }

    if (heutige.isNotEmpty()) {
        gruppen += VerlaufGruppe(Gruppenschluessel.HEUTE, diktate = heutige)
    }
    if (gestrige.isNotEmpty()) {
        gruppen += VerlaufGruppe(Gruppenschluessel.GESTERN, diktate = gestrige)
    }
    if (diesewoche.isNotEmpty()) {
        gruppen += VerlaufGruppe(Gruppenschluessel.DIESE_WOCHE, diktate = diesewoche)
    }
    aeltere.groupBy { it.datum }.forEach { (datum, eintraege) ->
        gruppen += VerlaufGruppe(
            schluessel = Gruppenschluessel.AELTER,
            eigenesDatum = datum,
            diktate = eintraege
        )
    }
    return gruppen
}

/** Sucht im Text; leerer Begriff liefert alles zurueck. */
fun suche(diktate: List<Diktat>, begriff: String): List<Diktat> =
    if (begriff.isBlank()) diktate
    else diktate.filter { it.text.contains(begriff.trim(), ignoreCase = true) }
