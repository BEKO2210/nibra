package de.ithandwerkstuttgart.nibra.forschung

import java.io.File

/**
 * Was der Prozess über sich selbst preisgibt: Rechenzeit, offene
 * Dateizeiger, Fäden, Speicher.
 *
 * Eigene, geprüfte Klasse, weil hier die gefährlichste Art Zahl entsteht --
 * eine, die plausibel aussieht und falsch ist. Findet Android eine Auskunft
 * nicht, kommt **`null`** heraus und nie eine 0: „keine Rechenzeit
 * verbraucht" wäre das beste denkbare Ergebnis und das genaue Gegenteil
 * von „nicht gemessen".
 */
object Prozessbefund {

    data class Stand(
        /** Verbrauchte Rechenzeit in Millisekunden, oder `null`. */
        val rechenzeitMillis: Long?,
        val offeneZeiger: Int?,
        val faeden: Int,
        val speicherKb: Long
    )

    fun nimmAuf(): Stand = Stand(
        rechenzeitMillis = rechenzeitMillis(),
        offeneZeiger = File("/proc/self/fd").list()?.size,
        faeden = Thread.activeCount(),
        speicherKb = with(Runtime.getRuntime()) { (totalMemory() - freeMemory()) / 1024 }
    )

    /**
     * Nutzer- plus Systemzeit aus `/proc/self/stat`, umgerechnet in
     * Millisekunden.
     *
     * Die Felder stehen an Stelle 14 und 15 -- gezählt **nach** dem
     * Programmnamen in Klammern. Der Name kann selbst Leerzeichen und
     * Klammern enthalten, deshalb wird ab der letzten schließenden Klammer
     * getrennt und nicht am ersten Leerzeichen. Ein Aufteilen am
     * Leerzeichen liefert bei einem Prozessnamen wie `(nibra forschung)`
     * stillschweigend die falschen Felder.
     */
    fun rechenzeitMillis(): Long? =
        runCatching { rechenzeitAus(File("/proc/self/stat").readText()) }.getOrNull()

    /**
     * Reine Zerlegung, damit sie ohne Gerät zu prüfen ist.
     *
     * Getrennt wird ab der **letzten** schließenden Klammer, nicht am
     * ersten Leerzeichen: der Programmname steht in Klammern und darf
     * selbst Leerzeichen und Klammern enthalten. Ein Aufteilen am
     * Leerzeichen liefert bei einem Namen wie `(nibra forschung)`
     * stillschweigend die falschen Felder -- und eine Rechenzeit, die
     * plausibel aussieht und falsch ist.
     */
    fun rechenzeitAus(roh: String): Long? = runCatching {
        val nachName = roh.substring(roh.lastIndexOf(')') + 1).trim().split(" ")
        // Nach der Klammer ist das erste Feld `state` (Stelle 3). utime ist
        // Stelle 14, also Index 11 in dieser Liste.
        val utime = nachName[11].toLong()
        val stime = nachName[12].toLong()
        (utime + stime) * 1000 / TAKTE_JE_SEKUNDE
    }.getOrNull()

    /**
     * Takte je Sekunde. Auf Android ist das durchweg 100; abfragbar wäre
     * es nur über `sysconf`, das Java nicht anbietet. Der Wert steht
     * deshalb hier fest **und** wird im Bericht genannt, damit niemand die
     * Rechenzeit für exakter hält, als sie ist.
     */
    const val TAKTE_JE_SEKUNDE = 100L
}
