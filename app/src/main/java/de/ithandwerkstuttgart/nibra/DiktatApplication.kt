package de.ithandwerkstuttgart.nibra

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Anwendungsklasse von Nibra. Bewusst schlank: die App fordert kein
 * INTERNET-Recht an und braucht deshalb keine Netz-Initialisierung. Lokale
 * Datenhaltung (Room/DataStore) und die Geraete-Erkennung haengen ueber
 * Hilt daran.
 */
@HiltAndroidApp
class DiktatApplication : Application()
