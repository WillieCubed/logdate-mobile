package studio.hypertext.atproto.oauth.client

import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The most recent DPoP nonce each server origin handed out.
 *
 * Servers rotate nonces every few minutes and accept recent ones, so sharing one cache across
 * sessions saves a round trip on most requests. A missing or stale nonce costs one retry, so the
 * cache does not need to be shared between server instances.
 */
internal class DpopNonceCache {
    private val mutex = Mutex()
    private val nonces = mutableMapOf<String, String>()

    suspend fun get(url: String): String? = mutex.withLock { nonces[Url(url).origin()] }

    suspend fun put(
        url: String,
        nonce: String,
    ) {
        mutex.withLock {
            nonces[Url(url).origin()] = nonce
            if (nonces.size > MAX_ORIGINS) nonces.remove(nonces.keys.first())
        }
    }

    private companion object {
        const val MAX_ORIGINS: Int = 1024
    }
}

internal const val DPOP_HEADER: String = "DPoP"
internal const val DPOP_NONCE_HEADER: String = "DPoP-Nonce"
