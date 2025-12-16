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
     * Returns only entities that have been modified since last sync.
     */
    suspend fun getPendingUploads(entityType: EntityType): List<Uuid>

    /**
     * Marks an entity as successfully synced.
     */
    suspend fun markAsSynced(entityId: Uuid, entityType: EntityType, syncedAt: Instant, version: Int)

    /**
     * Gets the last sync time for a specific entity type.
     */
    suspend fun getLastSyncTime(entityType: EntityType): Instant?

    /**
     * Resets sync metadata for an entity (forces re-sync).
     */
    suspend fun resetSyncStatus(entityId: Uuid, entityType: EntityType)

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

/**
 * Stub implementation that syncs everything (current behavior).
 * Replace with real implementation backed by database.
 */
class AlwaysSyncMetadataService : SyncMetadataService {
    override suspend fun getPendingUploads(entityType: EntityType): List<Uuid> {
        // TODO: Implement proper tracking - for now returns empty list
        // This forces DefaultSyncManager to fall back to syncing everything
        return emptyList()
    }

    override suspend fun markAsSynced(entityId: Uuid, entityType: EntityType, syncedAt: Instant, version: Int) {
        // Stub: Do nothing
    }

    override suspend fun getLastSyncTime(entityType: EntityType): Instant? {
        // Stub: No tracking yet
        return null
    }

    override suspend fun resetSyncStatus(entityId: Uuid, entityType: EntityType) {
        // Stub: Do nothing
    }

    override suspend fun getPendingCount(): Int {
        // Stub: No pending items tracked
        return 0
    }

    override fun observePendingCount(): Flow<Int> {
        return kotlinx.coroutines.flow.flowOf(0)
    }
}
