package app.logdate.server.identity

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class StoredHostedPlcOperation(
    val id: Uuid,
    val accountId: Uuid,
    val did: String,
    val cid: String? = null,
    val prevCid: String? = null,
    val operationType: String,
    val operationJson: String,
    val createdAt: Instant = Clock.System.now(),
)

@OptIn(ExperimentalUuidApi::class)
interface HostedPlcOperationRepository {
    suspend fun save(operation: StoredHostedPlcOperation): StoredHostedPlcOperation

    suspend fun listByAccountId(accountId: Uuid): List<StoredHostedPlcOperation>

    suspend fun listByDid(did: String): List<StoredHostedPlcOperation>
}

@OptIn(ExperimentalUuidApi::class)
class InMemoryHostedPlcOperationRepository : HostedPlcOperationRepository {
    private val operationsById = linkedMapOf<Uuid, StoredHostedPlcOperation>()

    override suspend fun save(operation: StoredHostedPlcOperation): StoredHostedPlcOperation {
        operationsById[operation.id] = operation
        return operation
    }

    override suspend fun listByAccountId(accountId: Uuid): List<StoredHostedPlcOperation> =
        operationsById.values
            .filter { it.accountId == accountId }
            .sortedBy(StoredHostedPlcOperation::createdAt)

    override suspend fun listByDid(did: String): List<StoredHostedPlcOperation> =
        operationsById.values
            .filter { it.did == did }
            .sortedBy(StoredHostedPlcOperation::createdAt)
}
