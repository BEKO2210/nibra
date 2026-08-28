package de.ithandwerkstuttgart.loqui

import de.ithandwerkstuttgart.loqui.ui.modell.Diktat
import de.ithandwerkstuttgart.loqui.ui.modell.Gruppenschluessel
import de.ithandwerkstuttgart.loqui.verlauf.ordneVerlauf
import de.ithandwerkstuttgart.loqui.verlauf.suche
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class VerlaufsordnungTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    /** Donnerstag, 12:00 Uhr -- Wochenanfang ist der Montag davor. */
    private val jetzt = LocalDateTime.of(2026, 8, 27, 12, 0)
        .atZone(zone).toInstant().toEpochMilli()

    private fun diktat(tageZurueck: Long, stunde: Int = 9, datum: String = "") = Diktat(
        id = "$tageZurueck-$stunde",
        text = "Text $tageZurueck",
        zeitpunktMillis = LocalDateTime.of(2026, 8, 27, stunde, 0)
            .minusDays(tageZurueck).atZone(zone).toInstant().toEpochMilli(),
        uhrzeit = "$stunde:00",
        datum = datum.ifBlank { "Tag-$tageZurueck" },
        sprachCode = "de-DE",
        sprachName = "Deutsch",
        dauerSekunden = 5
    )

    @Test
    fun `heute gestern diese woche und aelter werden getrennt`() {
        val gruppen = ordneVerlauf(
            listOf(diktat(0), diktat(1), diktat(2), diktat(9)),
            jetzt,
            zone
        )
        assertEquals(
            listOf(
                Gruppenschluessel.HEUTE,
                Gruppenschluessel.GESTERN,
                Gruppenschluessel.DIESE_WOCHE,
                Gruppenschluessel.AELTER
            ),
            gruppen.map { it.schluessel }
        )
    }

    @Test
    fun `leere gruppen fallen weg`() {
        val gruppen = ordneVerlauf(listOf(diktat(0)), jetzt, zone)
        assertEquals(1, gruppen.size)
        assertEquals(Gruppenschluessel.HEUTE, gruppen.first().schluessel)
    }

    @Test
    fun `aeltere tage bekommen je datum eine eigene gruppe`() {
        val gruppen = ordneVerlauf(
            listOf(diktat(9, datum = "18.08.2026"), diktat(10, datum = "17.08.2026")),
            jetzt,
            zone
        )
        assertEquals(2, gruppen.size)
        assertEquals(listOf("18.08.2026", "17.08.2026"), gruppen.map { it.eigenesDatum })
    }

    @Test
    fun `innerhalb einer gruppe steht das juengste diktat oben`() {
        val gruppen = ordneVerlauf(listOf(diktat(0, 8), diktat(0, 11)), jetzt, zone)
        assertEquals(listOf("0-11", "0-8"), gruppen.first().diktate.map { it.id })
    }

    @Test
    fun `suche findet ohne ruecksicht auf grossschreibung`() {
        val treffer = suche(listOf(diktat(0), diktat(1)), "TEXT 1")
        assertEquals(1, treffer.size)
        assertEquals("Text 1", treffer.first().text)
    }

    @Test
    fun `leere suche liefert alles`() {
        val alle = listOf(diktat(0), diktat(1))
        assertEquals(alle, suche(alle, "   "))
    }
}
