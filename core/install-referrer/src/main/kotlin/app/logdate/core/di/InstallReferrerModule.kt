package app.logdate.core.di

import app.logdate.core.installreferrer.GooglePlayReferrer
import app.logdate.core.installreferrer.InstallReferrer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class InstallReferrerModule {
    @Binds
    abstract fun bindReferrer(referrer: GooglePlayReferrer): InstallReferrer
}