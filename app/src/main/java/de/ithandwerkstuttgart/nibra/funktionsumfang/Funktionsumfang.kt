package de.ithandwerkstuttgart.nibra.funktionsumfang

/**
 * Merkmale, die spaeter kostenpflichtig sein koennten (siehe AUFTRAG.md,
 * Nachtrag "Spaetere Bezahlvariante offenhalten"). Heute gibt es kein
 * Konto, keine Zahlung, keine Werbung -- aber jede Stelle im Code, die
 * spaeter eine Freischaltung pruefen muesste, fragt schon jetzt hier an,
 * statt das Merkmal direkt zu nutzen.
 */
enum class Merkmal {
    UNBEGRENZTER_VERLAUF,
    TEXTBAUSTEINE_UNBEGRENZT,
    EXPORT,
    MEHR_ALS_DREI_SPRACHEN
}

/**
 * Der einzige Ort, an dem eine spaetere Bezahlvariante eingehaengt wuerde.
 * Liefert heute fuer jedes Merkmal immer `true` -- keine Bibliothek fuer
 * Abrechnung, kein Play-Billing, nur die Schnittstelle.
 */
interface Funktionsumfang {
    fun istFreigeschaltet(merkmal: Merkmal): Boolean
}

object ImmerFreigeschaltet : Funktionsumfang {
    override fun istFreigeschaltet(merkmal: Merkmal): Boolean = true
}
