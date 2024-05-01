package app.logdate.core.di

import app.logdate.core.network.LogdateServerBaseClient
import app.logdate.core.network.LogdateServerClient
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