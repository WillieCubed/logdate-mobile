package app.logdate.server.identity

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class StoredSigningKey(
    val id: Uuid,
    val accountId: Uuid,
    val purpose: String = "atproto",
    val algorithm: String = "Ed25519",
    val publicKeyMultibase: String,
    val privateKeyEncrypted: String,
    val createdAt: Instant,
    val revokedAt: Instant? = null,
)

@OptIn(ExperimentalUuidApi::class)
interface SigningKeyRepository : EncryptedPrivateKeyStore {
    suspend fun save(signingKey: StoredSigningKey): StoredSigningKey

    suspend fun findActiveByAccountId(accountId: Uuid): StoredSigningKey?

    suspend fun revokeActiveKeys(accountId: Uuid): Int
}

@OptIn(ExperimentalUuidApi::class)
class InMemorySigningKeyRepository : SigningKeyRepository {
    private val keysById = linkedMapOf<Uuid, StoredSigningKey>()

    override suspend fun save(signingKey: StoredSigningKey): StoredSigningKey {
        keysById[signingKey.id] = signingKey
        return signingKey
    }

    override suspend fun findActiveByAccountId(accountId: Uuid): StoredSigningKey? =
        keysById.values
            .filter { it.accountId == accountId && it.revokedAt == null }
            .maxByOrNull(StoredSigningKey::createdAt)

    override suspend fun revokeActiveKeys(accountId: Uuid): Int {
        val revokedAt = Clock.System.now()
        val activeKeys = keysById.values.filter { it.accountId == accountId && it.revokedAt == null }
        activeKeys.forEach { key ->
            keysById[key.id] = key.copy(revokedAt = revokedAt)
        }
        return activeKeys.size
    }

    override suspend fun encryptedPrivateKeys(): List<EncryptedPrivateKey> =
        keysById.values.map { EncryptedPrivateKey(id = it.id.toString(), privateKeyEncrypted = it.privateKeyEncrypted) }

    override suspend fun replaceEncryptedPrivateKey(
        id: String,
        expected: String,
        replacement: String,
    ): Boolean {
        val key = keysById[Uuid.parse(id)]?.takeIf { it.privateKeyEncrypted == expected } ?: return false
        keysById[key.id] = key.copy(privateKeyEncrypted = replacement)
        return true
    }
}
