package de.ithandwerkstuttgart.nibra.forschung

/**
 * Wächst eine Größe über viele Sitzungen weiter, oder pendelt sie sich ein?
 *
 * Für Speicher ist der **Boden** die richtige Zahl, nicht der Mittelwert.
 * Eine Halde ohne Bereinigung steigt immer -- das ist ihr Wesen und kein
 * Befund. Was ein Leck von gesundem Verhalten trennt, ist, ob der Stand
 * **nach** dem Aufräumen steigt. Bleibt der Boden liegen, wurde alles
 * wieder frei; steigt er gleichmäßig, bleibt je Sitzung etwas hängen.
 *
 * Der erste Wurf verglich Mittelwerte zweier Fenster und meldete bei
 * beiden Geräten „wächst weiter" -- bei 0,24 KB je Sitzung. Zwei Fehler
 * darin:
 *
 * 1. Auf einem Sägezahn misst der Mittelwert vor allem, wo die
 *    Bereinigung gerade stand.
 * 2. Die Schwelle von 0,1 je Sitzung stammte von **Dateizeigern**. Auf
 *    Kilobyte angewandt heißt sie: hundert Byte je Sitzung sind schon ein
 *    Leck. Dieselbe Zahl für zwei Größen, die nichts miteinander zu tun
 *    haben.
 */
object Verlaufsurteil {

    enum class Art {
        /** Der Boden bleibt liegen. */
        RUHIG,

        /** Der Boden steigt, aber die Zuwächse schrumpfen. */
        PENDELT_SICH_EIN,

        /** Der Boden steigt mit gleichbleibenden Zuwächsen. */
        WAECHST_WEITER,

        /** Zu wenige Fenster für eine Aussage -- **nicht** „ruhig". */
        UNBEKANNT
    }

    data class Befund(val art: Art, val boeden: List<Long>, val zuwaechse: List<Long>)

    /**
     * @param werte die Messreihe, eine Zahl je Sitzung.
     * @param fenster wie viele Sitzungen je Fenster zusammengefasst werden.
     */
    fun beurteile(werte: List<Long>, fenster: Int = 100): Befund {
        if (fenster < 2 || werte.size < fenster * 3) {
            return Befund(Art.UNBEKANNT, emptyList(), emptyList())
        }
        val boeden = (0..werte.size - fenster step fenster)
            .map { anfang -> werte.subList(anfang, anfang + fenster).min() }
        val zuwaechse = boeden.zipWithNext { a, b -> b - a }
        val gesamt = boeden.last() - boeden.first()
        return Befund(
            art = when {
                gesamt <= 0 -> Art.RUHIG
                // Schrumpfen die Zuwächse deutlich, läuft die Größe auf
                // einen festen Stand zu. Bleiben sie gleich, nicht.
                zuwaechse.last() <= zuwaechse.first() / 2 -> Art.PENDELT_SICH_EIN
                else -> Art.WAECHST_WEITER
            },
            boeden = boeden,
            zuwaechse = zuwaechse
        )
    }

    fun beschreibe(befund: Befund): String = when (befund.art) {
        Art.RUHIG -> "Boden bleibt liegen (${befund.boeden.joinToString(" -> ")}) -- kein Leck"
        Art.PENDELT_SICH_EIN ->
            "Boden steigt mit schrumpfenden Zuwächsen " +
                "(${befund.boeden.joinToString(" -> ")}, " +
                "Zuwächse ${befund.zuwaechse.joinToString(", ")}) -- pendelt sich ein"
        Art.WAECHST_WEITER ->
            "**Boden steigt gleichmäßig** (${befund.boeden.joinToString(" -> ")}, " +
                "Zuwächse ${befund.zuwaechse.joinToString(", ")}) -- Leck möglich"
        Art.UNBEKANNT -> "zu wenige Sitzungen für eine Aussage"
    }
}
