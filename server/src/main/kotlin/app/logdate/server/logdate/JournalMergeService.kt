package app.logdate.server.logdate

import app.logdate.shared.model.sync.JournalMergeRequest
import app.logdate.shared.model.sync.JournalMergeResponse
import java.util.UUID

/** Coordinates resumable work; the repository and store own canonical and operation persistence. */
internal class JournalMergeService(
    private val repository: LogDateCollectionsRepository,
    private val store: JournalMergeStore,
) {
    suspend fun merge(
        userId: UUID,
        sourceId: String,
        request: JournalMergeRequest,
    ): JournalMergeResponse =
        store.withAccountLock(userId) {
            require(sourceId.isNotBlank() && request.operationId.isNotBlank() && request.destinationId.isNotBlank()) {
                "Merge IDs must be non-empty"
            }
            require(request.contentIds.none(String::isBlank)) { "Content IDs must be non-empty" }
            val operations = store.operations(userId)
            val existing = matchingOperation(sourceId, request, operations)
            if (existing?.completed == true) {
                return@withAccountLock existing.response(operations)
            }
            val competing = operations.firstOrNull { it.sourceId == sourceId && it.operationId != request.operationId }
            var interrupted: JournalMergeOperation? = null
            if (competing != null) {
                if (competing.started) {
                    val previousDestination = resolveMergeDestination(competing.destinationId, operations)
                    if (competing.completed || repository.getJournal(userId, previousDestination) != null) {
                        throw JournalMergeConflictException("Source journal already has a merge operation")
                    }
                    interrupted = competing
                } else {
                    interrupted = competing
                }
            }
            val destination = resolveMergeDestination(request.destinationId, operations)
            if (sourceId == destination) throw JournalMergeConflictException("Journals cannot be merged in a cycle")
            // Finish incoming work before retiring its destination, including its saved offline IDs.
            operations
                .filter {
                    it.started &&
                        !it.completed &&
                        it.sourceId != sourceId &&
                        resolveMergeDestination(it.destinationId, operations) == sourceId
                }.forEach { resume(userId, it) }
            val sourceContent = repository.listAssociations(userId).filter { it.journalId == sourceId }.map { it.entryId }
            val operation =
                (existing ?: JournalMergeOperation(request.operationId, sourceId, request.destinationId, request.contentIds, emptyList()))
                    .let {
                        it.copy(
                            supersededOperationIds =
                                interrupted
                                    ?.let { previous ->
                                        previous.supersededOperationIds + previous.operationId
                                    } ?: it.supersededOperationIds,
                            contentIds =
                                (it.contentIds + interrupted?.contentIds.orEmpty() + sourceContent + request.contentIds).distinct(),
                        )
                    }
            // Persist the complete membership union before the first canonical write.
            if (interrupted != null) {
                if (interrupted.started) repository.getJournal(userId, destination) ?: throw JournalMergeDestinationMissingException()
                store.replaceIncomplete(userId, interrupted.operationId, operation.copy(started = interrupted.started))
            } else {
                store.save(userId, operation)
            }
            resume(userId, operation)
        }

    private fun matchingOperation(
        sourceId: String,
        request: JournalMergeRequest,
        operations: List<JournalMergeOperation>,
    ): JournalMergeOperation? {
        if (operations.any { request.operationId in it.supersededOperationIds }) {
            throw JournalMergeConflictException("Operation ID has been replaced by a recovery operation")
        }
        val existing = operations.firstOrNull { it.operationId == request.operationId }
        if (existing != null &&
            (
                existing.sourceId != sourceId ||
                    existing.destinationId != request.destinationId ||
                    existing.submittedContentIds != request.contentIds
            )
        ) {
            throw JournalMergeConflictException("Operation ID already belongs to another request")
        }
        return existing
    }

    private suspend fun resume(
        userId: UUID,
        pending: JournalMergeOperation,
    ): JournalMergeResponse {
        var operations = store.operations(userId)
        val destination = resolveMergeDestination(pending.destinationId, operations)
        val destinationJournal = repository.getJournal(userId, destination) ?: throw JournalMergeDestinationMissingException()
        val operation = pending.copy(started = true)
        store.save(userId, operation)
        val existing =
            repository
                .listAssociations(userId)
                .filter { it.journalId == destination }
                .map { it.entryId }
                .toSet()
        val additions = operation.contentIds.filterNot(existing::contains)
        if (additions.isNotEmpty()) {
            repository.upsertAssociations(
                userId,
                additions.map { LogDateAssociation(destination, it, System.currentTimeMillis(), 0L, destinationJournal.deviceId) },
            )
        }
        val sourceAssociations = repository.listAssociations(userId).filter { it.journalId == operation.sourceId }
        if (sourceAssociations.isNotEmpty()) {
            repository.deleteAssociations(
                userId,
                sourceAssociations.map {
                    LogDateAssociationRef(it.journalId, it.entryId)
                },
                System.currentTimeMillis(),
            )
        }
        val deletedAt = maxOf(System.currentTimeMillis(), repository.status(userId).lastTimestamp + 1L)
        repository.deleteJournal(userId, operation.sourceId, deletedAt)
        val version = repository.recordJournalMergeDeletion(userId, operation.sourceId, deletedAt)
        val completed = operation.copy(completed = true, deletedAt = deletedAt, tombstoneVersion = version)
        store.save(userId, completed)
        operations = store.operations(userId)
        return completed.response(operations)
    }
}

internal fun resolveMergeDestination(
    id: String,
    operations: List<JournalMergeOperation>,
): String {
    val redirects = operations.filter { it.started }.associateBy { it.sourceId }
    val visited = mutableSetOf<String>()
    var current = id
    while (true) {
        if (!visited.add(current)) throw JournalMergeConflictException("Journal merge redirect cycle")
        current = redirects[current]?.destinationId ?: return current
    }
}

private fun JournalMergeOperation.response(operations: List<JournalMergeOperation>) =
    JournalMergeResponse(operationId, sourceId, resolveMergeDestination(destinationId, operations))

class JournalMergedException(
    val destinationId: String,
) : IllegalStateException("Journal has been merged")

class JournalMergeDestinationMissingException : IllegalStateException("Merge destination is unavailable")

class JournalMergeConflictException(
    message: String,
) : IllegalStateException(message)
