package app.logdate.core.di

import app.logdate.core.media.MediaManager
import app.logdate.core.media.OnDeviceMediaManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds
    abstract fun provideMediaManager(mediaManager: OnDeviceMediaManager): MediaManager
}
