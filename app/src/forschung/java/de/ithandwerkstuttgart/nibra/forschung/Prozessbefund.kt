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
        /** Java-Halde: belegt, wie die Laufzeitumgebung sie sieht. */
        val speicherKb: Long,
        /** Native Halde -- wächst unabhängig von der Java-Halde. */
        val nativeKb: Long,
        /**
         * Vom Kern gemeldeter Arbeitsspeicher des Prozesses (RSS).
         *
         * Aus `/proc/self/statm`, Feld 2, in Seiten. `null`, wenn nicht
         * lesbar. RSS enthält auch geteilte Seiten und ist deshalb grob --
         * aber es ist die einzige Zahl, die *beide* Halden und alles
         * andere zusammen sieht. Wächst sie, während beide Halden ruhig
         * bleiben, liegt es weder an Java noch am nativen Teil.
         */
        val rssKb: Long?
    )

    fun nimmAuf(): Stand = Stand(
        rechenzeitMillis = rechenzeitMillis(),
        offeneZeiger = File("/proc/self/fd").list()?.size,
        faeden = Thread.activeCount(),
        speicherKb = with(Runtime.getRuntime()) { (totalMemory() - freeMemory()) / 1024 },
        nativeKb = android.os.Debug.getNativeHeapAllocatedSize() / 1024,
        rssKb = rssAus(runCatching { File("/proc/self/statm").readText() }.getOrNull())
    )

    /**
     * RSS aus dem Inhalt von `/proc/self/statm`, in Kilobyte.
     *
     * Reine Funktion, damit sie ohne Gerät zu prüfen ist. Feld 2 ist die
     * Zahl der belegten Seiten; eine Seite ist auf allen hier fraglichen
     * Geräten 4 KB. Der Wert steht fest **und** wird genannt, damit
     * niemand die Zahl für genauer hält, als sie ist.
     */
    fun rssAus(inhalt: String?): Long? = runCatching {
        inhalt?.trim()?.split(" ")?.get(1)?.toLong()?.times(SEITE_KB)
    }.getOrNull()

    const val SEITE_KB = 4L

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
