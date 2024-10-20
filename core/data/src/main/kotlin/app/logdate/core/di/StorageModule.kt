package app.logdate.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File

/**
 * DI module that provides key directories for storage and data management.
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    fun provideCacheDirectory(@ApplicationContext context: Context): File {
        return context.cacheDir
    }
}