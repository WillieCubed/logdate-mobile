package app.logdate.client.intelligence.di

internal actual fun loadOpenAiApiKey(): String? {
    // Priority 1: Environment variable
    System.getenv("OPENAI_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }

    // Priority 2: System property
    System.getProperty("OPENAI_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }

    return null
}
