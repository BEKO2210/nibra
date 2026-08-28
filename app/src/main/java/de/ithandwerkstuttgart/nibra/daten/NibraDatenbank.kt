package de.ithandwerkstuttgart.nibra.daten

import androidx.room.Database
import androidx.room.RoomDatabase

/** Lokale Ablage von Nibra: Verlauf und Textbausteine, kein Netz. */
@Database(
    entities = [DiktatEintrag::class, TextbausteinEintrag::class],
    version = 1,
    exportSchema = false
)
abstract class NibraDatenbank : RoomDatabase() {
    abstract fun diktatDao(): DiktatDao
    abstract fun textbausteinDao(): TextbausteinDao
}
