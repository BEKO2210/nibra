package de.ithandwerkstuttgart.nibra.forschung

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MicrophoneInfo
import android.os.Build

/**
 * Stellt fest, was das Geraet ueber seine Mikrofone **tatsaechlich** meldet.
 *
 * Nur fuer die Forschungsauspraegung. Sie beantwortet Fragen, die man sonst
 * annehmen wuerde: Wie viele Mikrofone gibt es? Welche sind bei welcher
 * Quelle aktiv? Welche Kanalzuordnung? Bekommen wir bei `UNPROCESSED`
 * wirklich unbearbeitetes Signal?
 *
 * **Nichts hier wird geraten.** Wo Android keine Auskunft gibt, steht das
 * ausdruecklich als „nicht gemeldet" im Bericht -- nicht als Schaetzung.
 */
object Mikrofonbefund {

    /** Die drei Quellen, die fuer Diktat ueberhaupt in Frage kommen. */
    val QUELLEN = listOf(
        "MIC" to MediaRecorder.AudioSource.MIC,
        "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "UNPROCESSED" to MediaRecorder.AudioSource.UNPROCESSED
    )

    /** Abtastraten, die der Reihe nach probiert werden. */
    val RATEN = listOf(48_000, 44_100, 32_000, 16_000, 8_000)

    fun erhebe(zusammenhang: Context): String = buildString {
        val verwaltung = zusammenhang.getSystemService(AudioManager::class.java)

        zeile("GERAET")
        zeile("  Modell            ${Build.MANUFACTURER} ${Build.MODEL}")
        zeile("  Android           ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        zeile("  Hardware          ${Build.HARDWARE}")
        zeile("")

        zeile("EIGENSCHAFTEN, DIE DAS SYSTEM MELDET")
        listOf(
            AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE to "bevorzugte Abtastrate (Ausgabe)",
            AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER to "Rahmen je Puffer (Ausgabe)"
        ).forEach { (schluessel, was) ->
            zeile("  ${was.padEnd(34)} ${verwaltung?.getProperty(schluessel) ?: "nicht gemeldet"}")
        }
        zeile("")

        zeile("EINGABEGERAETE (AudioManager.getDevices)")
        val eingaenge = verwaltung?.getDevices(AudioManager.GET_DEVICES_INPUTS).orEmpty()
        if (eingaenge.isEmpty()) zeile("  keine gemeldet")
        eingaenge.forEach { geraet ->
            zeile("  ${artName(geraet.type)}")
            zeile("    Bezeichnung     ${geraet.productName}")
            zeile("    Kanaele         ${geraet.channelCounts.joinToString().ifEmpty { "nicht gemeldet" }}")
            zeile("    Abtastraten     ${geraet.sampleRates.joinToString().ifEmpty { "nicht gemeldet" }}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                zeile("    Adresse         ${geraet.address.ifBlank { "nicht gemeldet" }}")
            }
        }
        zeile("")

        zeile("MIKROFONE (AudioManager.getMicrophones)")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            zeile("  nicht verfuegbar unter Android 9")
        } else {
            val mikrofone = runCatching { verwaltung?.microphones.orEmpty() }
                .getOrElse {
                    zeile("  Abfrage schlug fehl: ${it.javaClass.simpleName}")
                    emptyList()
                }
            if (mikrofone.isEmpty()) zeile("  keine gemeldet")
            mikrofone.forEach { schreibeMikrofon(it) }
        }
        zeile("")

        zeile("QUELLEN: WAS TATSAECHLICH GEHT")
        QUELLEN.forEach { (name, quelle) ->
            zeile("  $name")
            pruefeQuelle(name, quelle).forEach { zeile("    $it") }
        }
    }

    /**
     * Probiert eine Quelle wirklich aus, statt ihre Verfuegbarkeit
     * anzunehmen: anlegen, starten, lesen, die aktiven Mikrofone erfragen.
     *
     * Eine Quelle kann sich anlegen lassen und trotzdem nur Stille liefern.
     * Deshalb wird gelesen und der Ausschlag gemessen.
     */
    private fun pruefeQuelle(name: String, quelle: Int): List<String> {
        val zeilen = mutableListOf<String>()

        RATEN.forEach { rate ->
            val kleinste = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (kleinste <= 0) {
                zeilen += "$rate Hz: nicht unterstuetzt (getMinBufferSize = $kleinste)"
                return@forEach
            }

            var aufnahme: AudioRecord? = null
            try {
                aufnahme = AudioRecord(
                    quelle, rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    kleinste * 4
                )
                if (aufnahme.state != AudioRecord.STATE_INITIALIZED) {
                    zeilen += "$rate Hz: nicht bereit (state = ${aufnahme.state})"
                    return@forEach
                }
                aufnahme.startRecording()
                if (aufnahme.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    zeilen += "$rate Hz: startet nicht"
                    return@forEach
                }

                val puffer = ShortArray(kleinste / 2)
                var groesster = 0
                var gelesen = 0
                // Rund 400 ms lesen -- genug, damit der Pfad wirklich laeuft
                // und ein Ausschlag sichtbar wuerde.
                val bis = System.nanoTime() + 400_000_000L
                while (System.nanoTime() < bis) {
                    val n = aufnahme.read(puffer, 0, puffer.size)
                    if (n <= 0) break
                    gelesen += n
                    for (i in 0 until n) {
                        val betrag = kotlin.math.abs(puffer[i].toInt())
                        if (betrag > groesster) groesster = betrag
                    }
                }

                val aktive = aktiveMikrofone(aufnahme)
                zeilen += "$rate Hz: OK  gelesen=$gelesen Rahmen  " +
                    "groesster Ausschlag=$groesster/32767  " +
                    "Rate laut Geraet=${aufnahme.sampleRate}  " +
                    "Kanaele=${aufnahme.channelCount}"
                if (aktive.isNotEmpty()) {
                    aktive.forEach { zeilen += "    aktiv: $it" }
                }
            } catch (fehler: Throwable) {
                zeilen += "$rate Hz: ${fehler.javaClass.simpleName} ${fehler.message.orEmpty()}"
            } finally {
                runCatching { aufnahme?.stop() }
                runCatching { aufnahme?.release() }
            }
        }
        return zeilen
    }

    /** Welche Mikrofone waehrend dieser Aufnahme wirklich aktiv sind. */
    fun aktiveMikrofone(aufnahme: AudioRecord): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return listOf("nicht abfragbar unter Android 9")
        }
        return runCatching {
            aufnahme.activeMicrophones.map { mikro ->
                val zuordnung = mikro.channelMapping.joinToString { paar ->
                    "Kanal ${paar.first}=${wertName(paar.second)}"
                }
                "${mikro.description} / ${richtungName(mikro.directionality)} / " +
                    "Ort ${ortName(mikro.location)} / " +
                    (zuordnung.ifBlank { "keine Kanalzuordnung gemeldet" })
            }
        }.getOrElse { listOf("Abfrage schlug fehl: ${it.javaClass.simpleName}") }
    }

    private fun StringBuilder.zeile(text: String) = appendLine(text)

    private fun StringBuilder.schreibeMikrofon(mikro: MicrophoneInfo) {
        zeile("  ${mikro.description}")
        zeile("    Kennung         ${mikro.id}")
        zeile("    Ort             ${ortName(mikro.location)}")
        zeile("    Richtung        ${richtungName(mikro.directionality)}")
        zeile("    Adresse         ${mikro.address.ifBlank { "nicht gemeldet" }}")
        val lage = runCatching { mikro.position }.getOrNull()
        zeile(
            "    Lage            " + if (lage != null && !lage.x.isNaN()) {
                "x=${lage.x} y=${lage.y} z=${lage.z}"
            } else {
                "nicht gemeldet"
            }
        )
        val zuordnung = runCatching {
            mikro.channelMapping.joinToString { "Kanal ${it.first}=${wertName(it.second)}" }
        }.getOrNull()
        zeile("    Kanalzuordnung  ${zuordnung?.ifBlank { "leer" } ?: "nicht gemeldet"}")
    }

    private fun wertName(wert: Int): String = when (wert) {
        MicrophoneInfo.CHANNEL_MAPPING_DIRECT -> "DIRECT"
        MicrophoneInfo.CHANNEL_MAPPING_PROCESSED -> "PROCESSED"
        else -> "unbekannt($wert)"
    }

    private fun ortName(wert: Int): String = when (wert) {
        MicrophoneInfo.LOCATION_MAINBODY -> "Geraet"
        MicrophoneInfo.LOCATION_MAINBODY_MOVABLE -> "Geraet, beweglich"
        MicrophoneInfo.LOCATION_PERIPHERAL -> "angeschlossen"
        else -> "unbekannt($wert)"
    }

    private fun richtungName(wert: Int): String = when (wert) {
        MicrophoneInfo.DIRECTIONALITY_OMNI -> "rundum"
        MicrophoneInfo.DIRECTIONALITY_BI_DIRECTIONAL -> "zweiseitig"
        MicrophoneInfo.DIRECTIONALITY_CARDIOID -> "Niere"
        MicrophoneInfo.DIRECTIONALITY_HYPER_CARDIOID -> "Superniere"
        MicrophoneInfo.DIRECTIONALITY_SUPER_CARDIOID -> "Hyperniere"
        MicrophoneInfo.DIRECTIONALITY_UNKNOWN -> "nicht gemeldet"
        else -> "unbekannt($wert)"
    }

    private fun artName(wert: Int): String = when (wert) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "eingebautes Mikrofon"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (SCO)"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headset am Kabel"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB-Geraet"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-Headset"
        AudioDeviceInfo.TYPE_TELEPHONY -> "Telefonie"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "Abgriff (Remote Submix)"
        else -> "Art $wert"
    }
}
