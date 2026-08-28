package de.ithandwerkstuttgart.loqui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.ithandwerkstuttgart.loqui.daten.EinstellungenAblage
import de.ithandwerkstuttgart.loqui.daten.LoquiDatenbank
import de.ithandwerkstuttgart.loqui.daten.TextbausteinEintrag
import de.ithandwerkstuttgart.loqui.erkennung.Erkennerquelle
import de.ithandwerkstuttgart.loqui.erkennung.Erkennungsereignis
import de.ithandwerkstuttgart.loqui.erkennung.Sprachverzeichnis
import de.ithandwerkstuttgart.loqui.ui.LoquiViewModel
import de.ithandwerkstuttgart.loqui.ui.modell.Aufnahmezustand
import de.ithandwerkstuttgart.loqui.ui.modell.Fehlerart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prueft die Kette Aufnahme -> Erkennung -> Textbausteine -> Ablage, ohne
 * echtes Sprechen: die Erkennung kommt aus [FakeErkenner].
 */
// Robolectric 4.13 bildet hoechstens API 34 nach; die App laeuft ab API 26.
@RunWith(RobolectricTestRunner::class)
// Ohne Manifest, weil Robolectric 4.13 hoechstens targetSdk 34 zulaesst;
// die App selbst laeuft ab API 26 bis 37.
@Config(sdk = [34], manifest = Config.NONE)
class LoquiViewModelTest {

    /**
     * Bildet den echten Erkenner nach: Der Fluss endet nach Ergebnis oder
     * Fehler. Ein Fake mit endlosem Fluss laesst die Sammel-Coroutine des
     * ViewModels ewig laufen -- dann wartet `runTest` am Testende auf sie und
     * der Lauf haengt.
     */
    private class FakeErkenner : Erkennerquelle {
        private var senden: ((Erkennungsereignis) -> Unit)? = null
        private var schliessen: (() -> Unit)? = null
        var gestoppt = false

        override fun erkenne(
            sprachCode: String,
            stoppBeiStille: Boolean
        ): Flow<Erkennungsereignis> = callbackFlow {
            senden = { trySend(it) }
            schliessen = { close() }
            awaitClose {
                senden = null
                schliessen = null
            }
        }

        override fun stoppen() {
            gestoppt = true
        }

        /** Beendet einen noch offenen Fluss -- fuer das Testende. */
        fun schliesse() {
            schliessen?.invoke()
        }

        /** Schickt ein Ereignis; Ergebnis und Fehler beenden den Fluss. */
        fun sende(ereignis: Erkennungsereignis) {
            senden?.invoke(ereignis)
            if (ereignis is Erkennungsereignis.Ergebnis ||
                ereignis is Erkennungsereignis.Fehlgeschlagen
            ) {
                schliessen?.invoke()
            }
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var datenbank: LoquiDatenbank
    private lateinit var erkenner: FakeErkenner
    private lateinit var modell: LoquiViewModel

    /**
     * Haelt das ViewModel wie ein Bildschirm es haelt. Ohne dieses Leeren am
     * Testende laufen die Dauer-Sammlungen aus `init` weiter, und `runTest`
     * wartet am Schluss bis zu einer Minute auf sie -- der Lauf haengt.
     */
    private val ablageDerModelle = ViewModelStore()

    /**
     * Jeder Test endet mit geschlossenem Erkenner und geleertem ViewModel --
     * sonst wartet `runTest` am Schluss auf dessen Dauer-Sammlungen.
     */
    private fun pruefe(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) =
        runTest(dispatcher, timeout = 5.seconds) {
            block()
            erkenner.schliesse()
            ablageDerModelle.clear()
            advanceUntilIdle()
        }

    @Before
    fun aufbau() {
        Dispatchers.setMain(dispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        datenbank = Room.inMemoryDatabaseBuilder(context, LoquiDatenbank::class.java)
            .allowMainThreadQueries().build()
        erkenner = FakeErkenner()
        val gebaut = LoquiViewModel(
            context = context,
            diktatDao = datenbank.diktatDao(),
            textbausteinDao = datenbank.textbausteinDao(),
            ablage = EinstellungenAblage(context),
            erkenner = erkenner,
            sprachverzeichnis = Sprachverzeichnis(context)
        )
        modell = ViewModelProvider(
            ablageDerModelle,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = gebaut as T
            }
        )[LoquiViewModel::class.java]
    }

    @After
    fun abbau() {
        // Erst das ViewModel beenden, dann die Ablage schliessen.
        ablageDerModelle.clear()
        datenbank.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `ergebnis landet im verlauf`() = pruefe {
        advanceUntilIdle()
        modell.aufnahmeUmschalten()
        advanceUntilIdle()
        erkenner.sende(Erkennungsereignis.Ergebnis("Guten Morgen"))
        advanceUntilIdle()

        val zustand = modell.zustand.value
        assertEquals(1, zustand.diktate.size)
        assertEquals("Guten Morgen", zustand.diktate.first().text)
        assertEquals(Aufnahmezustand.Bereit, zustand.aufnahme)
    }

    @Test
    fun `textbausteine wirken auf das ergebnis`() = pruefe {
        datenbank.textbausteinDao().sichere(TextbausteinEintrag("1", "mfg", "Mit freundlichen Grüßen"))
        advanceUntilIdle()
        modell.aufnahmeUmschalten()
        advanceUntilIdle()
        erkenner.sende(Erkennungsereignis.Ergebnis("Bis bald mfg"))
        advanceUntilIdle()

        assertEquals("Bis bald Mit freundlichen Grüßen", modell.zustand.value.diktate.first().text)
    }

    @Test
    fun `pegel wandert in die kurve`() = pruefe {
        advanceUntilIdle()
        modell.aufnahmeUmschalten()
        advanceUntilIdle()
        erkenner.sende(Erkennungsereignis.Pegel(0.4f))
        erkenner.sende(Erkennungsereignis.Pegel(0.7f))
        advanceUntilIdle()

        val laufend = modell.zustand.value.aufnahme as Aufnahmezustand.Laeuft
        assertEquals(0.7f, laufend.pegel, 0.001f)
        assertEquals(listOf(0.4f, 0.7f), laufend.verlauf)
    }

    @Test
    fun `stille wird angezeigt`() = pruefe {
        advanceUntilIdle()
        modell.aufnahmeUmschalten()
        advanceUntilIdle()
        erkenner.sende(Erkennungsereignis.Stille)
        advanceUntilIdle()

        assertTrue((modell.zustand.value.aufnahme as Aufnahmezustand.Laeuft).stilleErkannt)
    }

    @Test
    fun `fehler wird als klartextzustand gemeldet und nichts gespeichert`() = pruefe {
        advanceUntilIdle()
        modell.aufnahmeUmschalten()
        advanceUntilIdle()
        erkenner.sende(
            Erkennungsereignis.Fehlgeschlagen(Fehlerart.NICHTS_VERSTANDEN)
        )
        advanceUntilIdle()

        assertEquals(
            Aufnahmezustand.Fehler(Fehlerart.NICHTS_VERSTANDEN),
            modell.zustand.value.aufnahme
        )
        assertTrue(modell.zustand.value.diktate.isEmpty())

        modell.fehlerZuruecksetzen()
        assertEquals(Aufnahmezustand.Bereit, modell.zustand.value.aufnahme)
    }

    @Test
    fun `zweites tippen beendet die aufnahme`() = pruefe {
        advanceUntilIdle()
        modell.aufnahmeUmschalten()
        advanceUntilIdle()
        modell.aufnahmeUmschalten()
        advanceUntilIdle()

        assertTrue(erkenner.gestoppt)
        assertEquals(Aufnahmezustand.Wandelt, modell.zustand.value.aufnahme)
    }

    @Test
    fun `erneute erkennung ersetzt den text des eintrags`() = pruefe {
        advanceUntilIdle()
        modell.aufnahmeUmschalten()
        advanceUntilIdle()
        erkenner.sende(Erkennungsereignis.Ergebnis("erster Versuch"))
        advanceUntilIdle()
        val id = modell.zustand.value.diktate.first().id

        modell.erneutErkennen(id)
        advanceUntilIdle()
        erkenner.sende(Erkennungsereignis.Ergebnis("zweiter Versuch"))
        advanceUntilIdle()

        assertEquals(1, modell.zustand.value.diktate.size)
        assertEquals("zweiter Versuch", modell.zustand.value.diktate.first().text)
    }

    @Test
    fun `bausteine lassen sich sichern und loeschen`() = pruefe {
        advanceUntilIdle()
        modell.sichereBaustein(
            de.ithandwerkstuttgart.loqui.ui.modell.Textbaustein("", "adr", "Musterstraße 1")
        )
        advanceUntilIdle()
        val gesichert = modell.zustand.value.textbausteine.single()
        assertEquals("adr", gesichert.kuerzel)

        modell.loescheBaustein(gesichert)
        advanceUntilIdle()
        assertTrue(modell.zustand.value.textbausteine.isEmpty())
    }
}
