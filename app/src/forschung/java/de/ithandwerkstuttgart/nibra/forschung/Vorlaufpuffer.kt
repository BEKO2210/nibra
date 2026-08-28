package de.ithandwerkstuttgart.nibra.forschung

/**
 * Hält die jüngsten Tonblöcke, bis die Erkennung beginnt.
 *
 * Ohne ihn geht verloren, was zwischen dem Öffnen des Mikrofons und dem
 * Start der Erkennung gesprochen wurde -- also genau der Wortanfang, auf
 * den es ankommt.
 *
 * Bewusst ohne Android: der Vorlauf lässt sich damit gegen eine bekannte
 * Zahlenfolge prüfen, statt gleichzeitig fünf Android-Bestandteile zu
 * testen. Ein Vorlauf, der Blöcke doppelt, vertauscht oder verliert, wäre
 * sonst erst an unerklärlich schlechter Erkennung aufgefallen.
 */
class Vorlaufpuffer(private val hoechstens: Int) {

    private val bloecke = ArrayDeque<ByteArray>()

    /** Wie viele Blöcke gerade aufgehoben sind. */
    val anzahl: Int get() = bloecke.size

    /**
     * Legt einen Block ab. Ist der Puffer voll, fällt der **älteste**
     * heraus -- ein Vorlauf soll das Jüngste behalten, nicht das Erste.
     */
    fun lege(block: ByteArray) {
        if (hoechstens <= 0) return
        bloecke.addLast(block)
        while (bloecke.size > hoechstens) bloecke.removeFirst()
    }

    /**
     * Gibt den Vorlauf heraus und leert sich.
     *
     * Die Reihenfolge ist die der Aufnahme. Andersherum wäre es kein
     * Vorlauf, sondern Durcheinander -- und der Erkenner bekäme einen
     * Satz, den niemand gesprochen hat.
     */
    fun nimmHeraus(): List<ByteArray> {
        val alle = bloecke.toList()
        bloecke.clear()
        return alle
    }

    fun leere() = bloecke.clear()

    companion object {
        /**
         * Wie viele Blöcke für eine gewünschte Dauer nötig sind.
         *
         * @param blockBytes Länge eines Blocks in Bytes
         * @param abtastrate Abtastwerte je Sekunde
         * @param bytesJeWert bei 16 Bit sind es zwei
         */
        fun bloeckeFuer(
            millis: Int,
            blockBytes: Int,
            abtastrate: Int,
            bytesJeWert: Int = 2
        ): Int {
            if (millis <= 0 || blockBytes <= 0) return 0
            val bytesGesamt = millis.toLong() * abtastrate * bytesJeWert / 1000
            // Aufrunden: lieber etwas mehr Vorlauf als ein abgeschnittenes
            // erstes Wort.
            return ((bytesGesamt + blockBytes - 1) / blockBytes).toInt()
        }
    }
}
