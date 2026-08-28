package de.ithandwerkstuttgart.nibra.funktionsumfang

/**
 * Merkmale, die später kostenpflichtig sein könnten (siehe AUFTRAG.md,
 * Nachtrag "Spätere Bezahlvariante offenhalten"). Heute gibt es kein
 * Konto, keine Zahlung, keine Werbung -- aber jede Stelle im Code, die
 * später eine Freischaltung prüfen müsste, fragt schon jetzt hier an,
 * statt das Merkmal direkt zu nutzen.
 */
enum class Merkmal {
    UNBEGRENZTER_VERLAUF,
    TEXTBAUSTEINE_UNBEGRENZT,
    EXPORT,
    MEHR_ALS_DREI_SPRACHEN
}

/**
 * Der einzige Ort, an dem eine spätere Bezahlvariante eingehängt würde.
 * Liefert heute für jedes Merkmal immer `true` -- keine Bibliothek für
 * Abrechnung, kein Play-Billing, nur die Schnittstelle.
 */
interface Funktionsumfang {
    fun istFreigeschaltet(merkmal: Merkmal): Boolean
}

object ImmerFreigeschaltet : Funktionsumfang {
    override fun istFreigeschaltet(merkmal: Merkmal): Boolean = true
}
