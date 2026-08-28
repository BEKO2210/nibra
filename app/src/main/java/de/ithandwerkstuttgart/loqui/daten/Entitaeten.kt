package de.ithandwerkstuttgart.loqui.daten

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Ein Diktat, wie es auf dem Geraet liegt. Verlaesst das Geraet nie. */
@Entity(tableName = "diktate")
data class DiktatEintrag(
    @PrimaryKey val id: String,
    val text: String,
    val zeitpunktMillis: Long,
    val sprachCode: String,
    val dauerSekunden: Int
)

/** Eine eigene Ersetzung: Kuerzel im Diktat wird zum vollen Text. */
@Entity(tableName = "textbausteine")
data class TextbausteinEintrag(
    @PrimaryKey val id: String,
    val kuerzel: String,
    val ersatz: String
)

@Dao
interface DiktatDao {
    @Query("SELECT * FROM diktate ORDER BY zeitpunktMillis DESC")
    fun alle(): Flow<List<DiktatEintrag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun sichere(eintrag: DiktatEintrag)

    @Query("UPDATE diktate SET text = :text, sprachCode = :sprachCode WHERE id = :id")
    suspend fun aktualisiere(id: String, text: String, sprachCode: String)

    @Delete
    suspend fun loesche(eintrag: DiktatEintrag)

    @Query("DELETE FROM diktate WHERE id = :id")
    suspend fun loescheNachId(id: String)

    @Query("DELETE FROM diktate")
    suspend fun loescheAlle()
}

@Dao
interface TextbausteinDao {
    @Query("SELECT * FROM textbausteine ORDER BY kuerzel COLLATE NOCASE ASC")
    fun alle(): Flow<List<TextbausteinEintrag>>

    @Query("SELECT * FROM textbausteine")
    suspend fun alleEinmalig(): List<TextbausteinEintrag>

    @Upsert
    suspend fun sichere(eintrag: TextbausteinEintrag)

    @Query("DELETE FROM textbausteine WHERE id = :id")
    suspend fun loescheNachId(id: String)
}
