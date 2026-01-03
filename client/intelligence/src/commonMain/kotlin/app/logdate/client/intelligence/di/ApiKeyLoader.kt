package app.logdate.client.intelligence.di

/**
 * Loads the OpenAI API key from platform-specific sources at runtime.
 *
 * Checks multiple sources in priority order:
 * 1. Environment variables (OPENAI_API_KEY)
 * 2. System/JVM properties (OPENAI_API_KEY)
 * 3. Platform-specific BuildConfig (Android only)
 *
 * @return The API key if found and non-blank, null otherwise
 */
internal expect fun loadOpenAiApiKey(): String?
