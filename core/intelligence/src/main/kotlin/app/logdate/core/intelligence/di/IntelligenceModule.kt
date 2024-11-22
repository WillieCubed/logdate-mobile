package app.logdate.core.intelligence.di

import app.logdate.core.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.core.intelligence.generativeai.openai.OpenAiClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI Module that maps high-level interfaces to their implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IntelligenceModule {
    @Binds
    @Singleton
    abstract fun provideGenerativeAIClient(client: OpenAiClient): GenerativeAIChatClient
}

