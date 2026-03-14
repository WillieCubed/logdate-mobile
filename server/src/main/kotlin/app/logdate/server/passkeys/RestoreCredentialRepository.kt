package app.logdate.server.passkeys

import java.util.concurrent.ConcurrentHashMap
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
    private val credentials = ConcurrentHashMap<String, Pair<Uuid, StoredRestoreCredentialData>>()
    private val activeFlags = ConcurrentHashMap<String, Boolean>()

    override suspend fun store(
        userId: Uuid,
        credentialId: String,
        publicKey: ByteArray,
        signCount: Long,
    ): Boolean {
        val data = StoredRestoreCredentialData(credentialId, publicKey, signCount, userId)
        credentials[credentialId] = userId to data
        activeFlags[credentialId] = true
        return true
    }

    override suspend fun findByCredentialId(credentialId: String): StoredRestoreCredentialData? {
        if (activeFlags[credentialId] != true) return null
        return credentials[credentialId]?.second
    }

    override suspend fun getCredentialIdsForUser(userId: Uuid): List<String> =
        credentials
            .filter { (id, pair) -> pair.first == userId && activeFlags[id] == true }
            .keys
            .toList()

    override suspend fun updateSignCount(
        credentialId: String,
        newSignCount: Long,
    ): Boolean {
        val (userId, data) = credentials[credentialId] ?: return false
        credentials[credentialId] = userId to data.copy(signCount = newSignCount)
        return true
    }

    override suspend fun deactivate(credentialId: String): Boolean {
        if (!credentials.containsKey(credentialId)) return false
        activeFlags[credentialId] = false
        return true
    }

    override suspend fun deactivateAllForUser(userId: Uuid): Boolean {
        credentials
            .filter { (_, pair) -> pair.first == userId }
            .keys
            .forEach { activeFlags[it] = false }
        return true
    }
}
