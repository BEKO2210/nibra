package de.ithandwerkstuttgart.nibra.forschung

/**
 * Entscheidet, ob ein Name im erkannten Text steht.
 *
 * Eigener, geprüfter Ort, weil an dieser Entscheidung das ganze Urteil
 * über die Vorgabeliste hängt. Zählt sie zu streng, sieht jede Vorgabe
 * wirkungslos aus; zählt sie zu großzügig, sieht jede Vorgabe gut aus.
 * Beides wäre nicht zu bemerken, wenn die Regel im Versuch verstreut
 * stünde.
 */
object Namenstreffer {

    /**
     * Wahr, wenn die Wörter des Namens **in dieser Reihenfolge und
     * unmittelbar nacheinander** im Text vorkommen.
     *
     * Groß- und Kleinschreibung sowie Satzzeichen bleiben außer Betracht:
     * „Aslani." und „aslani" sind für den Nutzer derselbe Treffer, und ein
     * Vergleich auf Gleichheit hätte reihenweise richtige Erkennungen als
     * Fehler gezählt.
     *
     * Zusammenhängend, nicht bloß irgendwo im Text: bei „d und b
     * audiotechnik" wäre es sonst schon ein Treffer, wenn „und" irgendwo
     * fällt und „audiotechnik" zehn Wörter später.
     */
    fun steckt(text: String, name: String): Boolean {
        val worte = Wortvergleich.zerlege(text)
        val gesucht = Wortvergleich.zerlege(name)
        if (gesucht.isEmpty() || worte.size < gesucht.size) return false
        return worte.windowed(gesucht.size, 1, partialWindows = false)
            .any { it == gesucht }
    }
}
