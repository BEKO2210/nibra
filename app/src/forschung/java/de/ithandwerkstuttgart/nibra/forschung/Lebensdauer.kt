package de.ithandwerkstuttgart.nibra.forschung

import java.lang.ref.WeakReference

/**
 * Wie lange überleben Objekte ihre Sitzung?
 *
 * **Die Zahl gleichzeitig lebender Objekte beantwortet die Frage nicht.**
 * Leben bei Sitzung 900 noch hundert Erkenner, ist keiner davon älter als
 * vierzig Sitzungen, dann gibt das Gerät sie verzögert frei und der
 * Rückstau ist begrenzt. Leben dagegen Erkenner aus Sitzung 5 immer noch,
 * hält sie jemand fest -- und das ist etwas völlig anderes, obwohl beide
 * Fälle dieselbe Gesamtzahl zeigen können.
 *
 * Deshalb zählt hier das **Alter**, nicht der Bestand.
 */
object Lebensdauer {

    /** Ein Objekt, das beobachtet wird, ohne festgehalten zu werden. */
    data class Zeuge(
        val sitzung: Int,
        val art: String,
        val kennung: Long,
        val referenz: WeakReference<Any>
    )

    /** Die Altersverteilung der Überlebenden einer Art. */
    data class Verteilung(
        val art: String,
        val lebende: Int,
        val aeltester: Int,
        val median: Int,
        val perzentil95: Int,
        /** Wie viele Überlebende älter sind als 10, 25, 50, 100, 200 Sitzungen. */
        val aelterAls: Map<Int, Int>
    ) {
        override fun toString(): String =
            "%-14s %6d %8d %8d %8d   %s".format(
                art.take(14), lebende, aeltester, median, perzentil95,
                SCHWELLEN.joinToString(" ") { "%5d".format(aelterAls[it] ?: 0) }
            )
    }

    val SCHWELLEN = listOf(10, 25, 50, 100, 200)

    const val KOPFZEILE =
        "  Art             lebend  ältester   Median      P95      >10   >25   >50  >100  >200"

    /**
     * @param zeugen alle bisher angehängten Referenzen.
     * @param jetzt die Nummer der gerade beendeten Sitzung.
     * @param mindestAbstand wie viele Sitzungen ein Objekt zurückliegen
     *        muss, um überhaupt als Überlebender zu gelten. Die jüngste
     *        Sitzung darf noch gehalten werden, ohne dass das ein Befund
     *        wäre.
     */
    fun verteilungen(
        zeugen: List<Zeuge>,
        jetzt: Int,
        mindestAbstand: Int = 2
    ): List<Verteilung> =
        zeugen
            .filter { it.sitzung <= jetzt - mindestAbstand && it.referenz.get() != null }
            .groupBy { it.art }
            .map { (art, gruppe) -> verteilung(art, gruppe.map { jetzt - it.sitzung }) }
            .sortedByDescending { it.lebende }

    /** Aus den Altersangaben in Sitzungen die Verteilung rechnen. */
    fun verteilung(art: String, alter: List<Int>): Verteilung {
        if (alter.isEmpty()) {
            return Verteilung(art, 0, 0, 0, 0, SCHWELLEN.associateWith { 0 })
        }
        val sortiert = alter.sorted()
        return Verteilung(
            art = art,
            lebende = sortiert.size,
            aeltester = sortiert.last(),
            median = sortiert[sortiert.size / 2],
            // Der Wert, unter dem 95 von hundert liegen. Bei wenigen
            // Werten ist das der grösste -- und das ist richtig so: aus
            // fünf Zahlen lässt sich kein Perzentil schätzen, das mehr
            // wüsste als das Maximum.
            perzentil95 = sortiert[minOf(sortiert.size - 1, (sortiert.size * 95) / 100)],
            aelterAls = SCHWELLEN.associateWith { grenze -> sortiert.count { it > grenze } }
        )
    }

    /**
     * Pearsons Zusammenhang zweier Reihen.
     *
     * Für die Frage, ob die native Halde mit der Zahl lebender Erkenner
     * mitgeht. **Ein Zusammenhang ist keine Ursache** -- beide könnten mit
     * der Sitzungsnummer wachsen und deshalb miteinander. Die Zahl sagt
     * nur, ob es sich lohnt, dort weiterzusuchen.
     *
     * @return `null`, wenn zu wenige Punkte vorliegen oder eine Reihe
     *         konstant ist.
     */
    fun zusammenhang(a: List<Double>, b: List<Double>): Double? {
        if (a.size != b.size || a.size < 3) return null
        val ma = a.average()
        val mb = b.average()
        val zaehler = a.indices.sumOf { (a[it] - ma) * (b[it] - mb) }
        val nenner = kotlin.math.sqrt(
            a.sumOf { (it - ma) * (it - ma) } * b.sumOf { (it - mb) * (it - mb) }
        )
        return if (nenner == 0.0) null else zaehler / nenner
    }

    /**
     * Die Einordnung, um die es am Ende geht.
     *
     * Bewusst mit einem eigenen Fall für „weiß ich nicht": ein Befund, der
     * sich nicht einordnen lässt, ist keiner der anderen drei.
     */
    enum class Einordnung {
        /** Alte Objekte bleiben dauerhaft, der Bestand wächst. */
        UNBEGRENZTES_LECK,

        /** Objekte leben verzögert, Alter und Bestand laufen auf eine Grenze zu. */
        BEGRENZTER_RUECKSTAU,

        /** Das Wachstum hört nach einer Anlaufzeit auf. */
        ANLAUF_ODER_ZWISCHENSPEICHER,

        /** Reicht nicht für eine Aussage. */
        UNGEKLAERT
    }

    /**
     * @param aelteste das Alter des ältesten Überlebenden an jedem
     *        Haltepunkt, in Reihenfolge.
     * @param bestaende die Zahl der Lebenden an jedem Haltepunkt.
     */
    fun ordneEin(aelteste: List<Int>, bestaende: List<Int>): Einordnung {
        if (aelteste.size < 4 || bestaende.size != aelteste.size) {
            return Einordnung.UNGEKLAERT
        }
        val haelfte = aelteste.size / 2
        val alterVorn = aelteste.take(haelfte).average()
        val alterHinten = aelteste.drop(haelfte).average()
        val bestandVorn = bestaende.take(haelfte).average()
        val bestandHinten = bestaende.drop(haelfte).average()

        return when {
            // Wächst das Alter des ältesten Überlebenden ungefähr im Takt
            // der Sitzungen, überlebt dasselbe Objekt die ganze Zeit.
            alterHinten > alterVorn * 1.8 && bestandHinten > bestandVorn * 1.3 ->
                Einordnung.UNBEGRENZTES_LECK

            // Alter und Bestand stehen: es staut sich, aber begrenzt.
            alterHinten <= alterVorn * 1.4 && bestandHinten <= bestandVorn * 1.4 ->
                Einordnung.BEGRENZTER_RUECKSTAU

            bestandHinten <= bestandVorn * 1.1 ->
                Einordnung.ANLAUF_ODER_ZWISCHENSPEICHER

            else -> Einordnung.UNGEKLAERT
        }
    }

    fun beschreibe(einordnung: Einordnung): String = when (einordnung) {
        Einordnung.UNBEGRENZTES_LECK ->
            "A) UNBEGRENZTES LECK -- alte Objekte bleiben, der Bestand wächst mit"
        Einordnung.BEGRENZTER_RUECKSTAU ->
            "B) BEGRENZTER RÜCKSTAU -- verzögerte Freigabe, Alter und Bestand laufen auf eine Grenze"
        Einordnung.ANLAUF_ODER_ZWISCHENSPEICHER ->
            "C) ANLAUF ODER ZWISCHENSPEICHER -- das Wachstum hört nach einer Phase auf"
        Einordnung.UNGEKLAERT ->
            "D) UNGEKLÄRT -- die Messung trägt keine der drei Aussagen"
    }
}
