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
    fun bindTextNoteDao(database: AppDatabase) = database.textNoteDao()
    @Provides
    fun bindImageNoteDao(database: AppDatabase) = database.imageNoteDao()
}