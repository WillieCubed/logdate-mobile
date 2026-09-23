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

    /**
     * Marks an entity as successfully synced by removing it from the pending queue.
     */
    suspend fun markAsSynced(
        entityId: String,
        entityType: EntityType,
        syncedAt: Instant,
        version: Long,
    )

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
    NOTE,
    ASSOCIATION,
    MEDIA,
    HEALTH,
    DRAFT,
}
