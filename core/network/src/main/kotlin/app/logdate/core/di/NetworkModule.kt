package app.logdate.core.di

import app.logdate.core.network.httpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

/**
 * A module that exposes state of the real world including location and physical activity to the application.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    @Provides
    @Singleton
    fun provideNetworkHttpClient(): HttpClient = httpClient
}