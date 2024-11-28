package app.logdate.client.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/**
 * An HTTP client that supports JSON serialization.
 *
 * Logging is enabled by default.
 */
actual val httpClient: HttpClient = HttpClient(Darwin) {
    configureClientDefaults()
}