package app.logdate.server.logdate

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class JournalMergeOperation(
    val operationId: String,
    val sourceId: String,
    val destinationId: String,
    val submittedContentIds: List<String>,
    val contentIds: List<String>,
    val started: Boolean = false,
    val completed: Boolean = false,
    val deletedAt: Long? = null,
    val tombstoneVersion: Long? = null,
    val supersededOperationIds: List<String> = emptyList(),
)

interface JournalMergeStore {
    suspend fun <T> withAccountLock(
        userId: UUID,
        action: suspend () -> T,
    ): T

    suspend fun operations(userId: UUID): List<JournalMergeOperation>

    suspend fun removeUnstarted(
        userId: UUID,
        operationId: String,
    )

    suspend fun replaceIncomplete(
        userId: UUID,
        previousOperationId: String,
        replacement: JournalMergeOperation,
    )

    suspend fun save(
        userId: UUID,
        operation: JournalMergeOperation,
    )

    suspend fun purgeTombstones(
        userId: UUID?,
        olderThan: Long,
    )
}

class InMemoryJournalMergeStore : JournalMergeStore {
    private val locks = ConcurrentHashMap<UUID, Mutex>()
    private val records = ConcurrentHashMap<UUID, ConcurrentHashMap<String, JournalMergeOperation>>()

    override suspend fun <T> withAccountLock(
        userId: UUID,
        action: suspend () -> T,
    ): T = locks.getOrPut(userId) { Mutex() }.withLock { action() }

    override suspend fun operations(userId: UUID): List<JournalMergeOperation> = records[userId]?.values?.toList().orEmpty()

    override suspend fun removeUnstarted(
        userId: UUID,
        operationId: String,
    ) {
        records[userId]?.computeIfPresent(operationId) { _, operation -> if (operation.started) operation else null }
    }

    override suspend fun replaceIncomplete(
        userId: UUID,
        previousOperationId: String,
        replacement: JournalMergeOperation,
    ) {
        val operations = records.getOrPut(userId) { ConcurrentHashMap() }
        check(operations[previousOperationId]?.completed == false)
        operations[replacement.operationId] = replacement
        operations.remove(previousOperationId)
    }

    override suspend fun save(
        userId: UUID,
        operation: JournalMergeOperation,
    ) {
        records.getOrPut(userId) { ConcurrentHashMap() }[operation.operationId] = operation
    }

    override suspend fun purgeTombstones(
        userId: UUID?,
        olderThan: Long,
    ) {
        records.forEach { (owner, operations) ->
            if (userId == null || owner == userId) {
                operations.replaceAll { _, operation ->
                    if (operation.completed && (operation.deletedAt ?: Long.MAX_VALUE) < olderThan) {
                        operation.copy(tombstoneVersion = null)
                    } else {
                        operation
                    }
                }
            }
        }
    }
}
