package app.logdate.server.database

import app.logdate.server.passkeys.PasskeyRepository
import app.logdate.server.passkeys.StoredPasskeyData
import app.logdate.shared.model.PasskeyInfo
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PostgreSQLPasskeyRepository : PasskeyRepository {
    
    override suspend fun storePasskey(
        userId: Uuid,
        credentialId: String,
        publicKey: ByteArray,
        signCount: Long,
        info: PasskeyInfo
    ): Boolean {
        return try {
            transaction {
                PasskeysTable.insert {
                    it[id] = info.id.toJavaUUID()
                    it[accountId] = userId.toJavaUUID()
                    it[PasskeysTable.credentialId] = credentialId
                    it[PasskeysTable.publicKey] = publicKey.toString() // Convert to string for text storage
                    it[PasskeysTable.signCount] = signCount
                    it[nickname] = info.nickname
                    it[deviceType] = info.deviceType
                    it[createdAt] = info.createdAt
                    it[lastUsedAt] = info.lastUsedAt
                    it[isActive] = info.isActive
                    it[webauthnData] = "{}"
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getPasskeyByCredentialId(credentialId: String): Pair<Uuid, StoredPasskeyData>? {
        return transaction {
            PasskeysTable.selectAll()
                .where { PasskeysTable.credentialId eq credentialId }
                .singleOrNull()
                ?.let { row ->
                    val userId = row[PasskeysTable.accountId].toKotlinUuid()
                    val storedData = StoredPasskeyData(
                        credentialId = row[PasskeysTable.credentialId],
                        publicKey = row[PasskeysTable.publicKey].toByteArray(), // Convert back to bytes
                        signCount = row[PasskeysTable.signCount],
                        info = row.toPasskeyInfo(),
                        userId = userId
                    )
                    userId to storedData
                }
        }
    }
    
    override suspend fun getPasskeysForUser(userId: Uuid): List<PasskeyInfo> {
        return transaction {
            PasskeysTable.selectAll()
                .where { (PasskeysTable.accountId eq userId.toJavaUUID()) and (PasskeysTable.isActive eq true) }
                .orderBy(PasskeysTable.lastUsedAt, SortOrder.DESC)
                .map { it.toPasskeyInfo() }
        }
    }
    
    override suspend fun updateSignCount(credentialId: String, newSignCount: Long): Boolean {
        return transaction {
            val updatedRows = PasskeysTable.update({ PasskeysTable.credentialId eq credentialId }) {
                it[signCount] = newSignCount
                it[lastUsedAt] = Clock.System.now()
            }
            updatedRows > 0
        }
    }
    
    override suspend fun deactivatePasskey(credentialId: String, userId: Uuid): Boolean {
        return transaction {
            val updatedRows = PasskeysTable.update({ 
                (PasskeysTable.credentialId eq credentialId) and (PasskeysTable.accountId eq userId.toJavaUUID())
            }) {
                it[isActive] = false
            }
            updatedRows > 0
        }
    }
    
    override suspend fun getCredentialIdsForUser(userId: Uuid): List<String> {
        return transaction {
            PasskeysTable.selectAll()
                .where { (PasskeysTable.accountId eq userId.toJavaUUID()) and (PasskeysTable.isActive eq true) }
                .map { it[PasskeysTable.credentialId] }
        }
    }
    
    override suspend fun credentialExists(credentialId: String): Boolean {
        return transaction {
            PasskeysTable.selectAll()
                .where { PasskeysTable.credentialId eq credentialId }
                .count() > 0
        }
    }
    
    // Additional utility methods for internal use
    suspend fun findById(passkeyId: Uuid): PasskeyInfo? {
        return transaction {
            PasskeysTable.selectAll()
                .where { PasskeysTable.id eq passkeyId.toJavaUUID() }
                .singleOrNull()
                ?.toPasskeyInfo()
        }
    }
    
    suspend fun findByCredentialId(credentialId: String): PasskeyInfo? {
        return transaction {
            PasskeysTable.selectAll()
                .where { PasskeysTable.credentialId eq credentialId }
                .singleOrNull()
                ?.toPasskeyInfo()
        }
    }
    
    suspend fun findByAccountId(accountId: Uuid): List<PasskeyInfo> {
        return transaction {
            PasskeysTable.selectAll()
                .where { PasskeysTable.accountId eq accountId.toJavaUUID() }
                .orderBy(PasskeysTable.createdAt, SortOrder.DESC)
                .map { it.toPasskeyInfo() }
        }
    }
    
    suspend fun findActiveByAccountId(accountId: Uuid): List<PasskeyInfo> {
        return getPasskeysForUser(accountId)
    }
    
    suspend fun updateLastUsed(credentialId: String): Boolean {
        return transaction {
            val updatedRows = PasskeysTable.update({ PasskeysTable.credentialId eq credentialId }) {
                it[lastUsedAt] = Clock.System.now()
            }
            updatedRows > 0
        }
    }
    
    suspend fun deletePasskey(passkeyId: Uuid): Boolean {
        return transaction {
            val deletedRows = PasskeysTable.deleteWhere { id eq passkeyId.toJavaUUID() }
            deletedRows > 0
        }
    }
    
    suspend fun getCredentialIdsForAccount(accountId: Uuid): List<String> {
        return getCredentialIdsForUser(accountId)
    }
    
    /**
     * Save a passkey with additional WebAuthn data (public key, sign count, etc.)
     * This is used by the WebAuthn service to store cryptographic data.
     */
    suspend fun saveWithWebAuthnData(
        accountId: Uuid,
        passkey: PasskeyInfo,
        publicKey: String,
        signCount: Long,
        webauthnData: String = "{}"
    ): PasskeyInfo {
        return transaction {
            PasskeysTable.insert {
                it[id] = passkey.id.toJavaUUID()
                it[this.accountId] = accountId.toJavaUUID()
                it[credentialId] = passkey.credentialId
                it[this.publicKey] = publicKey
                it[this.signCount] = signCount
                it[nickname] = passkey.nickname
                it[deviceType] = passkey.deviceType
                it[createdAt] = passkey.createdAt
                it[lastUsedAt] = passkey.lastUsedAt
                it[isActive] = passkey.isActive
                it[this.webauthnData] = webauthnData
            }
            passkey
        }
    }
    
    /**
     * Get the stored public key and sign count for a credential
     */
    suspend fun getWebAuthnData(credentialId: String): Triple<String, Long, String>? {
        return transaction {
            PasskeysTable.selectAll()
                .where { PasskeysTable.credentialId eq credentialId }
                .singleOrNull()
                ?.let { row ->
                    Triple(
                        row[PasskeysTable.publicKey],
                        row[PasskeysTable.signCount],
                        row[PasskeysTable.webauthnData]
                    )
                }
        }
    }
    
    private fun ResultRow.toPasskeyInfo(): PasskeyInfo {
        return PasskeyInfo(
            id = this[PasskeysTable.id].toKotlinUuid(),
            credentialId = this[PasskeysTable.credentialId],
            nickname = this[PasskeysTable.nickname],
            deviceType = this[PasskeysTable.deviceType],
            createdAt = this[PasskeysTable.createdAt],
            lastUsedAt = this[PasskeysTable.lastUsedAt],
            isActive = this[PasskeysTable.isActive]
        )
    }
}