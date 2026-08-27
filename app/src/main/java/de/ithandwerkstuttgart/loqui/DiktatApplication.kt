package de.ithandwerkstuttgart.loqui

import android.app.Application

/**
 * Anwendungsklasse von Loqui. Bewusst schlank: die App fordert kein
 * INTERNET-Recht an und braucht deshalb keine Netz-Initialisierung. Lokale
 * Datenhaltung (Room/DataStore) haengt sich hier ein, sobald Station 4 sie
 * verdrahtet (siehe AUFTRAG.md).
 */
class DiktatApplication : Application()
