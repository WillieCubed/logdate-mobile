package app.logdate.client.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android

/**
 * An HTTP client that supports JSON serialization.
 *
 * Configured only by [configureClientDefaults], like every other platform. This client used to add
 * its own body-level logging on top, which read each upload whole into a string just to log it;
 * a large photo or recording ran the app out of memory part way through a backup.
 */
actual val httpClient: HttpClient =
    HttpClient(Android) {
        configureClientDefaults()
    }
