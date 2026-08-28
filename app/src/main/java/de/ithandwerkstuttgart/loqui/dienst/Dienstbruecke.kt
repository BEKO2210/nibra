package de.ithandwerkstuttgart.loqui.dienst

/**
 * Einziger Weg von der App zum laufenden Bedienungshilfen-Dienst. Der
 * Dienst traegt sich hier ein, solange er lebt; die App fragt nur, ob er
 * gerade Text einfuegen kann.
 */
object Dienstbruecke {

    @Volatile
    private var dienst: DiktatBedienungshilfenDienst? = null

    fun melde(dienst: DiktatBedienungshilfenDienst?) {
        this.dienst = dienst
    }

    fun laeuft(): Boolean = dienst != null

    /** Liefert wahr, wenn der Text wirklich in einem Feld gelandet ist. */
    fun fuegeEin(text: String): Boolean = dienst?.fuegeTextEin(text) ?: false
}
