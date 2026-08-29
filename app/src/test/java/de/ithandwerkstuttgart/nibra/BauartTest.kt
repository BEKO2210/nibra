package de.ithandwerkstuttgart.nibra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Prüfungen an der Bauart, nicht am Verhalten.
 *
 * Jeder Test hier steht für einen Fehler, der uns an einem einzigen Tag
 * echte Stunden gekostet hat -- und der sich **nur** am Aufbau erkennen
 * lässt, nicht an einem Ablauf. Ein Verhaltenstest hätte keinen davon
 * gefunden: die Fehler zeigten sich erst auf dem Gerät, im Zusammenspiel
 * mit dem Systemdienst von Android.
 *
 * Sie sind bewusst Quelltextprüfungen. Wer die Regel bricht, bricht den
 * Bau -- nicht erst die Fassung beim Nutzer.
 */
class BauartTest {

    /**
     * **Der teuerste Fehler des Tages.** Vier Stellen legten je einen
     * eigenen `SpeechRecognizer` an, alle im selben Prozess. Ein solcher
     * Erkenner hält eine Bindung an einen Systemdienst; mehrere gleichzeitig
     * blockieren sich, und der zweite bekommt **keine Antwort** -- weder
     * Ergebnis noch Fehler. Das Diktat versagte, die Sprachliste hing.
     */
    @Test
    fun `nur der erkennerhalter legt einen spracherkenner an`() {
        val erlaubt = "Erkennerhalter.kt"
        val sünder = quelldateien().filter { datei ->
            datei.name != erlaubt &&
                Regex("""SpeechRecognizer\.create\w*SpeechRecognizer\(""")
                    .containsMatchIn(datei.readText())
        }
        assertTrue(
            "Diese Dateien legen selbst einen SpeechRecognizer an: " +
                sünder.joinToString { it.name } +
                ". Es darf genau einen je Prozess geben, und den vergibt " +
                "$erlaubt. Mehrere blockieren sich gegenseitig, ohne dass " +
                "Android das meldet.",
            sünder.isEmpty()
        )
    }

    /**
     * Seit Android 11 sieht eine App fremde Pakete nur, wenn sie den Bedarf
     * erklärt. Ohne diesen Block blockte der `AppsFilter` die Verbindung
     * zum Erkennungsdienst **kommentarlos**, und das Diktat meldete
     * `RECOGNIZER_BUSY` -- als wäre besetzt. Der Block fehlte vom ersten
     * Tag an und war von außen nicht zu sehen.
     */
    @Test
    fun `das manifest erklaert den bedarf an erkennungsdiensten`() {
        val manifest = File(wurzel(), "app/src/main/AndroidManifest.xml").readText()
        assertTrue(
            "Im Manifest fehlt <queries> für android.speech.RecognitionService. " +
                "Ohne diese Erklärung blockiert Android ab Version 11 die " +
                "Verbindung zum Erkennungsdienst, ohne es zu melden.",
            manifest.contains("<queries>") &&
                manifest.contains("android.speech.RecognitionService")
        )
    }

    /**
     * Vorübergehende Störungen dürfen nicht als Urteil über das Gerät
     * erscheinen. „Dieses Gerät kann Sprache nicht auf dem Gerät erkennen"
     * stand nach **zwei erkannten Sätzen** auf dem Bildschirm -- beweisbar
     * falsch, und für den Nutzer eine Sackgasse statt eines zweiten
     * Versuchs.
     */
    @Test
    fun `voruebergehende stoerungen gelten nicht als geraeteunfaehigkeit`() {
        val quelle = quelldateien().first { it.name == "Spracherkenner.kt" }.readText()
        val zuordnung = quelle.substringAfter("fun fehlerartAus(")
        listOf("ERROR_RECOGNIZER_BUSY", "ERROR_SERVER_DISCONNECTED", "ERROR_CLIENT")
            .forEach { code ->
                // Nur bis zum Pfeil lesen: ein `when`-Zweig endet dort. Der
                // erste Wurf dieses Tests nahm vier Zeilen und fand dadurch
                // die Zuordnung des **nächsten** Zweigs -- ein Test, der aus
                // Unschärfe Alarm schlägt, ist so wertlos wie einer, der
                // nichts findet.
                val zweig = zuordnung.lineSequence()
                    .dropWhile { !it.contains(code) }
                    .takeWhile { !it.contains("->") || it.contains(code) }
                    .plus(
                        zuordnung.lineSequence()
                            .dropWhile { !it.contains(code) }
                            .firstOrNull { it.contains("->") } ?: ""
                    )
                    .joinToString(" ")
                assertTrue(
                    "$code darf nicht als ERKENNUNG_NICHT_VERFUEGBAR gelten -- " +
                        "das behauptet, das Gerät könne grundsätzlich keine " +
                        "Sprache erkennen. Es ist eine vorübergehende Störung. " +
                        "Gefunden: $zweig",
                    !zweig.contains("ERKENNUNG_NICHT_VERFUEGBAR")
                )
            }
    }

    /**
     * Ein Rückruf, der ausbleiben kann, braucht einen Ausgang. Zweimal
     * hing die App an genau dieser Stelle: einmal endlos auf „wandelt",
     * einmal blieb der Erkenner für immer verliehen, weil
     * `checkRecognitionSupport` auf dem S23 Ultra nie antwortete.
     */
    @Test
    fun `die sprachabfrage gibt den erkenner in jedem fall zurueck`() {
        val quelle = quelldateien().first { it.name == "Sprachverzeichnis.kt" }.readText()
        assertTrue(
            "Die Sprachabfrage braucht ein finally, das den Erkenner " +
                "zurückgibt. Auf dem S23 Ultra antwortet " +
                "checkRecognitionSupport nie -- ohne finally bleibt der " +
                "eine Erkenner des Prozesses für immer verliehen und jedes " +
                "Diktat danach bekommt eine Absage.",
            quelle.contains("finally") && quelle.contains("gibZurueck")
        )
    }

    /**
     * Das Protokoll ist das einzige Werkzeug, mit dem sich diese Fehler
     * finden ließen. Es darf aber niemals aufzeichnen, **was** jemand
     * diktiert hat: ein Diktat kann alles enthalten, und ein Protokoll
     * überlebt die App.
     */
    @Test
    fun `das protokoll schreibt niemals gesprochenen inhalt`() {
        val quelle = quelldateien().first { it.name == "Spracherkenner.kt" }.readText()
        // Groß- und Kleinschreibung egal, und auch Aufrufe erwischen, die
        // den Inhalt erst über eine Funktion holen. Der erste Wurf dieses
        // Tests ließ `ersterText(...)` durch -- eine Prüfung, die den
        // eigenen Gegenversuch nicht fängt, prüft nichts.
        // Feste Zeichenketten sind harmlos: „lesarten=2" ist eine Anzahl,
        // kein Inhalt. Verdächtig ist nur, was als **Wert** hineingeht.
        // Deshalb erst die Zeichenketten entfernen, dann suchen -- der
        // erste Wurf schlug bei „lesarten=" an und der zweite ließ
        // `ersterText(...)` durch. Beide Male prüfte der Test etwas
        // anderes als die Regel.
        // Auf die **konkreten Textquellen** prüfen, nicht auf Wörter.
        // Zwei Anläufe scheiterten daran, dass sie Zeichenketten und
        // Werte nicht auseinanderhalten konnten: „lesarten=2" ist eine
        // Anzahl, `ersterText(...)` ist der Satz selbst. Wer die Regel
        // prüfen will, muss sie benennen können.
        val textquellen = listOf("ersterText(", ".text", "lesarten[")
        val verdächtig = Regex("""Erkennungsprotokoll\.\w+\(([^)]*)\)""")
            .findAll(quelle)
            .map { it.groupValues[1] }
            .filter { aufruf -> textquellen.any { aufruf.contains(it) } }
            .toList()
        assertTrue(
            "Diese Protokollaufrufe könnten gesprochenen Inhalt schreiben: " +
                verdächtig.joinToString() +
                ". Erlaubt sind nur Namen, Zeiten, Fehlercodes und Anzahlen.",
            verdächtig.isEmpty()
        )
    }

    /**
     * Die Auslieferung darf keine Forschungsklasse mitbauen. Der Compiler
     * würde es zwar melden, aber erst beim Bau der falschen Ausprägung --
     * dieser Test sagt es sofort und mit Begründung.
     */
    @Test
    fun `die auslieferung kennt keine forschungsklassen`() {
        val forschung = File(wurzel(), "app/src/forschung/java").walkTopDown()
            .filter { it.extension == "kt" }
            .map { it.nameWithoutExtension }
            .toSet()
        assertTrue("Forschungsklassen nicht gefunden", forschung.isNotEmpty())
        val sünder = quelldateien().filter { datei ->
            val text = datei.readText()
            forschung.any { name -> Regex("""\b$name\b""").containsMatchIn(text) }
        }
        assertTrue(
            "Auslieferungscode verweist auf Forschungsklassen: " +
                sünder.joinToString { it.name },
            sünder.isEmpty()
        )
    }

    /** Der Auslieferungscode darf nichts aus dem Netz holen. */
    @Test
    fun `die auslieferung enthaelt keinen netzcode`() {
        val verboten = listOf("java.net.", "okhttp", "HttpURLConnection", "retrofit")
        val sünder = quelldateien().flatMap { datei ->
            val text = datei.readText()
            verboten.filter { text.contains(it) }.map { datei.name to it }
        }
        assertTrue(
            "Netzzugriff im Auslieferungscode: " +
                sünder.joinToString { "${it.second} in ${it.first}" },
            sünder.isEmpty()
        )
    }

    /** Die Fassungsnummer wird bei jeder Abgabe hochgezählt. */
    @Test
    fun `die fassung ist ueber eins punkt null hinaus`() {
        val gradle = File(wurzel(), "app/build.gradle.kts").readText()
        val code = Regex("""versionCode = (\d+)""").find(gradle)?.groupValues?.get(1)?.toInt()
        assertNotNull("versionCode nicht gefunden", code)
        assertTrue(
            "versionCode steht bei $code. Ohne Hochzählen lässt sich am " +
                "Gerät nicht erkennen, welcher Stand läuft -- und ein Test " +
                "gegen eine unbekannte Fassung ist kein Test.",
            code!! > 1
        )
    }

    private fun wurzel(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "app/src/main/java").isDirectory }
        ?: error("Projektwurzel nicht gefunden")

    private fun quelldateien(): List<File> {
        val dateien = File(wurzel(), "app/src/main/java").walkTopDown()
            .filter { it.extension == "kt" }.toList()
        assertTrue("Keine Quelldateien gefunden", dateien.size > 10)
        return dateien
    }

    /** Sicherheitsnetz gegen einen leeren Suchpfad -- sonst prüft nichts. */
    @Test
    fun `die pruefungen finden ueberhaupt quelldateien`() {
        assertEquals(
            "Die Bauartprüfungen müssen im Auslieferungscode suchen",
            true,
            quelldateien().any { it.name == "Spracherkenner.kt" }
        )
    }

    /**
     * Vorlaeufiger und bestaetigter Text muessen auf dem Bildschirm
     * unterscheidbar sein.
     *
     * Der laufende Satz wird vom Erkenner staendig umgeschrieben, der
     * fertige nie wieder. Steht beides gleich da, liest der Nutzer jede
     * Korrektur als Fehler der App -- und traut dem, was er sieht, nicht
     * mehr. Diese Regel steht hier, weil die Versuchung gross ist, beim
     * naechsten Umbau wieder einen einzigen String anzuzeigen: das ist
     * kuerzer und sieht im Code sauberer aus.
     *
     * Farbe allein reicht nicht. Wer sie nicht unterscheiden kann, braucht
     * dieselbe Auskunft in der Beschreibung fuer die Sprachausgabe --
     * deshalb wird auch die verlangt.
     */
    @Test
    fun `der bildschirm trennt vorlaeufigen von bestaetigtem text`() {
        val quelle = quelldateien().first { it.name == "AufnahmeBildschirm.kt" }.readText()
        assertTrue(
            "Der bestaetigte Text muss eigens gezeichnet werden",
            quelle.contains("zustand.festerText")
        )
        assertTrue(
            "Der vorlaeufige Text muss eigens gezeichnet werden",
            quelle.contains("zustand.teiltext")
        )
        assertTrue(
            "Die Unterscheidung muss auch fuer die Sprachausgabe da sein",
            quelle.contains("sw_aufnahme_text_bestaetigt") &&
                quelle.contains("sw_aufnahme_text_vorlaeufig")
        )
    }

    /**
     * Gegenprobe zur vorigen Regel: eine Fassung, die nur den
     * zusammengesetzten Text anzeigt, muss durchfallen. Ohne diese Probe
     * wuesste niemand, ob die Regel ueberhaupt etwas prueft.
     */
    @Test
    fun `gegenprobe -- ein zusammengesetzter text allein faellt durch`() {
        val erfunden = """
            Text(
                text = zustand.sichtbarerText,
                style = MaterialTheme.typography.bodyLarge
            )
        """.trimIndent()
        assertFalse(
            "Diese Fassung darf die Regel nicht bestehen",
            erfunden.contains("zustand.festerText") &&
                erfunden.contains("zustand.teiltext")
        )
    }


    /**
     * Der laufende Satz darf die Sprachausgabe nicht zum Dauerfeuer machen.
     *
     * Zwischenmeldungen kommen mehrmals je Sekunde. Stuenden bestaetigter
     * und vorlaeufiger Text in **einem** Knoten, laese TalkBack bei jeder
     * Aenderung den ganzen Satz neu vor -- samt allem, was laengst
     * feststeht. Wer auf die Sprachausgabe angewiesen ist, koennte der App
     * dann nicht folgen.
     *
     * Zwei Vorkehrungen, beide hier festgehalten:
     * - getrennte Textknoten, damit der bestaetigte Teil ruhig bleibt
     * - **kein** liveRegion auf dem vorlaeufigen Teil, damit seine
     *   Aenderungen nicht von selbst angesagt werden
     */
    @Test
    fun `der laufende satz wird nicht staendig angesagt`() {
        val quelle = quelldateien().first { it.name == "AufnahmeBildschirm.kt" }.readText()
        // Auf die Zuweisung geprueft, nicht auf das blosse Wort: im Code
        // steht ein Kommentar, der erklaert, warum es *keine* gibt. Eine
        // Suche nach dem Wort allein waere an genau dieser Erklaerung
        // haengen geblieben -- eine Regel, die den Hinweis auf sich selbst
        // als Verstoss zaehlt, ist wertlos.
        assertFalse(
            "Kein liveRegion auf dem Aufnahmebildschirm -- das waere Dauerfeuer",
            quelle.contains("liveRegion =") || quelle.contains("LiveRegionMode")
        )
        // Zwei getrennte Bedingungen statt einer gemeinsamen: genau daran
        // haengt, dass es zwei Knoten sind und nicht einer.
        assertTrue(
            "Bestaetigter und vorlaeufiger Text brauchen eigene Bedingungen",
            quelle.contains("if (zustand.festerText.isNotBlank())") &&
                quelle.contains("if (zustand.teiltext.isNotBlank())")
        )
    }

    /**
     * Vorlaeufiger Text darf sichtbar unsicher sein, aber nie wie
     * abgeschalteter oder unwichtiger Text wirken -- er ist beim Sprechen
     * das Einzige, was der Nutzer liest.
     *
     * Deshalb dieselbe Farbstaerke wie der bestaetigte Text und ein
     * Merkmal, das ohne Farbe erkennbar ist.
     */
    @Test
    fun `der vorlaeufige text wird nicht abgedunkelt`() {
        val quelle = quelldateien().first { it.name == "AufnahmeBildschirm.kt" }.readText()
        val abschnitt = quelle.substringAfter("if (zustand.teiltext.isNotBlank())")
            .substringBefore("stilleErkannt")
        assertFalse(
            "Der laufende Satz darf nicht in der leisen Farbe stehen",
            abschnitt.contains("onSurfaceVariant")
        )
        assertTrue(
            "Er braucht ein Merkmal, das auch ohne Farbe zu sehen ist",
            abschnitt.contains("TextDecoration.Underline")
        )
    }


    /**
     * Ein Diktat darf nicht beginnen, wenn bekannt ist, dass das
     * Sprachpaket fehlt.
     *
     * Vorher lief das Diktat an, nahm sechs Sekunden auf und meldete erst
     * hinterher, dass etwas fehlt -- und auch das nur, wenn der Dienst eine
     * Stoerung meldete. Auf dem Pixel 9 kam nicht einmal die: dort liegt
     * nur en-US auf dem Geraet, Nibra fragt de-DE ohne Netz, und der
     * Dienst schweigt. Der Nutzer sah eine App, die zuhoert und nichts
     * versteht.
     *
     * Die Pruefung muss **vor** dem Wechsel in den laufenden Zustand
     * stehen, sonst zeigt der Bildschirm erst „hoert zu" und widerruft es
     * gleich wieder.
     */
    @Test
    fun `ohne sprachpaket beginnt kein diktat`() {
        val quelle = quelldateien().first { it.name == "NibraViewModel.kt" }.readText()
        val start = quelle.substringAfter("private fun starteAufnahme(")
        val pruefung = start.indexOf("!gewaehlteSprache.aufGeraetVerfuegbar")
        val laeuft = start.indexOf("Aufnahmezustand.Laeuft(")
        assertTrue("Die Pruefung auf das Sprachpaket fehlt", pruefung >= 0)
        assertTrue(
            "Die Pruefung muss vor dem Wechsel in den laufenden Zustand stehen",
            pruefung < laeuft
        )
        assertTrue(
            "Es muss auch etwas dagegen unternommen werden",
            start.substring(0, laeuft).contains("ladeSprachpaket(code)")
        )
    }

    /**
     * Gegenprobe: eine Fassung, die erst startet und hinterher prueft,
     * faellt durch. Sonst pruefte die Regel nur, dass irgendwo im
     * Quelltext das Wort vorkommt.
     */
    @Test
    fun `gegenprobe -- pruefung nach dem start faellt durch`() {
        val erfunden = """
            _zustand.update { it.copy(aufnahme = Aufnahmezustand.Laeuft()) }
            if (!gewaehlteSprache.aufGeraetVerfuegbar) { ladeSprachpaket(code) }
        """.trimIndent()
        assertFalse(
            "Diese Reihenfolge darf die Regel nicht bestehen",
            erfunden.indexOf("!gewaehlteSprache.aufGeraetVerfuegbar") <
                erfunden.indexOf("Aufnahmezustand.Laeuft(")
        )
    }


    /**
     * Der Bedienungshilfen-Dienst muss erklaeren, was er ist, und die App
     * muss aufklaeren, bevor er eingeschaltet wird.
     *
     * Google lehnt Apps ab, die die Bedienungshilfen-Schnittstelle fuer
     * eine gewoehnliche Funktion nutzen, ohne den Nutzer vorher
     * aufzuklaeren. Nibra faellt in diese Gruppe: die Blase ist kein
     * Hilfsmittel fuer Menschen mit Behinderung, sondern eine Funktion fuer
     * alle. Beides -- die Erklaerung und die Aufklaerung -- ist damit
     * Bedingung dafuer, dass die Blase ueberhaupt ausgeliefert werden darf.
     */
    @Test
    fun `der bedienungshilfen-dienst erklaert sich und klaert auf`() {
        val konfig = java.io.File("src/main/res/xml/dictation_accessibility_service.xml").readText()
        assertTrue(
            "isAccessibilityTool muss ausdruecklich dastehen, nicht fehlen",
            konfig.contains("android:isAccessibilityTool=")
        )
        val texte = java.io.File("src/main/res/values/strings.xml").readText()
        assertTrue(
            "Die Offenlegung fuer die Systemeinstellungen fehlt",
            texte.contains("sw_bedienungshilfen_offenlegung")
        )
        assertTrue(
            "Die Aufklaerung in der App selbst fehlt",
            texte.contains("sw_einrichtung_dienst_offenlegung")
        )
        val einrichtung = quelldateien().first { it.name == "EinrichtungBildschirm.kt" }.readText()
        assertTrue(
            "Die Aufklaerung muss vor dem Knopf zum Einschalten stehen",
            einrichtung.indexOf("sw_einrichtung_dienst_offenlegung") <
                einrichtung.indexOf("sw_einrichtung_dienst_handlung")
        )
    }


    /**
     * Der Store-Auftritt darf nicht der Vorlage gehören.
     *
     * Unter `app/src/main/play/` lag die vollständige Store-Aufmachung von
     * **AIDictation**: deren Titel, deren Logo, deren Bildschirmfotos mit
     * dem Spruch „The Most Intelligent Voice Typing Tool" -- und deren
     * Anschrift. `contact-email.txt` nannte support@aidictation.com,
     * `privacy-policy.txt` zeigte auf aidictation.com/privacy.
     *
     * Die MIT-Lizenz erlaubt, den Code zu verwenden. Sie erlaubt nicht,
     * unter dem Namen und mit den Bildern des anderen Projekts
     * aufzutreten -- und schon gar nicht, zahlende Kundschaft an dessen
     * Postfach zu schicken.
     *
     * Nibras eigenes Store-Material liegt unter `store/`. Nennungen der
     * Vorlage in der Lizenzangabe und auf dem Fremdsoftware-Bildschirm
     * bleiben davon unberührt: die gehören dorthin.
     */
    @Test
    fun `kein fremder store-auftritt im quellbaum`() {
        val play = File("src/main/play")
        assertFalse(
            "app/src/main/play enthält die Store-Aufmachung der Vorlage und darf nicht wieder auftauchen",
            play.exists()
        )
        val verdaechtig = File("src/main")
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("txt", "json") }
            .filter { it.path.contains("raw/lizenzen").not() }
            .filter { it.readText().contains("aidictation.com") }
            .toList()
        assertTrue(
            "Diese Dateien nennen die Anschrift der Vorlage: $verdaechtig",
            verdaechtig.isEmpty()
        )
    }

    /**
     * Gegenprobe: die Regel muss anschlagen, wenn genau das wiederkäme.
     * Ohne sie prüfte sie womöglich nur, dass ein Ordner fehlt.
     */
    @Test
    fun `gegenprobe -- fremde anschrift wird erkannt`() {
        val probe = kotlin.io.path.createTempDirectory("bauart").toFile()
        val datei = File(probe, "contact-email.txt")
        datei.writeText("support@aidictation.com\n")
        val treffer = probe.walkTopDown()
            .filter { it.isFile && it.extension == "txt" }
            .filter { it.readText().contains("aidictation.com") }
            .toList()
        assertTrue("Die Suche muss die fremde Anschrift finden", treffer.size == 1)
        probe.deleteRecursively()
    }

    /**
     * **Kein Rückfall auf einen Erkenner, der ins Netz gehen darf.**
     *
     * Nibra wird damit beworben, dass nichts das Telefon verlässt. Die App
     * hat keine INTERNET-Berechtigung, also kann sie selbst nichts senden.
     * Sie kann den Ton aber an den System-Erkenner geben, und der darf.
     *
     * `EXTRA_PREFER_OFFLINE` sieht wie eine Zusage aus und ist keine:
     * "bevorzugt" heisst, dass er ohne Offline-Modell online geht. Genau so
     * stand es hier, und auf Android 8 bis 12 war es der einzige Weg.
     *
     * Seit Fassung 2.2 gibt es nur noch `createOnDeviceSpeechRecognizer`,
     * und minSdk ist 33 -- Play bietet die App nur Geräten an, auf denen
     * das geht. Steht kein Geräte-Erkenner bereit, sagt die App das, bevor
     * jemand spricht.
     */
    @Test
    fun `die auslieferung nutzt nur den geraete-erkenner`() {
        val quellen = File("src/main/java")
            .walkTopDown().filter { it.isFile && it.extension == "kt" }
        val verdaechtig = quellen.filter { datei ->
            val text = datei.readText()
            // Der blosse Name in einem Kommentar ist kein Aufruf.
            Regex("""SpeechRecognizer\.createSpeechRecognizer\s*\(""").containsMatchIn(text) ||
                Regex("""SpeechRecognizer\.isRecognitionAvailable\s*\(""").containsMatchIn(text)
        }.toList()
        assertTrue(
            "Diese Dateien greifen auf einen Erkenner zu, der ins Netz gehen darf: " +
                verdaechtig.map { it.name },
            verdaechtig.isEmpty()
        )
    }

    /**
     * Gegenprobe: die alte Fassung muss durchfallen. Ohne sie prüfte die
     * Regel womöglich nur, dass eine Zeichenkette zufällig fehlt.
     */
    @Test
    fun `gegenprobe -- der alte rueckfall wird erkannt`() {
        val alt = """
            private fun baue(): SpeechRecognizer? = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            }.getOrNull()
        """.trimIndent()
        assertTrue(
            "Die Regel muss auf genau dieses Muster anschlagen",
            Regex("""SpeechRecognizer\.createSpeechRecognizer\s*\(""").containsMatchIn(alt)
        )
    }

    /**
     * minSdk darf nicht wieder unter 33 fallen: darunter gibt es
     * `isOnDeviceRecognitionAvailable` nicht, und die App wäre auf jenen
     * Geräten installierbar, aber arbeitsunfähig.
     */
    @Test
    fun `minsdk bleibt bei mindestens 33`() {
        val bau = File("build.gradle.kts").readText()
        val treffer = Regex("""minSdk\s*=\s*(\d+)""").find(bau)
        assertNotNull("minSdk steht nicht im Baubuch", treffer)
        val wert = treffer!!.groupValues[1].toInt()
        assertTrue("minSdk ist $wert, nötig sind mindestens 33", wert >= 33)
    }
}
