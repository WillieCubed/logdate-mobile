package app.logdate.client.sync.metadata

import app.logdate.client.database.dao.sync.SyncMetadataDao
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.entities.sync.SyncCursorEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Room-backed implementation of [SyncMetadataService].
 * Persists sync cursors and pending uploads to the local database.
 */
class DatabaseSyncMetadataService(
    private val dao: SyncMetadataDao
) : SyncMetadataService {

    override suspend fun getPendingUploads(entityType: EntityType): List<Uuid> {
        return dao.getPendingByType(entityType.name)
            .mapNotNull { entity ->
                try {
                    Uuid.parse(entity.entityId)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
    }

    override suspend fun markAsSynced(
        entityId: Uuid,
        entityType: EntityType,
        syncedAt: Instant,
        version: Int
    ) {
        dao.deletePending(entityType.name, entityId.toString())
        dao.upsertCursor(
            SyncCursorEntity(
                entityType = entityType.name,
                lastSyncTimestamp = syncedAt.toEpochMilliseconds()
            )
        )
    }

    override suspend fun getLastSyncTime(entityType: EntityType): Instant? {
        return dao.getCursor(entityType.name)?.let { cursor ->
            Instant.fromEpochMilliseconds(cursor.lastSyncTimestamp)
        }
    }

    override suspend fun resetSyncStatus(entityId: Uuid, entityType: EntityType) {
        dao.insertPending(
            PendingUploadEntity(
                entityType = entityType.name,
                entityId = entityId.toString(),
                operation = "UPDATE",
                createdAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    override suspend fun getPendingCount(): Int {
        return dao.getPendingCount()
    }

    override fun observePendingCount(): Flow<Int> {
        return dao.observePendingCount()
    }

    /**
     * Adds an entity to the pending upload queue.
     */
    suspend fun addPendingUpload(
        entityId: Uuid,
        entityType: EntityType,
        operation: String
    ) {
        dao.insertPending(
            PendingUploadEntity(
                entityType = entityType.name,
                entityId = entityId.toString(),
                operation = operation,
                createdAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    /**
     * Updates the sync cursor for a specific entity type.
     */
    suspend fun updateCursor(entityType: EntityType, timestamp: Instant) {
        dao.upsertCursor(
            SyncCursorEntity(
                entityType = entityType.name,
                lastSyncTimestamp = timestamp.toEpochMilliseconds()
            )
        )
    }

    /**
     * Clears all sync metadata (for logout/reset).
     */
    suspend fun clearAll() {
        dao.deleteAllPending()
        dao.deleteAllCursors()
    }
}
