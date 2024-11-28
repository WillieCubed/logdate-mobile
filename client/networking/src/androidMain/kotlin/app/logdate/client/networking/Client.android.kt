package app.logdate.client.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging

/**
 * An HTTP client that supports JSON serialization.
 *
 * Logging is enabled by default.
 */
actual val httpClient: HttpClient = HttpClient(Android) {
    install(Logging) {
        logger = Logger.ANDROID
        level = LogLevel.ALL
    }
}