package app.logdate.server.oauth

import app.logdate.server.identity.EncryptedPrivateKey
import app.logdate.server.identity.EncryptedPrivateKeyStore
import kotlin.time.Instant

/**
 * Storage for the keys that sign OAuth access tokens issued to third-party AT Protocol apps.
 *
 * Every server instance must sign with the same key, and the key must outlive restarts; otherwise
 * tokens issued by one instance fail on another, or after a deploy. Keys rotate through three
 * slots: the "next" key is accepted and published before anyone signs with it, the "active" key
 * signs, and the "retiring" key keeps verifying the tokens it signed until they expire.
 */
interface OAuthSigningKeyRepository : EncryptedPrivateKeyStore {
    /**
     * Returns the active key. When none exists yet, stores the key from [candidate] first. When
     * several instances race, exactly one candidate is stored and all of them receive it.
     */
    fun getOrCreate(candidate: () -> StoredOAuthSigningKey): StoredOAuthSigningKey

    /** Returns every stored key by slot. */
    fun keySet(): OAuthSigningKeySet

    /**
     * Stores [candidate] as the next key unless one is already staged, and returns the staged key.
     * Racing instances all receive the same key.
     */
    fun stageNext(candidate: () -> StoredOAuthSigningKey): StoredOAuthSigningKey

    /**
     * Makes the next key active and the active key retiring until [retireAfter], replacing any
     * earlier retiring key. Applies only while the active and next keys are still
     * [expectedActiveKeyId] and [expectedNextKeyId], so racing instances promote once. Returns
     * whether this call promoted.
     */
    fun promoteNext(
        expectedActiveKeyId: String,
        expectedNextKeyId: String,
        retireAfter: Instant,
    ): Boolean

    /** Deletes the retiring key once [now] is past its retire time. Returns whether one was deleted. */
    fun deleteRetired(now: Instant): Boolean
}

/**
 * An OAuth signing key as stored: the private key is encrypted under the deployment's
 * key-encryption key, the public key is plain X.509 SubjectPublicKeyInfo in Base64. [retireAfter]
 * is set only on the retiring key.
 */
data class StoredOAuthSigningKey(
    val keyId: String,
    val privateKeyEncrypted: String,
    val publicKeySpki: String,
    val createdAt: Instant,
    val retireAfter: Instant? = null,
)

/** The stored OAuth signing keys, by slot. */
data class OAuthSigningKeySet(
    val active: StoredOAuthSigningKey?,
    val next: StoredOAuthSigningKey?,
    val retiring: StoredOAuthSigningKey?,
)

/** Keeps the keys for the lifetime of one process. Used in tests and when no database is configured. */
class InMemoryOAuthSigningKeyRepository : OAuthSigningKeyRepository {
    private var keys = OAuthSigningKeySet(active = null, next = null, retiring = null)

    @Synchronized
    override fun getOrCreate(candidate: () -> StoredOAuthSigningKey): StoredOAuthSigningKey =
        keys.active ?: candidate().also { keys = keys.copy(active = it) }

    @Synchronized
    override fun keySet(): OAuthSigningKeySet = keys

    @Synchronized
    override fun stageNext(candidate: () -> StoredOAuthSigningKey): StoredOAuthSigningKey =
        keys.next ?: candidate().also { keys = keys.copy(next = it) }

    @Synchronized
    override fun promoteNext(
        expectedActiveKeyId: String,
        expectedNextKeyId: String,
        retireAfter: Instant,
    ): Boolean {
        val active = keys.active?.takeIf { it.keyId == expectedActiveKeyId } ?: return false
        val next = keys.next?.takeIf { it.keyId == expectedNextKeyId } ?: return false
        keys = OAuthSigningKeySet(active = next, next = null, retiring = active.copy(retireAfter = retireAfter))
        return true
    }

    @Synchronized
    override fun deleteRetired(now: Instant): Boolean {
        val retireAfter = keys.retiring?.retireAfter ?: return false
        if (retireAfter > now) return false
        keys = keys.copy(retiring = null)
        return true
    }

    override suspend fun encryptedPrivateKeys(): List<EncryptedPrivateKey> =
        keySet().let { listOfNotNull(it.active, it.next, it.retiring) }.map {
            EncryptedPrivateKey(id = it.keyId, privateKeyEncrypted = it.privateKeyEncrypted)
        }

    override suspend fun replaceEncryptedPrivateKey(
        id: String,
        expected: String,
        replacement: String,
    ): Boolean = replaceEncryptedPrivateKeyNow(id, expected, replacement)

    @Synchronized
    private fun replaceEncryptedPrivateKeyNow(
        id: String,
        expected: String,
        replacement: String,
    ): Boolean {
        fun StoredOAuthSigningKey?.replaced() =
            this?.takeIf { it.keyId == id && it.privateKeyEncrypted == expected }?.copy(privateKeyEncrypted = replacement)
        val updated =
            OAuthSigningKeySet(
                active = keys.active.replaced() ?: keys.active,
                next = keys.next.replaced() ?: keys.next,
                retiring = keys.retiring.replaced() ?: keys.retiring,
            )
        if (updated == keys) return false
        keys = updated
        return true
    }
}
