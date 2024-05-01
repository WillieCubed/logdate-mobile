package app.logdate.core.di

import android.content.Context
import app.logdate.core.database.AppDatabase
import app.logdate.core.database.BackupableDatabase
import app.logdate.core.database.LogdateDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * A module to provide the app-wide database.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.buildDatabase(context)

    @Provides
    @Singleton
    fun provideLogdateDatabase(@ApplicationContext context: Context): LogdateDatabase =
        provideAppDatabase(context)

    @Provides
    @Singleton
    fun provideBackupableDatabase(@ApplicationContext context: Context): BackupableDatabase =
        provideAppDatabase(context)
}
