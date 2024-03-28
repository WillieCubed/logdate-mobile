package app.logdate.core.di

import app.logdate.core.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object DaosModule {
    @Provides
    fun provideTextNoteDao(database: AppDatabase) = database.textNoteDao()

    @Provides
    fun provideImageNoteDao(database: AppDatabase) = database.imageNoteDao()

    @Provides
    fun provideJournalDao(database: AppDatabase) = database.journalsDao()
}