package app.logdate.core.intelligence.di

import app.logdate.core.coroutines.BuildConfig
import app.logdate.core.intelligence.generativeai.openai.OpenAiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

/**
 * DI module that provides AI client libraries.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClientsModule {
    @Provides
    @Singleton
    fun provideOpenAIClient(httpClient: HttpClient): OpenAiClient {
        // TODO: Move this to the server and provide the API key securely.
        val apiKey = BuildConfig.OPENAI_API_KEY
        return OpenAiClient(apiKey, httpClient)
    }
}