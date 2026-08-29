import java.util.Properties
import java.io.FileInputStream

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
