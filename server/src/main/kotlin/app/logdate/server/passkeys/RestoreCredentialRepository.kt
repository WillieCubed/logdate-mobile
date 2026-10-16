package app.logdate.server.passkeys

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface RestoreCredentialRepository {
    suspend fun store(
        userId: Uuid,
        credentialId: String,
        publicKey: ByteArray,
        signCount: Long,
    ): Boolean

    suspend fun findByCredentialId(credentialId: String): StoredRestoreCredentialData?

    suspend fun getCredentialIdsForUser(userId: Uuid): List<String>

    suspend fun updateSignCount(
        credentialId: String,
        newSignCount: Long,
    ): Boolean

    suspend fun deactivate(credentialId: String): Boolean

    /** Atomically update the verified active record; counter changes or revocation invalidate stale verification. */
    suspend fun recordAuthentication(
        expected: StoredRestoreCredentialData,
        newSignCount: Long,
    ): Boolean

    suspend fun deactivateAllForUser(userId: Uuid): Boolean
}

@OptIn(ExperimentalUuidApi::class)
data class StoredRestoreCredentialData(
    val credentialId: String,
    val publicKey: ByteArray,
    val signCount: Long,
    val userId: Uuid,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StoredRestoreCredentialData
        if (credentialId != other.credentialId) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (signCount != other.signCount) return false
        if (userId != other.userId) return false
        return true
    }

    override fun hashCode(): Int {
        var result = credentialId.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + signCount.hashCode()
        result = 31 * result + userId.hashCode()
        return result
    }
}

@OptIn(ExperimentalUuidApi::class)
class InMemoryRestoreCredentialRepository : RestoreCredentialRepository {
    private val lock = Any()
    private val credentials = mutableMapOf<String, StoredRestoreCredentialData>()
    private val activeFlags = mutableMapOf<String, Boolean>()

    override suspend fun store(
        userId: Uuid,
        credentialId: String,
        publicKey: ByteArray,
        signCount: Long,
    ): Boolean =
        synchronized(lock) {
            if (credentials.containsKey(credentialId)) return@synchronized false
            credentials[credentialId] = StoredRestoreCredentialData(credentialId, publicKey.copyOf(), signCount, userId)
            activeFlags[credentialId] = true
            true
        }

    override suspend fun findByCredentialId(credentialId: String): StoredRestoreCredentialData? =
        synchronized(lock) {
            if (activeFlags[credentialId] != true) return@synchronized null
            credentials[credentialId]?.let { it.copy(publicKey = it.publicKey.copyOf()) }
        }

    override suspend fun getCredentialIdsForUser(userId: Uuid): List<String> =
        synchronized(lock) {
            credentials.filter { (id, data) -> data.userId == userId && activeFlags[id] == true }.keys.toList()
        }

    override suspend fun updateSignCount(
        credentialId: String,
        newSignCount: Long,
    ): Boolean =
        synchronized(lock) {
            val data = credentials[credentialId] ?: return@synchronized false
            credentials[credentialId] = data.copy(signCount = newSignCount)
            true
        }

    override suspend fun deactivate(credentialId: String): Boolean =
        synchronized(lock) {
            if (!credentials.containsKey(credentialId)) return@synchronized false
            activeFlags[credentialId] = false
            true
        }

    override suspend fun recordAuthentication(
        expected: StoredRestoreCredentialData,
        newSignCount: Long,
    ): Boolean =
        synchronized(lock) {
            if (activeFlags[expected.credentialId] != true || credentials[expected.credentialId] != expected) return@synchronized false
            credentials[expected.credentialId] = expected.copy(publicKey = expected.publicKey.copyOf(), signCount = newSignCount)
            true
        }

    override suspend fun deactivateAllForUser(userId: Uuid): Boolean =
        synchronized(lock) {
            credentials.filter { (_, data) -> data.userId == userId }.keys.forEach { activeFlags[it] = false }
            true
        }
}
