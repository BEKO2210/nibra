package de.ithandwerkstuttgart.loqui.daten

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.ithandwerkstuttgart.loqui.erkennung.Erkennerquelle
import de.ithandwerkstuttgart.loqui.erkennung.Spracherkenner
import de.ithandwerkstuttgart.loqui.erkennung.Sprachverzeichnis
import javax.inject.Singleton

/** Alles, was Loqui braucht, kommt vom Geraet -- nichts aus dem Netz. */
@Module
@InstallIn(SingletonComponent::class)
object DatenModul {

    @Provides
    @Singleton
    fun datenbank(@ApplicationContext context: Context): LoquiDatenbank =
        Room.databaseBuilder(context, LoquiDatenbank::class.java, "loqui.db").build()

    @Provides
    fun diktatDao(datenbank: LoquiDatenbank): DiktatDao = datenbank.diktatDao()

    @Provides
    fun textbausteinDao(datenbank: LoquiDatenbank): TextbausteinDao = datenbank.textbausteinDao()

    @Provides
    @Singleton
    fun einstellungenAblage(@ApplicationContext context: Context): EinstellungenAblage =
        EinstellungenAblage(context)

    @Provides
    @Singleton
    fun spracherkenner(@ApplicationContext context: Context): Spracherkenner =
        Spracherkenner(context)

    @Provides
    @Singleton
    fun erkennerquelle(erkenner: Spracherkenner): Erkennerquelle = erkenner

    @Provides
    @Singleton
    fun sprachverzeichnis(@ApplicationContext context: Context): Sprachverzeichnis =
        Sprachverzeichnis(context)
}
