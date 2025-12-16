package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.openai.OpenAiClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that provides AI client libraries.
 */
val clientsModule: Module = module {
    single<GenerativeAIChatClient> {
        val apiKey = getKoin().getProperty<String>("OPENAI_API_KEY")
            ?: error("OPENAI_API_KEY is not configured")
        OpenAiClient(apiKey, get())
    }
}
