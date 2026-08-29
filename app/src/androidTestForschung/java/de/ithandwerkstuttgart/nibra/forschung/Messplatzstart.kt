package de.ithandwerkstuttgart.nibra.forschung

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Startet den Messplatz aus dem Prozess der App heraus.
 *
 * **Warum das der richtige Weg ist.** Der Messplatz öffnet auf eine Absicht
 * hin das Mikrofon, ohne dass jemand etwas antippt. Wäre die Aktivität
 * exportiert, könnte jede mitinstallierte App das auslösen -- eine Aufnahme
 * ohne eigenes Mikrofonrecht.
 *
 * Nicht zu exportieren war bisher keine Wahl, weil `adb shell am start`
 * dann eine SecurityException bekommt und die Messung von Hand zu bedienen
 * wäre. Eine Instrumentierung läuft aber **im Prozess der App selbst** und
 * darf deren nicht exportierte Aktivitäten starten. Damit braucht es weder
 * einen Export noch einen selbstgebauten Schlüssel: Android trägt die
 * Zugangskontrolle, nicht wir.
 *
 * Aufruf, mit denselben Zusätzen wie früher:
 * ```
 * adb shell am instrument -w \
 *   -e class de.ithandwerkstuttgart.nibra.forschung.Messplatzstart#starte \
 *   -e versuch verzug -e laeufe 20 \
 *   de.ithandwerkstuttgart.nibra.forschung/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
class Messplatzstart {

    @Test
    fun starte() {
        val werkzeug = InstrumentationRegistry.getInstrumentation()
        val gaben = InstrumentationRegistry.getArguments()
        val versuch = gaben.getString("versuch")
            ?: error("Es fehlt -e versuch <name>")

        val zusammenhang = werkzeug.targetContext
        val absicht = Intent(zusammenhang, ForschungActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(versuch, true)
            // Alles Weitere durchreichen. Zahlen und Wahrheitswerte werden
            // erkannt, damit die Aufrufe von früher unverändert gelten.
            gaben.keySet().forEach { schluessel ->
                if (schluessel in UEBERGANGEN || schluessel == versuch) return@forEach
                val wert = gaben.getString(schluessel) ?: return@forEach
                when {
                    wert == "true" || wert == "false" ->
                        absichtWahrheit(this, schluessel, wert == "true")
                    wert.toIntOrNull() != null -> putExtra(schluessel, wert.toInt())
                    else -> putExtra(schluessel, wert)
                }
            }
        }

        // Ergebnisdatei vorher wegräumen, damit das Warten nicht auf einen
        // alten Bericht hereinfällt.
        val bericht = File(zusammenhang.getExternalFilesDir(null), berichtsname(versuch))
        bericht.delete()

        zusammenhang.startActivity(absicht)

        // Der Messplatz läuft in seinem eigenen Faden weiter; die
        // Instrumentierung wartet nur, bis der Bericht liegt. Die Grenze ist
        // grosszügig, weil ein Sitzungslauf über eine Viertelstunde dauert.
        val bis = System.currentTimeMillis() + WARTEGRENZE_MILLIS
        while (System.currentTimeMillis() < bis && !bericht.exists()) {
            Thread.sleep(2_000)
        }
        assertTrue(
            "Kein Bericht ${bericht.name} nach ${WARTEGRENZE_MILLIS / 60_000} Minuten",
            bericht.exists()
        )
    }

    private fun absichtWahrheit(absicht: Intent, schluessel: String, wert: Boolean) {
        absicht.putExtra(schluessel, wert)
    }

    private fun berichtsname(versuch: String): String = when (versuch) {
        "vorlauf" -> "vorlaufversuch.txt"
        "dauer" -> "dauerversuch.txt"
        "lebenslauf" -> "lebenslauf.txt"
        "verzug" -> "verzug.txt"
        "vorgabe" -> "vorgabe.txt"
        "transport" -> "transport.txt"
        "sitzungen" -> "sitzungen.txt"
        "vergleich" -> "vergleich.txt"
        "livestrecke" -> "livestrecke.txt"
        "tonquelle" -> "tonquelle.txt"
        "diagnose" -> "erkennerdiagnose.txt"
        "mikrofon" -> "mikrofonvergleich.txt"
        "still" -> {
            val gaben = InstrumentationRegistry.getArguments()
            val echt = gaben.getString("satzsatz") == "echt"
            val vorgabe = gaben.getString("vorgabe") == "true"
            when {
                echt && vorgabe -> "echt-vorgabe.txt"
                echt -> "echt-segment.txt"
                vorgabe -> "still-vorgabe.txt"
                else -> "still-segment.txt"
            }
        }
        else -> error("Unbekannter Versuch: $versuch")
    }

    private companion object {
        val UEBERGANGEN = setOf(
            "class", "package", "annotation", "notAnnotation", "listener",
            "filter", "runnerBuilder", "debug", "log", "coverage",
            "coverageFile", "size", "numShards", "shardIndex", "versuch"
        )
        const val WARTEGRENZE_MILLIS = 45L * 60 * 1000
    }
}
