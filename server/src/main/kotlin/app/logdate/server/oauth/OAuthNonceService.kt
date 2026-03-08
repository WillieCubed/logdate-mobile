package app.logdate.server.oauth

import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Issues short-lived server nonces for DPoP-bound OAuth requests.
 */
class OAuthNonceService(
    private val secureRandom: SecureRandom = SecureRandom(),
    private val clock: Clock = Clock.System,
    private val ttl: Duration = 5.minutes,
) {
    @Volatile
    private var current: IssuedNonce = issue()

    /**
     * Returns the currently valid nonce, rotating it when expired.
     */
    fun currentNonce(): String {
        rotateIfExpired()
        return current.value
    }

    /**
     * Forces issuance of a new nonce.
     */
    fun refreshNonce(): String {
        current = issue()
        return current.value
    }

    private fun rotateIfExpired() {
        if (clock.now() >= current.expiresAt) {
            current = issue()
        }
    }

    private fun issue(): IssuedNonce {
        val bytes = ByteArray(NONCE_BYTES)
        secureRandom.nextBytes(bytes)
        return IssuedNonce(
            value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            expiresAt = clock.now() + ttl,
        )
    }

    private data class IssuedNonce(
        val value: String,
        val expiresAt: kotlin.time.Instant,
    )

    private companion object {
        private const val NONCE_BYTES = 24
    }
}
