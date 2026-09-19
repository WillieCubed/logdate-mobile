package app.logdate.client.networking

import app.logdate.util.UuidSerializer
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.uuid.Uuid

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
        json(
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                serializersModule =
                    SerializersModule {
                        contextual(Uuid::class, UuidSerializer)
                    }
            },
        )
    }
    // Headers only: bodies include whole media uploads, and logging one as text allocates
    // twice its size in a single string.
    install(Logging) {
        logger = NapierLogger
        level = LogLevel.HEADERS
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
}

internal object NapierLogger : Logger {
    override fun log(message: String) {
        Napier.v(tag = "HttpClient", message = message)
    }
}
