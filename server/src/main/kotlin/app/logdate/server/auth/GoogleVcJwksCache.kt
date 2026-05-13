package app.logdate.server.auth

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.net.URL
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * In-memory, single-flight cache for the Google Verifiable Credentials JWK set
 * used by [DigitalCredentialVerifier] to verify SD-JWT signatures coming from the
 * Android Digital Credentials email-verification flow.
 *
 * The remote set rarely changes; a 1-hour TTL is plenty. A cache miss for an
 * unknown `kid` forces a refresh (Google may rotate keys without bumping the URL).
 *
 * Thread-safe across coroutines: only one refresh runs at a time.
 */
class GoogleVcJwksCache(
    private val jwksUrl: URL = URI.create("https://verifiablecredentials-pa.googleapis.com/.well-known/vc-public-jwks").toURL(),
    private val ttl: Duration = 1.hours,
    private val clock: () -> Instant = { Clock.System.now() },
    private val loader: (URL) -> JWKSet = { JWKSet.load(it) },
) {
    private val mutex = Mutex()

    @Volatile
    private var cachedSet: JWKSet? = null

    @Volatile
    private var fetchedAt: Instant = Instant.DISTANT_PAST

    /**
     * Returns the JWK matching [kid], refreshing the cached set if it is stale
     * or if the requested kid is missing. Returns null if the kid still cannot be
     * found after a refresh, or if the refresh itself failed.
     */
    suspend fun keyForKid(kid: String): JWK? {
        val now = clock()
        cachedSet?.let { set ->
            if (now - fetchedAt < ttl) {
                set.getKeyByKeyId(kid)?.let { return it }
            }
        }

        return mutex.withLock {
            // Re-check under lock — another coroutine may have refreshed.
            cachedSet?.let { set ->
                if (clock() - fetchedAt < ttl) {
                    set.getKeyByKeyId(kid)?.let { return@withLock it }
                }
            }
            try {
                val fresh = loader(jwksUrl)
                cachedSet = fresh
                fetchedAt = clock()
                fresh.getKeyByKeyId(kid)
            } catch (e: Exception) {
                Napier.e("Failed to load Google VC JWK set from $jwksUrl", e)
                null
            }
        }
    }
}
