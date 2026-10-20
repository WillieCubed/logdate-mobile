package app.logdate.client.data.journals

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** Content-free upload intent shares the same durable value as the accepted local mutation. */
@Serializable
internal data class DraftMutationReceipt(
    val receiptId: String,
    val entityId: String,
    val scope: UploadScope,
    val operation: String,
    val predecessors: List<DraftMutationPredecessor>,
)

@Serializable
internal data class DraftMutationPredecessor(
    val operationId: String?,
    val operation: String,
)

internal class DraftMutationStore(
    private val storage: KeyValueStorage,
    private val json: Json,
    private val metadata: SyncMetadataService?,
    private val transactions: SyncTransactionManager,
) {
    suspend fun prepare(
        state: ArchiveDraftStorageState,
        id: Uuid,
        operation: PendingOperation,
        scope: DraftRecoveryScope,
    ): ArchiveDraftStorageState {
        val upload = UploadScope(checkNotNull(scope.outboxOwnerId), checkNotNull(scope.outboxServerOrigin))
        require(upload.ownerId.isNotBlank() && upload.serverOrigin.isNotBlank())
        val prior = checkNotNull(metadata).getPendingUploadForScope(EntityType.DRAFT, id.toString(), upload)
        val previous = state.pendingMutations.find { it.entityId == id.toString() && it.scope == upload }
        val predecessors =
            previous?.predecessors.orEmpty() +
                listOfNotNull(prior?.let { DraftMutationPredecessor(it.operationId, it.operation.name) })
        val receipt =
            DraftMutationReceipt(
                Uuid.random().toString(),
                id.toString(),
                upload,
                operation.name,
                predecessors.distinct(),
            )
        return state.copy(
            pendingMutations =
                state.pendingMutations.filterNot { it.entityId == receipt.entityId && it.scope == receipt.scope } + receipt,
        )
    }

    /** Caller holds transaction then draft mutex; SQL acknowledgment clears intent only after the outer commit. */
    suspend fun flush(
        key: String,
        cleanup: suspend (DraftMutationReceipt) -> Boolean = { true },
    ) {
        val service = metadata ?: return
        val state = read(key)
        for (receipt in state.pendingMutations) {
            val pending = service.getPendingUploadForScope(EntityType.DRAFT, receipt.entityId, receipt.scope)
            // A later local outbox mutation wins over replay of an interrupted older receipt.
            if (pending == null ||
                receipt.predecessors.any { it.operationId == pending.operationId && it.operation == pending.operation.name }
            ) {
                service.enqueuePendingForScope(
                    receipt.entityId,
                    EntityType.DRAFT,
                    PendingOperation.fromStorage(receipt.operation),
                    receipt.scope,
                )
            }
            transactions.afterCommit {
                LocalFirstDraftRepository.storageMutex.withLock {
                    val current = read(key)
                    if (current.pendingMutations.none { it.receiptId == receipt.receiptId } || !cleanup(receipt)) return@withLock
                    val cleaned = read(key)
                    val remaining = cleaned.pendingMutations.filterNot { it.receiptId == receipt.receiptId }
                    if (remaining != cleaned.pendingMutations) {
                        storage.putStringOrThrow(key, cleaned.copy(pendingMutations = remaining).encode(json))
                    }
                }
            }
        }
    }

    private suspend fun read(key: String): ArchiveDraftStorageState = ArchiveDraftStorageState.decode(json, storage.getStringOrThrow(key))
}
