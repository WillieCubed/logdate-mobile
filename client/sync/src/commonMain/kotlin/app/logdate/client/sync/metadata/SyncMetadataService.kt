package app.logdate.client.sync.metadata

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
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
     * Marks an entity as successfully synced.
     */
    suspend fun markAsSynced(entityId: String, entityType: EntityType, syncedAt: Instant, version: Long)

    /**
     * Gets the last sync time for a specific entity type.
     */
    suspend fun getLastSyncTime(entityType: EntityType): Instant?

    /**
     * Updates the last sync time for a specific entity type.
     */
    suspend fun updateLastSyncTime(entityType: EntityType, syncedAt: Instant)

    /**
     * Enqueues an entity change for sync (create, update, delete).
     */
    suspend fun enqueuePending(entityId: String, entityType: EntityType, operation: PendingOperation)

    /**
     * Resets sync metadata for an entity (forces re-sync).
     */
    suspend fun resetSyncStatus(entityId: String, entityType: EntityType)

    /**
     * Gets count of pending uploads for UI display.
     */
    suspend fun getPendingCount(): Int

    /**
     * Observes count of pending uploads for reactive UI updates.
     */
    fun observePendingCount(): Flow<Int>
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
    MEDIA
}
