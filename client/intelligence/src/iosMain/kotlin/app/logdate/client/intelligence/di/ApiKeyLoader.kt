package app.logdate.client.intelligence.di

internal actual fun loadOpenAiApiKey(): String? {
    // iOS has no environment variables or JVM system properties.
    return null
}
