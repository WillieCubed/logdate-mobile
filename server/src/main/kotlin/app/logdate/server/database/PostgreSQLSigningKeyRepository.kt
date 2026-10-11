package app.logdate.server.database

import app.logdate.server.identity.EncryptedPrivateKey
import app.logdate.server.identity.SigningKeyRepository
import app.logdate.server.identity.StoredSigningKey
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PostgreSQLSigningKeyRepository : SigningKeyRepository {
    override suspend fun save(signingKey: StoredSigningKey): StoredSigningKey =
        transaction {
            SigningKeysTable.insert {
                it[id] = signingKey.id.toJavaUUID()
                it[accountId] = signingKey.accountId.toJavaUUID()
                it[purpose] = signingKey.purpose
                it[algorithm] = signingKey.algorithm
                it[publicKeyMultibase] = signingKey.publicKeyMultibase
                it[privateKeyEncrypted] = signingKey.privateKeyEncrypted
                it[createdAt] = signingKey.createdAt
                it[revokedAt] = signingKey.revokedAt
            }
            signingKey
        }

    override suspend fun findActiveByAccountId(accountId: Uuid): StoredSigningKey? =
        transaction {
            SigningKeysTable
                .selectAll()
                .where {
                    (SigningKeysTable.accountId eq accountId.toJavaUUID()) and
                        (SigningKeysTable.revokedAt eq null)
                }.orderBy(SigningKeysTable.createdAt, SortOrder.DESC)
                .singleOrNull()
                ?.toStoredSigningKey()
        }

    override suspend fun revokeActiveKeys(accountId: Uuid): Int =
        transaction {
            SigningKeysTable.update({
                (SigningKeysTable.accountId eq accountId.toJavaUUID()) and
                    (SigningKeysTable.revokedAt eq null)
            }) {
                it[revokedAt] =
                    kotlin.time.Clock.System
                        .now()
            }
        }

    override suspend fun encryptedPrivateKeys(): List<EncryptedPrivateKey> =
        transaction {
            SigningKeysTable
                .select(SigningKeysTable.id, SigningKeysTable.privateKeyEncrypted)
                .map {
                    EncryptedPrivateKey(
                        id = it[SigningKeysTable.id].toString(),
                        privateKeyEncrypted = it[SigningKeysTable.privateKeyEncrypted],
                    )
                }
        }

    override suspend fun replaceEncryptedPrivateKey(
        id: String,
        expected: String,
        replacement: String,
    ): Boolean =
        transaction {
            SigningKeysTable.update({
                (SigningKeysTable.id eq Uuid.parse(id).toJavaUUID()) and
                    (SigningKeysTable.privateKeyEncrypted eq expected)
            }) {
                it[privateKeyEncrypted] = replacement
            } == 1
        }

    private fun ResultRow.toStoredSigningKey(): StoredSigningKey =
        StoredSigningKey(
            id = this[SigningKeysTable.id].toKotlinUuid(),
            accountId = this[SigningKeysTable.accountId].toKotlinUuid(),
            purpose = this[SigningKeysTable.purpose],
            algorithm = this[SigningKeysTable.algorithm],
            publicKeyMultibase = this[SigningKeysTable.publicKeyMultibase],
            privateKeyEncrypted = this[SigningKeysTable.privateKeyEncrypted],
            createdAt = this[SigningKeysTable.createdAt],
            revokedAt = this[SigningKeysTable.revokedAt],
        )
}
