package app.logdate.server.database

import app.logdate.server.passkeys.RestoreCredentialRepository
import app.logdate.server.passkeys.StoredRestoreCredentialData
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.Base64
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PostgreSQLRestoreCredentialRepository : RestoreCredentialRepository {
    override suspend fun store(
        userId: Uuid,
        credentialId: String,
        publicKey: ByteArray,
        signCount: Long,
    ): Boolean =
        try {
            transaction {
                RestoreCredentialsTable.insert {
                    it[accountId] = userId.toJavaUUID()
                    it[RestoreCredentialsTable.credentialId] = credentialId
                    it[RestoreCredentialsTable.publicKey] = encodePublicKey(publicKey)
                    it[RestoreCredentialsTable.signCount] = signCount
                    it[isActive] = true
                    it[createdAt] = Clock.System.now()
                    it[lastUsedAt] = null
                }
            }
            true
        } catch (e: Exception) {
            false
        }

    override suspend fun findByCredentialId(credentialId: String): StoredRestoreCredentialData? =
        transaction {
            RestoreCredentialsTable
                .selectAll()
                .where {
                    (RestoreCredentialsTable.credentialId eq credentialId) and
                        (RestoreCredentialsTable.isActive eq true)
                }.singleOrNull()
                ?.let { row ->
                    StoredRestoreCredentialData(
                        credentialId = row[RestoreCredentialsTable.credentialId],
                        publicKey = decodePublicKey(row[RestoreCredentialsTable.publicKey]),
                        signCount = row[RestoreCredentialsTable.signCount],
                        userId = row[RestoreCredentialsTable.accountId].toKotlinUuid(),
                    )
                }
        }

    override suspend fun getCredentialIdsForUser(userId: Uuid): List<String> =
        transaction {
            RestoreCredentialsTable
                .selectAll()
                .where {
                    (RestoreCredentialsTable.accountId eq userId.toJavaUUID()) and
                        (RestoreCredentialsTable.isActive eq true)
                }.map { it[RestoreCredentialsTable.credentialId] }
        }

    override suspend fun updateSignCount(
        credentialId: String,
        newSignCount: Long,
    ): Boolean =
        transaction {
            RestoreCredentialsTable.update({ RestoreCredentialsTable.credentialId eq credentialId }) {
                it[signCount] = newSignCount
                it[lastUsedAt] = Clock.System.now()
            } > 0
        }

    override suspend fun deactivate(credentialId: String): Boolean =
        transaction {
            RestoreCredentialsTable.update({ RestoreCredentialsTable.credentialId eq credentialId }) {
                it[isActive] = false
                it[lastUsedAt] = Clock.System.now()
            } > 0
        }

    override suspend fun deactivateAllForUser(userId: Uuid): Boolean =
        transaction {
            RestoreCredentialsTable.update({ RestoreCredentialsTable.accountId eq userId.toJavaUUID() }) {
                it[isActive] = false
            } > 0
        }

    private fun encodePublicKey(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decodePublicKey(value: String): ByteArray =
        runCatching { Base64.getUrlDecoder().decode(value) }.getOrElse { value.toByteArray() }
}
