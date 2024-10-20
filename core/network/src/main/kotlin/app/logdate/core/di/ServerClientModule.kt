package app.logdate.core.di

import app.logdate.core.activitypub.LogdateServerBaseClient
import app.logdate.core.activitypub.LogdateServerClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
internal object ServerClientModule {
    // TODO: Fetch domain from configuration
    @Provides
    fun provideLogdateServerClient(): LogdateServerBaseClient = LogdateServerClient("logdate.app")
}