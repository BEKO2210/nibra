package de.ithandwerkstuttgart.nibra.daten

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.ithandwerkstuttgart.nibra.erkennung.Erkennerhalter
import de.ithandwerkstuttgart.nibra.erkennung.Erkennerquelle
import de.ithandwerkstuttgart.nibra.erkennung.Spracherkenner
import de.ithandwerkstuttgart.nibra.erkennung.Sprachverzeichnis
import javax.inject.Singleton

/** Alles, was Nibra braucht, kommt vom Gerät -- nichts aus dem Netz. */
@Module
@InstallIn(SingletonComponent::class)
object DatenModul {

    @Provides
    @Singleton
    fun datenbank(@ApplicationContext context: Context): NibraDatenbank =
        Room.databaseBuilder(context, NibraDatenbank::class.java, "nibra.db").build()

    @Provides
    fun diktatDao(datenbank: NibraDatenbank): DiktatDao = datenbank.diktatDao()

    @Provides
    fun textbausteinDao(datenbank: NibraDatenbank): TextbausteinDao = datenbank.textbausteinDao()

    @Provides
    @Singleton
    fun einstellungenAblage(@ApplicationContext context: Context): EinstellungenAblage =
        EinstellungenAblage(context)

    @Provides
    @Singleton
    fun spracherkenner(
        @ApplicationContext context: Context,
        halter: Erkennerhalter
    ): Spracherkenner = Spracherkenner(context, halter)

    @Provides
    @Singleton
    fun erkennerquelle(erkenner: Spracherkenner): Erkennerquelle = erkenner

    @Provides
    @Singleton
    fun sprachverzeichnis(
        @ApplicationContext context: Context,
        halter: Erkennerhalter
    ): Sprachverzeichnis = Sprachverzeichnis(context, halter)
}
