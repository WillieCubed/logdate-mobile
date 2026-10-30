package app.logdate.client.networking

import app.logdate.util.UuidSerializer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.uuid.Uuid

/**
 * An HTTP client that supports JSON serialization.
 *
 * Transport bodies, headers, and URLs are never sent to logging sinks.
 */
expect val httpClient: HttpClient

/**
 * Configures the client with default settings.
 *
 * Implementers should call this function in their platform-specific client implementations.
 */
internal fun <T : HttpClientEngineConfig> HttpClientConfig<T>.configureClientDefaults() {
    // No overall request limit, since uploads can run long. A server that never accepts the
    // connection, or a transfer that stops moving, fails instead of leaving a spinner; requests
    // that need a tighter answer (sign-in, server checks) set their own limits.
    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
    }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                serializersModule =
                    SerializersModule {
                        contextual(Uuid::class, UuidSerializer)
                    }
            },
        )
    }
    // Ktor's general logger includes private origins, resource IDs, cookies and query values.
    // Operation diagnostics are emitted separately through an allowlisted event contract.
}

private const val CONNECT_TIMEOUT_MILLIS = 15_000L
private const val SOCKET_TIMEOUT_MILLIS = 60_000L
