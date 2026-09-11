package app.logdate.client.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncBackoff
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import io.github.aakira.napier.Napier
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Every upload function's shared retry/dead-letter/settlement bookkeeping: whether an entity is
 * due for another attempt, what happens when one fails (backoff vs. permanent dead-letter),
 * marking an attempt settled (success or a lost conflict, both terminal), and the two special-case
 * settlements (an unparsable outbox entry, a 409 conflict) that only differ from a plain success in
 * what error they report.
 *
 * @param recordConflict [SyncDownloadEngine.recordConflict] -- reused rather than duplicated so a
 *   conflict detected during upload is recorded identically to one detected during download.
 */
internal class SyncRetryCoordinator(
    private val retryScheduleStore: SyncRetryScheduleStore,
    private val syncMetadataService: SyncMetadataService,
    private val deadLetterStore: SyncDeadLetterStore,
    private val backoff: SyncBackoff,
    private val recordConflict: suspend (
        entityType: EntityType,
        entityId: String,
        reason: String,
        localVersion: Long?,
        remoteVersion: Long?,
        localUpdatedAt: Instant?,
        remoteUpdatedAt: Instant?,
    ) -> Unit,
) {
    suspend fun shouldAttempt(
        entityType: EntityType,
        entityId: String,
    ): Boolean {
        val nextAttemptAt = retryScheduleStore.nextAttemptAt(entityType, entityId) ?: return true
        return Clock.System.now().toEpochMilliseconds() >= nextAttemptAt
    }

    suspend fun handleRetryFailure(
        entityType: EntityType,
        pending: PendingUpload,
        error: Throwable,
        permanent: Boolean = false,
    ): Boolean {
        val nextRetryCount = pending.retryCount + 1
        syncMetadataService.incrementRetryCount(pending.entityId, entityType)
        // Record *after* the caller has already read the prior state to compute [permanent] --
        // this call is what the *next* attempt for this entity will see as "the previous failure".
        recordFailureKind(entityType, pending.entityId, error)

        // A permanent failure will fail identically every time, so spending the retry budget on it
        // only keeps the queue blocked for longer.
        if (permanent || nextRetryCount >= MAX_RETRY_ATTEMPTS) {
            deadLetterStore.add(
                SyncDeadLetterRecord(
                    id = "${entityType.name}:${pending.entityId}",
                    entityType = entityType.name,
                    entityId = pending.entityId,
                    operation = pending.operation.name,
                    retryCount = nextRetryCount,
                    lastError = error.message ?: "Unknown error",
                    failedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
            // Deliberately left in the pending queue. Removing it here would report an entry that
            // never reached the server as synced to every count and indicator the user can see,
            // and the entry would never be attempted again. Instead it stays queued behind a long
            // backoff: visibly unsynced, cheap to carry, and able to recover on its own once
            // whatever broke it is fixed.
            retryScheduleStore.setNextAttemptAt(
                entityType,
                pending.entityId,
                Clock.System.now().toEpochMilliseconds() + DEAD_LETTER_RETRY_INTERVAL_MS,
            )
            clearFailureKind(entityType, pending.entityId)
            return true
        }

        val delayMs = computeBackoffMs(nextRetryCount)
        retryScheduleStore.setNextAttemptAt(
            entityType,
            pending.entityId,
            Clock.System.now().toEpochMilliseconds() + delayMs,
        )
        return false
    }

    private data class PendingEntityKey(
        val entityType: EntityType,
        val entityId: String,
    )

    /**
     * Entities whose most recent upload attempt failed with [MissingMediaException]
     * specifically -- as opposed to any other kind of failure (network, server, conflict, ...).
     * Tracked generically across every [EntityType], matching every other piece of retry state in
     * this class ([retryScheduleStore], [syncMetadataService]) -- even though [MissingMediaException]
     * currently only ever originates from the note-upload path, hardcoding that assumption into the
     * storage shape would make it awkward for a future entity type that grows the same kind of
     * flaky-first-check failure to reuse this mechanism.
     *
     * [PendingUpload.retryCount] is a single counter shared across every failure reason for an
     * entity, so it cannot answer "were the last two failures *both* missing-media misses" --
     * using it directly means one unrelated hiccup (a transient server error, say) followed by
     * the *first-ever* missing-media miss satisfies "retryCount >= 1" and dead-letters a file
     * that may still be sitting right there on disk. This set tracks the one thing that
     * actually matters for that decision: whether the immediately preceding failure was one too.
     *
     * In-memory only, not persisted: an app restart just means the next miss for that entity is
     * treated as the first one again, which costs one extra retry rather than risking a
     * wrongly-permanent dead-letter. Cleared alongside every [retryScheduleStore] clear so it
     * cannot outlive the entity's own retry state.
     */
    private val entitiesLastFailedOnMissingMedia = mutableSetOf<PendingEntityKey>()

    /** Whether the failure immediately preceding this one for [entityId] was [MissingMediaException]. */
    fun previousFailureWasMissingMedia(
        entityType: EntityType,
        entityId: String,
    ): Boolean = PendingEntityKey(entityType, entityId) in entitiesLastFailedOnMissingMedia

    private fun recordFailureKind(
        entityType: EntityType,
        entityId: String,
        error: Throwable,
    ) {
        val key = PendingEntityKey(entityType, entityId)
        if (error is MissingMediaException) {
            entitiesLastFailedOnMissingMedia.add(key)
        } else {
            entitiesLastFailedOnMissingMedia.remove(key)
        }
    }

    /** Forgets the tracked failure kind for an entity that has left the pending queue. */
    private fun clearFailureKind(
        entityType: EntityType,
        entityId: String,
    ) {
        entitiesLastFailedOnMissingMedia.remove(PendingEntityKey(entityType, entityId))
    }

    private fun computeBackoffMs(retryCount: Int): Long = backoff.nextDelayMs(retryCount)

    /**
     * An upload attempt for [entityId] is done and should not be retried: marks it synced and
     * forgets whatever retry/failure-kind state it had accumulated. Used identically whether the
     * item actually succeeded or a conflict was recorded in its place -- both are terminal outcomes
     * for this attempt, differing only in what [syncedAt]/[version] the caller has to report.
     */
    suspend fun markUploadSettled(
        entityType: EntityType,
        entityId: String,
        syncedAt: Instant,
        version: Long,
    ) {
        syncMetadataService.markAsSynced(entityId, entityType, syncedAt, version)
        retryScheduleStore.clear(entityType, entityId)
        clearFailureKind(entityType, entityId)
    }

    /**
     * An outbox entry's own ID/key couldn't be parsed. Nothing will ever fix that by retrying, so
     * this settles it immediately (no retry state was ever scheduled for it, hence no
     * [retryScheduleStore] clear) and returns the [SyncError] to report for it.
     */
    suspend fun recordUnparsableOutboxEntry(
        entityType: EntityType,
        entityId: String,
        description: String,
    ): SyncError {
        syncMetadataService.markAsSynced(entityId, entityType, Clock.System.now(), 0L)
        return SyncError(SyncErrorType.UNKNOWN_ERROR, "Invalid $description in outbox: $entityId", retryable = false)
    }

    /**
     * The server rejected an upload with a 409: the remote side moved since this device last saw
     * it. Queues a conflict record for later review, settles this attempt at the version that lost
     * (so it stops retrying a write that will only 409 again), and returns the [SyncError] to
     * report for it.
     */
    suspend fun handleUploadConflict(
        entityType: EntityType,
        entityId: String,
        itemLabel: String,
        conflictLabel: String,
        error: Throwable,
        localVersion: Long,
        localUpdatedAt: Instant,
    ): SyncError {
        recordConflict(
            entityType,
            entityId,
            error.message.orEmpty().ifBlank { "$conflictLabel conflict" },
            localVersion,
            null,
            localUpdatedAt,
            null,
        )
        markUploadSettled(entityType, entityId, Clock.System.now(), localVersion)
        Napier.w("Queued conflict for $itemLabel", error)
        return SyncError(
            SyncErrorType.CONFLICT_ERROR,
            "Conflict uploading $itemLabel: ${error.message}",
            error,
            retryable = false,
        )
    }

    /** Re-queues a dead-lettered entity for upload, forgetting it was ever dead-lettered. */
    suspend fun retryDeadLetter(id: String) {
        val record = deadLetterStore.list().firstOrNull { it.id == id } ?: return
        val entityType = runCatching { EntityType.valueOf(record.entityType) }.getOrNull()
        val operation = runCatching { PendingOperation.valueOf(record.operation) }.getOrNull()
        if (entityType == null || operation == null) {
            Napier.w("Cannot retry dead-letter $id with type=${record.entityType} op=${record.operation}")
            deadLetterStore.remove(id)
            return
        }
        syncMetadataService.enqueuePending(record.entityId, entityType, operation)
        deadLetterStore.remove(id)
    }

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 9

        /** How long a dead-lettered entry waits before it is quietly tried again. */
        const val DEAD_LETTER_RETRY_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
