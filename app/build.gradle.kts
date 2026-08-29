import java.util.Properties
import java.io.FileInputStream
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(FileInputStream(f))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    signingConfigs {
        if (rootProject.file("keystore.properties").exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    namespace = "de.ithandwerkstuttgart.nibra"
    // Die neuen AndroidX-Bibliotheken verlangen es. targetSdk bleibt bei 36:
    // dagegen wird geprueft, was Play akzeptiert.
    compileSdk = 37

    defaultConfig {
        applicationId = "de.ithandwerkstuttgart.nibra"
        minSdk = 26
        targetSdk = 36
        // Wird bei jeder Abgabe hochgezählt. Ohne das lässt sich am Gerät
        // nicht erkennen, welcher Stand gerade läuft -- und ein Test gegen
        // eine unbekannte Fassung ist kein Test.
        versionCode = 12
        versionName = "2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Zwei Auspraegungen, damit die Netzfreiheit nicht von Disziplin abhaengt,
    // sondern vom Bauplan.
    //
    // `offline`   -- was in den Laden geht. Das Manifest in `src/main` kennt
    //                keine INTERNET-Berechtigung, und es gibt keinen Ort, an
    //                dem diese Auspraegung eine bekaeme.
    // `forschung` -- nur zum Messen. Darf ins Netz, traegt einen eigenen
    //                Paketnamen und laesst sich damit neben der echten App
    //                installieren, ohne sie zu ueberschreiben.
    //
    // Cloud-Code gehoert ausschliesslich nach `src/forschung`. Was dort
    // liegt, kann in `offline` nicht einmal versehentlich landen -- es wird
    // fuer diese Auspraegung gar nicht uebersetzt.
    // Lint ist ein Gate, kein Vorschlag.
    //
    // Es hat an einem Tag vier echte Fehler gefunden, die niemand gesehen
    // hatte: Aufrufe, die auf Android 8 bis 12 abgestürzt wären, weil die
    // Versionsprüfung über ein Feld hinweg stand. Ein Prüfer, dessen
    // Ergebnis man wegklicken kann, prüft nichts.
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        sarifReport = true
    }

    flavorDimensions += "netz"
    productFlavors {
        create("offline") {
            dimension = "netz"
            isDefault = true
        }
        create("forschung") {
            dimension = "netz"
            applicationIdSuffix = ".forschung"
            versionNameSuffix = "-forschung"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            // Ohne das bleiben die Ressourcen des entfernten Codes in der
            // APK liegen -- Lint meldet das zu Recht als Fehler.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // Fuer die Fassungsnummer im Markenfuss.
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room -- lokaler Verlauf und Textbausteine, kein Netz
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore -- lokale Einstellungen
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // Nur für die Forschungsausprägung: der Messplatz wird über eine
    // Instrumentierung gestartet, damit seine Aktivität nicht exportiert
    // sein muss. Die Auslieferung bekommt davon nichts.
    "androidTestForschungImplementation"("androidx.test:runner:1.6.2")
    "androidTestForschungImplementation"("androidx.test.ext:junit:1.2.1")
    // Liest den sichtbaren Text aus der Oberflächenhierarchie zurück --
    // unabhängig davon, was die App über ihren eigenen Rückkanal meldet.
    "androidTestForschungImplementation"("androidx.test.uiautomator:uiautomator:2.3.0")
}

// AGP 9 kennt `kotlinOptions` nicht mehr; das Ziel steht jetzt hier.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/**
 * Prueft, dass die Auslieferungsfassung keine Netzberechtigung traegt.
 *
 * Das Datenschutzversprechen der App -- "Nibra fordert kein Recht auf
 * Netzzugang an" -- steht im Datenschutz-Bildschirm in sieben Sprachen, im
 * README und in AUFTRAG.md. Es soll nicht an Disziplin haengen, sondern
 * nachweisbar sein.
 *
 * Diese Pruefung liest die uebersetzte Manifestdatei der Auspraegung
 * `offline` und bricht ab, sobald dort INTERNET oder ACCESS_NETWORK_STATE
 * auftaucht -- gleich ob von Hand eingetragen oder von einer Bibliothek
 * mitgebracht, denn genau das ist der wahrscheinlichere Fall.
 *
 * Aufruf: ./gradlew pruefeNetzfreiheit
 */
val pruefeNetzfreiheit by tasks.registering {
    group = "verification"
    description = "Bricht ab, wenn die Offline-Auspraegung eine Netzberechtigung traegt."

    dependsOn("processOfflineReleaseManifest", "processOfflineDebugManifest")

    doLast {
        val verboten = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"
        )
        val manifeste = layout.buildDirectory.get().asFile
            .walkTopDown()
            .filter { it.name == "AndroidManifest.xml" }
            .filter { it.path.contains("offline", ignoreCase = true) }
            .toList()

        if (manifeste.isEmpty()) {
            throw GradleException(
                "Kein uebersetztes Manifest der Offline-Auspraegung gefunden -- " +
                    "die Pruefung haette nichts geprueft und darf nicht still bestehen."
            )
        }

        val treffer = manifeste.flatMap { datei ->
            val inhalt = datei.readText()
            verboten.filter { inhalt.contains(it) }.map { "$it in ${datei.path}" }
        }

        if (treffer.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Die Offline-Auspraegung traegt eine Netzberechtigung:")
                    treffer.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("Das bricht die Zusage aus dem Datenschutz-Bildschirm.")
                    appendLine("Cloud-Code gehoert nach src/forschung, nicht nach src/main.")
                }
            )
        }
        logger.lifecycle(
            "Netzfreiheit belegt: ${manifeste.size} Manifest(e) der " +
                "Offline-Auspraegung, keine Netzberechtigung."
        )
    }
}

/**
 * Die Forschungsausprägung darf niemals in einer Auslieferung landen.
 *
 * Sie trägt INTERNET und ACCESS_NETWORK_STATE, nimmt rohes PCM auf und legt
 * Messberichte auf die Ablage. Alles davon ist für die Messung nötig und in
 * einer veröffentlichten App ein Bruch mit allem, was Nibra verspricht.
 *
 * **Der erste Riegel taugte nichts.** Er hängte eine Ausnahme an
 * `assembleForschungRelease` -- eine reine Sammelaufgabe, die als
 * **letzte** läuft. Wenn sie zuschlug, lag die signierte APK längst unter
 * `app/build/outputs/apk/forschung/release/`. Der Bau war rot, das Erzeugnis
 * war da. Und `packageForschungRelease` oder `signForschungReleaseBundle`
 * einzeln aufzurufen ging ganz ohne Warnung durch.
 *
 * Geprüft wurde damals die Fehlermeldung statt der Wirkung -- derselbe
 * Fehler, den MESSSYSTEM.md neun Mal beschreibt.
 *
 * Jetzt wird die Variante gar nicht erst gebaut: `beforeVariants` schaltet
 * sie ab, bevor irgendeine ihrer Aufgaben entsteht. Es gibt dann kein
 * `assembleForschungRelease`, kein `packageForschungRelease` und kein
 * signiertes Erzeugnis, das jemand einsammeln könnte.
 */
androidComponents {
    beforeVariants(selector().withFlavor("netz" to "forschung").withBuildType("release")) {
        it.enable = false
    }
}

/**
 * Frühwarnung bei Lizenzänderungen.
 *
 * **Das ist keine Rechtsberatung und macht nichts rechtssicher.** Es ist ein
 * technisches Tor, das anschlägt, wenn eine neue Abhängigkeit mit anderen
 * Bedingungen in die Auslieferung gerät -- damit das nicht unbemerkt
 * geschieht und erst im Store auffällt.
 *
 * Geprüft wird der Klassenpfad der Auslieferung, nicht der Testpfad: was
 * nur beim Prüfen gebraucht wird, wird nicht ausgeliefert und braucht keine
 * Nennung.
 */
val kopfeLizenzen = listOf(
    "The Apache Software License, Version 2.0",
    "The Apache License, Version 2.0",
    "Apache License, Version 2.0",
    "Apache 2.0",
    "Apache-2.0",
    "The MIT License",
    "MIT License",
    "SIL Open Font License 1.1",
    "The 2-Clause BSD License",
    "The 3-Clause BSD License",
)

/** Was sofort auffallen muss, weil es die Auslieferung beträfe. */
val heikleLizenzen = listOf("GPL", "AGPL", "LGPL", "SSPL", "CDDL", "MPL", "EPL", "CPL")

/**
 * Pakete ohne Lizenzangabe, deren Lizenz belegt ist.
 *
 * **Jede Ausnahme braucht einen Grund.** Eine stille Freiliste würde genau
 * das verbergen, wovor dieses Tor warnen soll. Steht hier etwas, hat jemand
 * nachgesehen und den Beleg notiert.
 */
val belegteAusnahmen = mapOf(
    "com.google.guava:listenablefuture" to
        "Platzhalterpaket ohne eigenen Code, das nur eine Abhängigkeit auflöst. " +
        "Guava steht unter Apache-2.0; das Paket führt keine eigene Angabe."
)

tasks.register("pruefeLizenzen") {
    group = "verification"
    description = "Warnt, wenn eine ausgelieferte Abhängigkeit unerwartete Lizenzbedingungen hat"

    doLast {
        val konfiguration = configurations.findByName("offlineReleaseRuntimeClasspath")
            ?: error("offlineReleaseRuntimeClasspath gibt es nicht")

        val teile = konfiguration.incoming.resolutionResult.allComponents
            .mapNotNull { it.moduleVersion }
            // Das eigene Modul ist keine Fremdsoftware.
            .filter { it.group.isNotBlank() && it.group != rootProject.name }
            .map { "${it.group}:${it.name}" }
            .distinct()
            .sorted()

        val beanstandet = mutableListOf<String>()
        val unbekannt = mutableListOf<String>()
        val heikel = mutableListOf<String>()
        val gefunden = mutableMapOf<String, String>()

        // Die Lizenz steht in der Paketbeschreibung, nicht in einer
        // gepflegten Liste -- eine Liste veraltet still.
        //
        // **Die Beschreibungen kommen über Gradles eigene Auflösung, nicht
        // aus einem geratenen Ordner.** Der erste Wurf durchsuchte
        // ~/.gradle/caches/modules-2. Auf dem CI-Läufer fand er dort keine
        // einzige Beschreibung und meldete alle 146 Abhängigkeiten als
        // "ohne Lizenzangabe" -- es sah aus wie ein Lizenzproblem und war
        // ein Pfadfehler. Wo der Zwischenspeicher liegt, weiß Gradle; wir
        // müssen es nicht wissen.
        val beschreibungen = mutableMapOf<String, File>()
        konfiguration.incoming.resolutionResult.allComponents
            .mapNotNull { bauteil -> bauteil.moduleVersion?.let { bauteil.id to it } }
            .filter { (_, fassung) -> fassung.group.isNotBlank() && fassung.group != rootProject.name }
            .chunked(50)
            .forEach { haufen ->
                dependencies.createArtifactResolutionQuery()
                    .forComponents(haufen.map { it.first })
                    .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
                    .execute()
                    .resolvedComponents
                    .forEach { gelöst ->
                        val name = gelöst.id.displayName.substringBeforeLast(":")
                        gelöst.getArtifacts(MavenPomArtifact::class.java)
                            .filterIsInstance<ResolvedArtifactResult>()
                            .firstOrNull()
                            ?.let { beschreibungen[name] = it.file }
                    }
            }
        teile.forEach { teil ->
            belegteAusnahmen[teil]?.let { grund ->
                gefunden[teil] = "Ausnahme mit Beleg"
                println("    Ausnahme: $teil -- $grund")
                return@forEach
            }
            val pom = beschreibungen[teil]
            if (pom == null || !pom.exists()) {
                unbekannt += "$teil (keine Paketbeschreibung gefunden)"
                return@forEach
            }
            val text = pom.readText()
            val namen = Regex("<licenses>.*?</licenses>", RegexOption.DOT_MATCHES_ALL)
                .find(text)?.value
                ?.let { Regex("<name>([^<]+)</name>").findAll(it).map { m -> m.groupValues[1].trim() }.toList() }
                ?: emptyList()
            if (namen.isEmpty()) {
                unbekannt += "$teil (keine Lizenzangabe im Paket)"
                return@forEach
            }
            gefunden[teil] = namen.joinToString(", ")
            namen.forEach { name ->
                if (heikleLizenzen.any { name.contains(it, ignoreCase = true) }) {
                    heikel += "$teil -> $name"
                } else if (kopfeLizenzen.none { name.equals(it, ignoreCase = true) }) {
                    beanstandet += "$teil -> $name"
                }
            }
        }

        // Die mitgelieferte Aufstellung muss zur Wirklichkeit passen.
        val aufstellung = File(projectDir, "src/main/res/raw/lizenzen.txt")
        val fehlen = if (aufstellung.exists()) {
            val inhalt = aufstellung.readText()
            teile.filterNot { inhalt.contains(it) }
        } else {
            listOf("die Datei src/main/res/raw/lizenzen.txt fehlt ganz")
        }

        val schriften = File(projectDir, "src/main/res/font")
            .listFiles { _, n -> n.endsWith(".ttf") || n.endsWith(".otf") }.orEmpty()
        val ohneSchriftlizenz = if (aufstellung.exists()) {
            val inhalt = aufstellung.readText()
            if (schriften.isNotEmpty() && !inhalt.contains("SIL Open Font License")) {
                listOf("${schriften.size} Schriften mitgeliefert, aber keine OFL-Angabe")
            } else emptyList()
        } else emptyList()

        val mitFehlend = if (aufstellung.exists() &&
            !aufstellung.readText().contains("Copyright (c) 2026 AI Dictation contributors")
        ) {
            listOf("der ursprüngliche MIT-Hinweis der Vorlage fehlt")
        } else emptyList()

        println("LIZENZTOR")
        println("  geprüfte Abhängigkeiten der Auslieferung: ${teile.size}")

        // **Ein Tor, das nicht prüfen konnte, ist kein Befund.**
        //
        // Findet es fast nirgends eine Paketbeschreibung, liegt das am
        // Tor und nicht an den Abhängigkeiten. Das muss anders klingen als
        // "diese Bibliothek hat eine unerwartete Lizenz", sonst sucht
        // jemand stundenlang an der falschen Stelle.
        if (unbekannt.size > teile.size / 2) {
            throw GradleException(
                "Das Lizenztor konnte nicht prüfen: für ${unbekannt.size} von " +
                    "${teile.size} Abhängigkeiten wurde keine Paketbeschreibung " +
                    "gefunden.\n\n" +
                    "Das ist kein Lizenzbefund, sondern ein Fehler im Tor -- " +
                    "vermutlich zeigt der Pfad auf den falschen Zwischenspeicher."
            )
        }
        gefunden.values.groupingBy { it }.eachCount().toList()
            .sortedByDescending { it.second }
            .forEach { (name, wieviele) -> println("    ${wieviele}x  $name") }

        val schaden = buildList {
            if (heikel.isNotEmpty()) add("Copyleft oder ungeklärt:\n" + heikel.joinToString("\n") { "    $it" })
            if (beanstandet.isNotEmpty()) add("unerwartete Lizenz:\n" + beanstandet.joinToString("\n") { "    $it" })
            if (unbekannt.isNotEmpty()) add("ohne Lizenzangabe:\n" + unbekannt.joinToString("\n") { "    $it" })
            if (fehlen.isNotEmpty()) add("in lizenzen.txt nicht genannt:\n" + fehlen.take(10).joinToString("\n") { "    $it" })
            if (ohneSchriftlizenz.isNotEmpty()) add(ohneSchriftlizenz.joinToString("\n"))
            if (mitFehlend.isNotEmpty()) add(mitFehlend.joinToString("\n"))
        }
        if (schaden.isEmpty()) {
            println("  nichts zu beanstanden")
        } else {
            throw GradleException(
                "Das Lizenztor schlägt an. Das heißt nicht, dass etwas unerlaubt ist -- " +
                    "es heißt, dass sich etwas geändert hat und jemand hinsehen muss.\n\n" +
                    schaden.joinToString("\n\n")
            )
        }
    }
}
