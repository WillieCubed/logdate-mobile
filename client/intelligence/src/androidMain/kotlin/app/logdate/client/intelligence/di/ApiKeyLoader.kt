package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.BuildConfig

internal actual fun loadOpenAiApiKey(): String? {
    // Priority 1: Environment variable
    System.getenv("OPENAI_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }

    // Priority 2: System property (JVM -D flag)
    System.getProperty("OPENAI_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }

    // Priority 3: BuildConfig (from local.properties at build time)
    return BuildConfig.OPENAI_API_KEY.takeIf { it.isNotBlank() }
}
