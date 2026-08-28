package de.ithandwerkstuttgart.nibra

import android.graphics.RuntimeShader
import androidx.test.core.app.ApplicationProvider
import de.ithandwerkstuttgart.nibra.dienst.Blasenansicht
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Schützt die Eigentümerschaft am [RuntimeShader].
 *
 * Die native Wettlaufsituation selbst lässt sich hier nicht nachstellen --
 * sie entsteht zwischen Anzeige- und Zeichenfaden auf echter Grafikhardware.
 * Was sich prüfen lässt, ist der **Aufbau**, der sie ermöglicht hat:
 *
 * 1. Die Blase zeichnet in ihrer eigenen Ansicht, nicht als Hintergrund.
 * 2. Jede Ansicht hat ihren eigenen Shader.
 * 3. Nirgends im Bestand mutiert eine `Drawable` einen `RuntimeShader`.
 *
 * Der Beweis, dass der Absturz weg ist, steht in
 * `messungen/blasen-stresstest.md`. Diese Tests sorgen dafür, dass der
 * Aufbau nicht unbemerkt zurückfällt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class BlasenansichtTest {

    private val zusammenhang get() =
        ApplicationProvider.getApplicationContext<android.content.Context>()

    /**
     * Ein Hintergrund wäre eine zweite Zeichenebene mit eigenem
     * Aufzeichnungsknoten -- genau der Aufbau, der den Prozess abgeschossen
     * hat. `ImageButton` setzt von sich aus einen; die Ansicht muss ihn
     * ausdrücklich abräumen.
     */
    @Test
    fun `die blase hat keinen hintergrund`() {
        assertNull(Blasenansicht(zusammenhang).background)
    }

    /**
     * Zwei Blasen dürfen sich niemals einen Shader teilen. Sonst schriebe
     * die eine Uniforms, während die andere zeichnet.
     */
    @Test
    fun `jede blase besitzt ihren eigenen shader`() {
        val eine = holeShader(Blasenansicht(zusammenhang))
        val andere = holeShader(Blasenansicht(zusammenhang))
        assertNotNull("Ohne Shader prüft der Test nichts", eine)
        assertNotNull("Ohne Shader prüft der Test nichts", andere)
        assertNotSame("Zwei Blasen teilen sich einen Shader", eine, andere)
    }

    /** Der runde Schatten hängt am Umriss; ohne ihn wäre er eckig. */
    @Test
    fun `die blase gibt einen eigenen umriss vor`() {
        assertNotNull(Blasenansicht(zusammenhang).outlineProvider)
    }

    /**
     * Die eigentliche Regel als Quelltextprüfung: **keine `Drawable` fasst
     * einen `RuntimeShader` an.**
     *
     * Ein Test auf eine einzelne Klasse hätte den Fehler nur dort verhindert.
     * Diese Prüfung greift auch, wenn jemand das Muster an einer ganz anderen
     * Stelle wieder einführt.
     */
    @Test
    fun `keine drawable mutiert einen runtime shader`() {
        val schuldige = quelldateien().filter { datei ->
            val text = datei.readText()
            val istDrawable = Regex("""class\s+\w+[^{]*:\s*[^{]*\bDrawable\(""")
                .containsMatchIn(text)
            istDrawable && text.contains("RuntimeShader")
        }
        assertTrue(
            "Diese Drawables fassen einen RuntimeShader an: " +
                schuldige.joinToString { it.name } +
                ". Ein Drawable bekommt von Android einen eigenen " +
                "Aufzeichnungsknoten; ein dort mutierter Shader hat den " +
                "Prozess abgeschossen. Gehört in eine eigene Ansicht.",
            schuldige.isEmpty()
        )
    }

    /**
     * Uniforms dürfen nur im Zeichenpfad gesetzt werden. Wird das anderswo
     * getan, ist der Zugriff nicht mehr auf einen Faden beschränkt.
     */
    @Test
    fun `uniforms werden nur im zeichenpfad gesetzt`() {
        val erlaubt = setOf("Blasenansicht.kt", "Blob.kt")
        val fremde = quelldateien().filter {
            it.readText().contains("setFloatUniform") && it.name !in erlaubt
        }
        assertTrue(
            "Unerwartete Stellen setzen Shader-Uniforms: " +
                fremde.joinToString { it.name } +
                ". Jede neue Stelle braucht eine bewusste Entscheidung " +
                "darüber, wem der Shader gehört.",
            fremde.isEmpty()
        )
    }

    private fun holeShader(ansicht: Blasenansicht): RuntimeShader? {
        val feld = Blasenansicht::class.java.getDeclaredField("shader")
        feld.isAccessible = true
        return feld.get(ansicht) as RuntimeShader?
    }

    /**
     * Der Quellbaum, vom Testverzeichnis aus gesucht. Findet er nichts,
     * schlägt der Test fehl statt still nichts zu prüfen.
     */
    private fun quelldateien(): List<File> {
        val wurzel = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java") }
            .firstOrNull { it.isDirectory }
        assertNotNull("Quellverzeichnis nicht gefunden", wurzel)
        val dateien = wurzel!!.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("Keine Quelldateien gefunden", dateien.size > 10)
        return dateien
    }
}
