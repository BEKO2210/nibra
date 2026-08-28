package de.ithandwerkstuttgart.loqui.daten

import androidx.room.Database
import androidx.room.RoomDatabase

/** Lokale Ablage von Loqui: Verlauf und Textbausteine, kein Netz. */
@Database(
    entities = [DiktatEintrag::class, TextbausteinEintrag::class],
    version = 1,
    exportSchema = false
)
abstract class LoquiDatenbank : RoomDatabase() {
    abstract fun diktatDao(): DiktatDao
    abstract fun textbausteinDao(): TextbausteinDao
}
