package app.logdate.server.passkeys

import app.logdate.shared.model.PasskeyInfo
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Repository interface for managing passkey storage and retrieval.
 * 
 * This abstracts the storage layer for passkeys, allowing for different implementations
 * such as in-memory storage for development or database storage for production.
 */
@OptIn(ExperimentalUuidApi::class)
interface PasskeyRepository {
    /**
     * Store a new passkey for a user.
     */
    suspend fun storePasskey(userId: Uuid, credentialId: String, publicKey: ByteArray, signCount: Long, info: PasskeyInfo): Boolean
    
    /**
     * Retrieve a passkey by credential ID.
     */
    suspend fun getPasskeyByCredentialId(credentialId: String): Pair<Uuid, StoredPasskeyData>?
    
    /**
     * Get all active passkeys for a user.
     */
    suspend fun getPasskeysForUser(userId: Uuid): List<PasskeyInfo>
    
    /**
     * Update the sign count for a passkey after successful authentication.
     */
    suspend fun updateSignCount(credentialId: String, newSignCount: Long): Boolean
    
    /**
     * Mark a passkey as inactive (soft delete).
     */
    suspend fun deactivatePasskey(credentialId: String, userId: Uuid): Boolean
    
    /**
     * Get credential IDs for a user (for exclude/allow lists).
     */
    suspend fun getCredentialIdsForUser(userId: Uuid): List<String>
    
    /**
     * Check if a credential ID already exists.
     */
    suspend fun credentialExists(credentialId: String): Boolean
}

/**
 * Data class representing stored passkey information.
 */
@OptIn(ExperimentalUuidApi::class)
data class StoredPasskeyData(
    val credentialId: String,
    val publicKey: ByteArray,
    val signCount: Long,
    val info: PasskeyInfo,
    val userId: Uuid
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as StoredPasskeyData
        
        if (credentialId != other.credentialId) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (signCount != other.signCount) return false
        if (info != other.info) return false
        if (userId != other.userId) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = credentialId.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + signCount.hashCode()
        result = 31 * result + info.hashCode()
        result = 31 * result + userId.hashCode()
        return result
    }
}

/**
 * In-memory implementation of PasskeyRepository for development and testing.
 */
@OptIn(ExperimentalUuidApi::class)
class InMemoryPasskeyRepository : PasskeyRepository {
    private val passkeys = mutableMapOf<String, StoredPasskeyData>()
    private val userPasskeys = mutableMapOf<Uuid, MutableList<String>>()
    
    override suspend fun storePasskey(
        userId: Uuid,
        credentialId: String,
        publicKey: ByteArray,
        signCount: Long,
        info: PasskeyInfo
    ): Boolean {
        return try {
            val storedData = StoredPasskeyData(
                credentialId = credentialId,
                publicKey = publicKey,
                signCount = signCount,
                info = info,
                userId = userId
            )
            
            passkeys[credentialId] = storedData
            userPasskeys.computeIfAbsent(userId) { mutableListOf() }.add(credentialId)
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getPasskeyByCredentialId(credentialId: String): Pair<Uuid, StoredPasskeyData>? {
        val passkey = passkeys[credentialId] ?: return null
        return if (passkey.info.isActive) {
            passkey.userId to passkey
        } else {
            null
        }
    }
    
    override suspend fun getPasskeysForUser(userId: Uuid): List<PasskeyInfo> {
        val credentialIds = userPasskeys[userId] ?: return emptyList()
        return credentialIds.mapNotNull { credentialId ->
            passkeys[credentialId]?.let { passkey ->
                if (passkey.info.isActive) passkey.info else null
            }
        }
    }
    
    override suspend fun updateSignCount(credentialId: String, newSignCount: Long): Boolean {
        val passkey = passkeys[credentialId] ?: return false
        passkeys[credentialId] = passkey.copy(signCount = newSignCount)
        return true
    }
    
    override suspend fun deactivatePasskey(credentialId: String, userId: Uuid): Boolean {
        val passkey = passkeys[credentialId] ?: return false
        if (passkey.userId != userId) return false
        
        passkeys[credentialId] = passkey.copy(
            info = passkey.info.copy(isActive = false)
        )
        return true
    }
    
    override suspend fun getCredentialIdsForUser(userId: Uuid): List<String> {
        val credentialIds = userPasskeys[userId] ?: return emptyList()
        return credentialIds.filter { credentialId ->
            passkeys[credentialId]?.info?.isActive == true
        }
    }
    
    override suspend fun credentialExists(credentialId: String): Boolean {
        return passkeys.containsKey(credentialId)
    }
}