package app.logdate.server.database

import app.logdate.server.identity.SigningKeyRepository
import app.logdate.server.identity.StoredSigningKey
import app.logdate.server.util.toKotlinInstant
import app.logdate.server.util.toKotlinxInstant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
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
                it[createdAt] = signingKey.createdAt.toKotlinxInstant()
                it[revokedAt] = signingKey.revokedAt?.toKotlinxInstant()
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
                        .toKotlinxInstant()
            }
        }

    private fun ResultRow.toStoredSigningKey(): StoredSigningKey =
        StoredSigningKey(
            id = this[SigningKeysTable.id].toKotlinUuid(),
            accountId = this[SigningKeysTable.accountId].toKotlinUuid(),
            purpose = this[SigningKeysTable.purpose],
            algorithm = this[SigningKeysTable.algorithm],
            publicKeyMultibase = this[SigningKeysTable.publicKeyMultibase],
            privateKeyEncrypted = this[SigningKeysTable.privateKeyEncrypted],
            createdAt = this[SigningKeysTable.createdAt].toKotlinInstant(),
            revokedAt = this[SigningKeysTable.revokedAt]?.toKotlinInstant(),
        )
}
