package app.logdate.server.oauth

import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Instant

/**
 * Storage for the key that signs OAuth access tokens issued to third-party AT Protocol apps.
 *
 * Every server instance must sign with the same key, and the key must outlive restarts; otherwise
 * tokens issued by one instance fail on another, or after a deploy.
 */
interface OAuthSigningKeyRepository {
    /**
     * Returns the stored key. When none exists yet, stores the key from [candidate] first. When
     * several instances race, exactly one candidate is stored and all of them receive it.
     */
    fun getOrCreate(candidate: () -> StoredOAuthSigningKey): StoredOAuthSigningKey
}

/**
 * The OAuth signing key as stored: the private key is encrypted under the deployment's
 * key-encryption key, the public key is plain X.509 SubjectPublicKeyInfo in Base64.
 */
data class StoredOAuthSigningKey(
    val keyId: String,
    val privateKeyEncrypted: String,
    val publicKeySpki: String,
    val createdAt: Instant,
)

/** Keeps the key for the lifetime of one process. Used in tests and when no database is configured. */
class InMemoryOAuthSigningKeyRepository : OAuthSigningKeyRepository {
    private val stored = AtomicReference<StoredOAuthSigningKey?>(null)

    override fun getOrCreate(candidate: () -> StoredOAuthSigningKey): StoredOAuthSigningKey =
        stored.get() ?: stored.updateAndGet { current -> current ?: candidate() }!!
}
