package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.NoOpGenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.openai.OpenAiClient
import io.github.aakira.napier.Napier
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that provides AI client libraries.
 */
val clientsModule: Module = module {
    single<GenerativeAIChatClient> {
        // Try to load API key from multiple sources at runtime
        val apiKey = loadOpenAiApiKey()

        if (apiKey != null) {
            // API key available - use real OpenAI client
            OpenAiClient(apiKey, get())
        } else {
            // API key missing - use no-op client for graceful degradation
            // This prevents crashes and returns AIResult.Unavailable(MissingCredentials)
            Napier.w(
                tag = "ClientsModule",
                message = "OPENAI_API_KEY not configured - AI features will be unavailable. " +
                    "Set via environment variable, system property, or local.properties"
            )
            NoOpGenerativeAIChatClient()
        }
    }
}
