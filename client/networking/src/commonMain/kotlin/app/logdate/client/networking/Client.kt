package app.logdate.client.networking

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * An HTTP client that supports JSON serialization.
 *
 * Logging is enabled by default.
 */
expect val httpClient: HttpClient

/**
 * Configures the client with default settings.
 *
 * Implementers should call this function in their platform-specific client implementations.
 */
internal fun <T : HttpClientEngineConfig> HttpClientConfig<T>.configureClientDefaults() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }
    install(Logging) {
        logger = NapierLogger
        level = LogLevel.HEADERS
    }
}

internal object NapierLogger : Logger {
    override fun log(message: String) {
        Napier.v(tag = "HttpClient", message = message)
    }
}