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
interface SigningKeyRepository {
    suspend fun save(key: StoredSigningKey): StoredSigningKey

    suspend fun findActiveByAccountId(accountId: Uuid): StoredSigningKey?

    suspend fun revokeActiveKeys(accountId: Uuid): Int
}

@OptIn(ExperimentalUuidApi::class)
class InMemorySigningKeyRepository : SigningKeyRepository {
    private val keysById = linkedMapOf<Uuid, StoredSigningKey>()

    override suspend fun save(key: StoredSigningKey): StoredSigningKey {
        keysById[key.id] = key
        return key
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
}
