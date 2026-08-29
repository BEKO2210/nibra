package de.ithandwerkstuttgart.nibra.forschung

import kotlin.math.abs
import kotlin.math.sqrt

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
 *
 * Der zweite Wurf urteilte aus **drei** Böden, indem er den letzten Zuwachs
 * mit dem ersten verglich. Über 900 Sitzungen las er für die Java-Halde
 * 4788 -> 5335 -> 5601, also Zuwächse 547 und 266, und sprach „pendelt sich
 * ein". Über dreißig Fenster gerechnet steigt dieselbe Reihe mit 1,66 KB je
 * Sitzung bei siebenfachem Standardfehler, und die zweite Hälfte ist so
 * steil wie die erste. Drei Punkte können den Unterschied nicht sehen: fällt
 * der erste Zuwachs zufällig größer aus, heißt gleichmäßiges Wachstum
 * „pendelt sich ein".
 *
 * Deshalb urteilt diese Fassung über eine **Ausgleichsgerade durch die
 * Böden** -- nach derselben Regel, die im Projekt schon für den
 * [Ratenverlauf] gilt: mindestens acht Fenster, und die Steigung muss zwei
 * Standardfehler überschreiten. Was diese Schwelle nicht reißt, heißt nicht
 * „ruhig", sondern ist nicht belegt.
 */
object Verlaufsurteil {

    /** Mindestzahl Fenster für eine Aussage. Weniger sieht keinen Verlauf. */
    const val FENSTER_MINDESTENS = 8

    /** Wie viele Standardfehler die Steigung überschreiten muss. */
    const val STANDARDFEHLER_SCHWELLE = 2.0

    enum class Art {
        /** Kein belegter Anstieg des Bodens. */
        RUHIG,

        /** Der Boden steigt, aber die zweite Hälfte deutlich flacher. */
        PENDELT_SICH_EIN,

        /** Der Boden steigt belegt und wird nicht flacher. */
        WAECHST_WEITER,

        /** Zu wenige Fenster für eine Aussage -- **nicht** „ruhig". */
        UNBEKANNT
    }

    data class Befund(
        val art: Art,
        val boeden: List<Long>,
        val zuwaechse: List<Long>,
        /** Steigung der Ausgleichsgeraden, je Sitzung. */
        val steigung: Double = 0.0,
        /** Steigung geteilt durch ihren Standardfehler. */
        val sicherheit: Double = 0.0,
        val steigungErsteHaelfte: Double = 0.0,
        val steigungZweiteHaelfte: Double = 0.0
    )

    private data class Gerade(val steigung: Double, val standardfehler: Double) {
        /**
         * Steigung in Standardfehlern.
         *
         * **Streuen die Reste nicht, ist die Steigung sicher.** Die erste
         * Fassung gab hier 0 zurück, wenn der Standardfehler 0 war -- und
         * stufte damit eine schnurgerade steigende Reihe als „ruhig" ein.
         * Der eindeutigste denkbare Befund wäre der einzige gewesen, den
         * die Prüfung nicht gemeldet hätte.
         */
        val sicherheit: Double get() = when {
            standardfehler > 0 -> steigung / standardfehler
            steigung != 0.0 -> Double.POSITIVE_INFINITY * (if (steigung > 0) 1 else -1)
            else -> 0.0
        }
    }

    /**
     * @param werte die Messreihe, eine Zahl je Sitzung.
     * @param fenster wie viele Sitzungen je Fenster zusammengefasst werden.
     *        Der Aufrufer sollte so wählen, dass mindestens
     *        [FENSTER_MINDESTENS] Fenster entstehen.
     */
    fun beurteile(werte: List<Long>, fenster: Int = 30): Befund {
        if (fenster < 2 || werte.size < fenster * FENSTER_MINDESTENS) {
            return Befund(Art.UNBEKANNT, emptyList(), emptyList())
        }
        val boeden = (0..werte.size - fenster step fenster)
            .map { anfang -> werte.subList(anfang, anfang + fenster).min() }
        val zuwaechse = boeden.zipWithNext { a, b -> b - a }

        val ganz = gerade(boeden)
        // Auf die Sitzung umgerechnet: die Böden stehen ein Fenster
        // auseinander, nicht eine Sitzung.
        val jeSitzung = ganz.steigung / fenster
        val haelfte = boeden.size / 2
        val vorn = gerade(boeden.take(haelfte))
        val hinten = gerade(boeden.drop(haelfte))

        val art = when {
            ganz.steigung <= 0.0 -> Art.RUHIG
            abs(ganz.sicherheit) < STANDARDFEHLER_SCHWELLE -> Art.RUHIG
            // Einpendeln heißt: die zweite Hälfte ist **halb so steil**
            // oder flacher. Nicht: „ein Schritt war kleiner". Eine Reihe,
            // die von 3 auf 2 KB je Sitzung fällt, wächst weiter.
            //
            // Zusätzlich zu verlangen, dass die zweite Hälfte für sich
            // genommen nicht mehr belegt ist, war zu scharf: eine glatte
            // Wurzelkurve flacht deutlich ab, ihre zweite Hälfte ist aber
            // sauber gemessen und damit hoch belegt. Danach wäre kein
            // Einpendeln je erkannt worden.
            //
            // Von beiden Irrtümern ist „hält für ruhig, was leckt" der
            // gefährlichere. Deshalb die strenge Hälfte-Schwelle.
            hinten.steigung <= vorn.steigung / 2 -> Art.PENDELT_SICH_EIN
            else -> Art.WAECHST_WEITER
        }
        return Befund(
            art = art,
            boeden = boeden,
            zuwaechse = zuwaechse,
            steigung = jeSitzung,
            sicherheit = ganz.sicherheit,
            steigungErsteHaelfte = vorn.steigung / fenster,
            steigungZweiteHaelfte = hinten.steigung / fenster
        )
    }

    /** Ausgleichsgerade durch die Punkte, mit Standardfehler der Steigung. */
    private fun gerade(punkte: List<Long>): Gerade {
        val n = punkte.size
        if (n < 3) return Gerade(0.0, 0.0)
        val sx = (0 until n).sumOf { it.toDouble() }
        val sy = punkte.sumOf { it.toDouble() }
        val sxx = (0 until n).sumOf { it.toDouble() * it }
        val sxy = (0 until n).sumOf { it.toDouble() * punkte[it] }
        val nenner = n * sxx - sx * sx
        if (nenner == 0.0) return Gerade(0.0, 0.0)
        val m = (n * sxy - sx * sy) / nenner
        val c = (sy - m * sx) / n
        val quadrate = (0 until n).sumOf { val r = punkte[it] - (m * it + c); r * r }
        val streuung = sqrt(quadrate / (n - 2))
        return Gerade(m, streuung * sqrt(n / nenner))
    }

    fun beschreibe(befund: Befund): String {
        val zahlen = "%.2f je Sitzung, %.1f Standardfehler".format(
            befund.steigung, befund.sicherheit
        )
        return when (befund.art) {
            Art.RUHIG ->
                "Boden ohne belegten Anstieg (${befund.boeden.joinToString(" -> ")}) " +
                    "-- $zahlen, unter der Schwelle"
            Art.PENDELT_SICH_EIN ->
                "Boden steigt, zweite Hälfte flacht ab " +
                    "(%.2f -> %.2f je Sitzung) -- pendelt sich ein".format(
                        befund.steigungErsteHaelfte, befund.steigungZweiteHaelfte
                    )
            Art.WAECHST_WEITER ->
                "**Boden steigt belegt weiter** ($zahlen; erste Hälfte " +
                    "%.2f, zweite %.2f je Sitzung) -- Leck".format(
                        befund.steigungErsteHaelfte, befund.steigungZweiteHaelfte
                    )
            Art.UNBEKANNT ->
                "zu wenige Sitzungen für eine Aussage " +
                    "(mindestens $FENSTER_MINDESTENS Fenster nötig)"
        }
    }
}
