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

/**
 * Another operation for [did] already names [prevCid] as its previous operation. Appending this one
 * as well would fork the identity's history, so nothing was stored.
 */
class HostedPlcOperationConflictException(
    val did: String,
    val prevCid: String?,
    cause: Throwable? = null,
) : IllegalStateException("An operation for $did already follows $prevCid", cause)

@OptIn(ExperimentalUuidApi::class)
interface HostedPlcOperationRepository {
    /**
     * Stores [operation]. Saving a row with an existing id replaces it.
     *
     * @throws HostedPlcOperationConflictException when another operation for the same DID already
     *   names the same previous operation, so the log stays one chain.
     */
    suspend fun save(operation: StoredHostedPlcOperation): StoredHostedPlcOperation

    suspend fun listByAccountId(accountId: Uuid): List<StoredHostedPlcOperation>

    suspend fun listByDid(did: String): List<StoredHostedPlcOperation>
}

@OptIn(ExperimentalUuidApi::class)
class InMemoryHostedPlcOperationRepository : HostedPlcOperationRepository {
    private val operationsById = linkedMapOf<Uuid, StoredHostedPlcOperation>()

    override suspend fun save(operation: StoredHostedPlcOperation): StoredHostedPlcOperation =
        synchronized(operationsById) {
            val forks =
                operation.prevCid != null &&
                    operationsById.values.any { it.id != operation.id && it.did == operation.did && it.prevCid == operation.prevCid }
            if (forks) throw HostedPlcOperationConflictException(operation.did, operation.prevCid)
            operationsById[operation.id] = operation
            operation
        }

    override suspend fun listByAccountId(accountId: Uuid): List<StoredHostedPlcOperation> =
        synchronized(operationsById) {
            operationsById.values
                .filter { it.accountId == accountId }
                .sortedBy(StoredHostedPlcOperation::createdAt)
        }

    override suspend fun listByDid(did: String): List<StoredHostedPlcOperation> =
        synchronized(operationsById) {
            operationsById.values
                .filter { it.did == did }
                .sortedBy(StoredHostedPlcOperation::createdAt)
        }
}
