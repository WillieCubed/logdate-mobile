package app.logdate.client.sync.metadata

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Service for tracking sync metadata to determine what needs to be synced.
 * Prevents re-syncing unchanged data.
 */
interface SyncMetadataService {
    /**
     * Gets entities that need to be uploaded.
     * Returns pending entries with operation metadata for each entity type.
     */
    suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload>

    suspend fun hasPending(
        entityType: EntityType,
        entityId: String,
    ): Boolean = getPendingUploads(entityType).any { it.entityId == entityId }

    /**
     * Marks an entity as successfully synced by removing it from the pending queue.
     */
    suspend fun markAsSynced(
        entityId: String,
        entityType: EntityType,
        syncedAt: Instant,
        version: Long,
    )

    /** Settle the captured operation only; a later local mutation remains queued. */
    suspend fun settleIfCurrent(
        entityType: EntityType,
        pending: PendingUpload,
        syncedAt: Instant,
        version: Long,
    ): Boolean {
        if (!isCurrentOperation(entityType, pending)) return false
        markAsSynced(pending.entityId, entityType, syncedAt, version)
        return true
    }

    suspend fun isCurrentOperation(
        entityType: EntityType,
        pending: PendingUpload,
    ): Boolean = getPendingUploads(entityType).any { pending.isSameOperation(it) }

    suspend fun incrementRetryIfCurrent(
        entityType: EntityType,
        pending: PendingUpload,
    ): Boolean {
        if (!isCurrentOperation(entityType, pending)) return false
        incrementRetryCount(pending.entityId, entityType)
        return true
    }

    /**
     * Gets the last sync time for a specific entity type.
     */
    suspend fun getLastSyncTime(entityType: EntityType): Instant?

    /**
     * Updates the last sync time for a specific entity type.
     */
    suspend fun updateLastSyncTime(
        entityType: EntityType,
        syncedAt: Instant,
    )

    /**
     * Enqueues an entity change for sync (create, update, delete).
     */
    suspend fun enqueuePending(
        entityId: String,
        entityType: EntityType,
        operation: PendingOperation,
    )

    /** Queue a fresh transcript mutation; durable stores must join the caller's data transaction. */
    suspend fun enqueueTranscriptMutation(noteId: String): Boolean {
        if (getPendingUploads(EntityType.NOTE).any { it.entityId == noteId && it.operation == PendingOperation.DELETE }) return false
        enqueuePending(noteId, EntityType.NOTE, PendingOperation.UPDATE)
        return true
    }

    /** Backfills a surviving local record without replacing an edit, deletion, or repair. */
    suspend fun enqueueCreateIfAbsent(
        entityId: String,
        entityType: EntityType,
    ) {
        if (!hasPending(entityType, entityId)) enqueuePending(entityId, entityType, PendingOperation.CREATE)
    }

    /** Bind a captured CREATE to an observed encrypted legacy version without replacing a newer mutation. */
    suspend fun bindCreateToServerVersion(
        entityType: EntityType,
        pending: PendingUpload,
        serverVersion: Long,
    ): Boolean = false

    /** Advance a surviving scoped repair from a confirmed own upload without changing its identity. */
    suspend fun advancePendingVersionAfterUpload(
        entityType: EntityType,
        uploaded: PendingUpload,
        serverVersion: Long,
    ): Boolean = false

    /** Enqueues recovery of a surviving local copy without replacing a user mutation. */
    suspend fun enqueueRepairIfAbsent(
        entityId: String,
        entityType: EntityType,
        expectedServerVersion: Long? = null,
        serverOrigin: String? = null,
    ) {
        check(expectedServerVersion == null) { "Versioned repair requires durable metadata" }
        if (!hasPending(entityType, entityId)) enqueuePending(entityId, entityType, PendingOperation.UPDATE)
    }

    /**
     * Resets sync metadata for an entity (forces re-sync).
     */
    suspend fun resetSyncStatus(
        entityId: String,
        entityType: EntityType,
    )

    /**
     * Gets count of pending uploads for UI display.
     */
    suspend fun getPendingCount(): Int

    /**
     * Observes count of pending uploads for reactive UI updates.
     */
    fun observePendingCount(): Flow<Int>

    /**
     * Observes everything waiting to upload, oldest first, so the UI can say what is waiting and
     * not just how much.
     */
    fun observePendingUploads(): Flow<List<QueuedUpload>>

    /**
     * Increments the retry count for a pending upload.
     */
    suspend fun incrementRetryCount(
        entityId: String,
        entityType: EntityType,
    )

    /**
     * Drops the current local owner's queued uploads for the current backend.
     * This is not a sign-out operation: sign-out always preserves offline work.
     */
    suspend fun clearPending()

    /**
     * Drops every entity type's download cursor for the current local owner and backend, so the
     * next download starts from the beginning again. Used after recovering a different identity
     * key: records the old key could not decrypt were left in place on the server rather than
     * repaired, and only re-arrive once the cursor that already passed them is reset.
     */
    suspend fun resetAllCursors()
}

/**
 * Marker interface for entities that can be synced.
 */
interface Syncable {
    val uid: Uuid
    val lastUpdated: Instant
}

/**
 * Types of entities that can be synced.
 */
enum class EntityType {
    JOURNAL,
    JOURNAL_MERGE,
    NOTE,
    ASSOCIATION,
    MEDIA,
    HEALTH,
    DRAFT,
}
