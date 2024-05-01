package app.logdate.core.di

import app.logdate.core.updater.GooglePlayAppUpdater
import app.logdate.core.updater.AppUpdater
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdaterModule {
    @Binds
    abstract fun bindSubscriptionBiller(biller: GooglePlayAppUpdater): AppUpdater
}