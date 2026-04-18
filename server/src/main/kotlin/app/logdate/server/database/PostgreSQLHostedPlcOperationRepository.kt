package app.logdate.server.database

import app.logdate.server.identity.HostedPlcOperationRepository
import app.logdate.server.identity.StoredHostedPlcOperation
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PostgreSQLHostedPlcOperationRepository : HostedPlcOperationRepository {
    override suspend fun save(operation: StoredHostedPlcOperation): StoredHostedPlcOperation =
        transaction {
            HostedPlcOperationsTable.insert {
                it[id] = operation.id.toJavaUUID()
                it[accountId] = operation.accountId.toJavaUUID()
                it[did] = operation.did
                it[cid] = operation.cid
                it[prevCid] = operation.prevCid
                it[operationType] = operation.operationType
                it[operationJson] = operation.operationJson
                it[createdAt] = operation.createdAt
            }
            operation
        }

    override suspend fun listByAccountId(accountId: Uuid): List<StoredHostedPlcOperation> =
        transaction {
            HostedPlcOperationsTable
                .selectAll()
                .where { HostedPlcOperationsTable.accountId eq accountId.toJavaUUID() }
                .orderBy(HostedPlcOperationsTable.createdAt, SortOrder.ASC)
                .map { row -> row.toStoredHostedPlcOperation() }
        }

    override suspend fun listByDid(did: String): List<StoredHostedPlcOperation> =
        transaction {
            HostedPlcOperationsTable
                .selectAll()
                .where { HostedPlcOperationsTable.did eq did }
                .orderBy(HostedPlcOperationsTable.createdAt, SortOrder.ASC)
                .map { row -> row.toStoredHostedPlcOperation() }
        }

    private fun ResultRow.toStoredHostedPlcOperation(): StoredHostedPlcOperation =
        StoredHostedPlcOperation(
            id = this[HostedPlcOperationsTable.id].toKotlinUuid(),
            accountId = this[HostedPlcOperationsTable.accountId].toKotlinUuid(),
            did = this[HostedPlcOperationsTable.did],
            cid = this[HostedPlcOperationsTable.cid],
            prevCid = this[HostedPlcOperationsTable.prevCid],
            operationType = this[HostedPlcOperationsTable.operationType],
            operationJson = this[HostedPlcOperationsTable.operationJson],
            createdAt = this[HostedPlcOperationsTable.createdAt],
        )
}
