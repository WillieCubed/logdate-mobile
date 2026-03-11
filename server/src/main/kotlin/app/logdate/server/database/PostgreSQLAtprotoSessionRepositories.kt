package app.logdate.server.database

import app.logdate.server.atproto.AtprotoPasswordCredential
import app.logdate.server.atproto.AtprotoPasswordCredentialRepository
import app.logdate.server.atproto.AtprotoSession
import app.logdate.server.atproto.AtprotoSessionRepository
import app.logdate.server.util.toKotlinInstant
import app.logdate.server.util.toKotlinxInstant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PostgreSQLAtprotoPasswordCredentialRepository : AtprotoPasswordCredentialRepository {
    override suspend fun save(credential: AtprotoPasswordCredential): AtprotoPasswordCredential =
        transaction {
            val existing =
                AtprotoPasswordCredentialsTable
                    .selectAll()
                    .where { AtprotoPasswordCredentialsTable.accountId eq credential.accountId.toJavaUUID() }
                    .singleOrNull()

            if (existing == null) {
                AtprotoPasswordCredentialsTable.insert {
                    it[accountId] = credential.accountId.toJavaUUID()
                    it[salt] = credential.salt
                    it[hash] = credential.hash
                    it[iterations] = credential.iterations
                    it[createdAt] =
                        kotlin.time.Instant
                            .fromEpochMilliseconds(credential.createdAtEpochMillis)
                            .toKotlinxInstant()
                    it[updatedAt] =
                        kotlin.time.Instant
                            .fromEpochMilliseconds(credential.updatedAtEpochMillis)
                            .toKotlinxInstant()
                }
            } else {
                AtprotoPasswordCredentialsTable.update({ AtprotoPasswordCredentialsTable.accountId eq credential.accountId.toJavaUUID() }) {
                    it[salt] = credential.salt
                    it[hash] = credential.hash
                    it[iterations] = credential.iterations
                    it[updatedAt] =
                        kotlin.time.Instant
                            .fromEpochMilliseconds(credential.updatedAtEpochMillis)
                            .toKotlinxInstant()
                }
            }
            credential
        }

    override suspend fun findByAccountId(accountId: Uuid): AtprotoPasswordCredential? =
        transaction {
            AtprotoPasswordCredentialsTable
                .selectAll()
                .where { AtprotoPasswordCredentialsTable.accountId eq accountId.toJavaUUID() }
                .singleOrNull()
                ?.toPasswordCredential()
        }
}

@OptIn(ExperimentalUuidApi::class)
class PostgreSQLAtprotoSessionRepository : AtprotoSessionRepository {
    override suspend fun save(session: AtprotoSession): AtprotoSession =
        transaction {
            val existing =
                AtprotoSessionsTable
                    .selectAll()
                    .where { AtprotoSessionsTable.id eq session.id }
                    .singleOrNull()
            if (existing == null) {
                AtprotoSessionsTable.insert {
                    it[id] = session.id
                    it[accountId] = session.accountId.toJavaUUID()
                    it[createdAt] =
                        kotlin.time.Instant
                            .fromEpochMilliseconds(session.createdAtEpochMillis)
                            .toKotlinxInstant()
                    it[refreshExpiresAt] =
                        kotlin.time.Instant
                            .fromEpochMilliseconds(session.refreshExpiresAtEpochMillis)
                            .toKotlinxInstant()
                    it[revokedAt] = session.revokedAtEpochMillis?.let(kotlin.time.Instant::fromEpochMilliseconds)?.toKotlinxInstant()
                }
            } else {
                AtprotoSessionsTable.update({ AtprotoSessionsTable.id eq session.id }) {
                    it[accountId] = session.accountId.toJavaUUID()
                    it[createdAt] =
                        kotlin.time.Instant
                            .fromEpochMilliseconds(session.createdAtEpochMillis)
                            .toKotlinxInstant()
                    it[refreshExpiresAt] =
                        kotlin.time.Instant
                            .fromEpochMilliseconds(session.refreshExpiresAtEpochMillis)
                            .toKotlinxInstant()
                    it[revokedAt] = session.revokedAtEpochMillis?.let(kotlin.time.Instant::fromEpochMilliseconds)?.toKotlinxInstant()
                }
            }
            session
        }

    override suspend fun findById(sessionId: String): AtprotoSession? =
        transaction {
            AtprotoSessionsTable
                .selectAll()
                .where { AtprotoSessionsTable.id eq sessionId }
                .singleOrNull()
                ?.toAtprotoSession()
        }

    override suspend fun revoke(sessionId: String): Boolean =
        transaction {
            val updatedRows =
                AtprotoSessionsTable.update({ AtprotoSessionsTable.id eq sessionId }) {
                    it[revokedAt] =
                        kotlin.time.Clock.System
                            .now()
                            .toKotlinxInstant()
                }
            if (updatedRows == 0) {
                AtprotoSessionsTable.deleteWhere { id eq sessionId } > 0
            } else {
                true
            }
        }
}

@OptIn(ExperimentalUuidApi::class)
private fun ResultRow.toPasswordCredential(): AtprotoPasswordCredential =
    AtprotoPasswordCredential(
        accountId = this[AtprotoPasswordCredentialsTable.accountId].toKotlinUuid(),
        salt = this[AtprotoPasswordCredentialsTable.salt],
        hash = this[AtprotoPasswordCredentialsTable.hash],
        iterations = this[AtprotoPasswordCredentialsTable.iterations],
        createdAtEpochMillis = this[AtprotoPasswordCredentialsTable.createdAt].toKotlinInstant().toEpochMilliseconds(),
        updatedAtEpochMillis = this[AtprotoPasswordCredentialsTable.updatedAt].toKotlinInstant().toEpochMilliseconds(),
    )

@OptIn(ExperimentalUuidApi::class)
private fun ResultRow.toAtprotoSession(): AtprotoSession =
    AtprotoSession(
        id = this[AtprotoSessionsTable.id],
        accountId = this[AtprotoSessionsTable.accountId].toKotlinUuid(),
        createdAtEpochMillis = this[AtprotoSessionsTable.createdAt].toKotlinInstant().toEpochMilliseconds(),
        refreshExpiresAtEpochMillis = this[AtprotoSessionsTable.refreshExpiresAt].toKotlinInstant().toEpochMilliseconds(),
        revokedAtEpochMillis = this[AtprotoSessionsTable.revokedAt]?.toKotlinInstant()?.toEpochMilliseconds(),
    )
