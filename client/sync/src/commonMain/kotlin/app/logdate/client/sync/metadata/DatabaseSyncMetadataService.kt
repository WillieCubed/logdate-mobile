package app.logdate.client.sync.metadata

import app.logdate.client.database.dao.sync.SyncMetadataDao
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.entities.sync.SyncCursorEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Room-backed implementation of [SyncMetadataService].
 * Persists sync cursors and pending uploads to the local database.
 */
class DatabaseSyncMetadataService(
    private val dao: SyncMetadataDao
) : SyncMetadataService {

    override suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload> {
        return dao.getPendingByType(entityType.name)
            .map { entity ->
                PendingUpload(
                    entityId = entity.entityId,
                    operation = PendingOperation.fromStorage(entity.operation),
                    retryCount = entity.retryCount
                )
            }
    }

    override suspend fun markAsSynced(
        entityId: String,
        entityType: EntityType,
        syncedAt: Instant,
        version: Long
    ) {
        dao.deletePending(entityType.name, entityId)
        updateCursorIfNewer(entityType, syncedAt)
    }

    override suspend fun getLastSyncTime(entityType: EntityType): Instant? {
        return dao.getCursor(entityType.name)?.let { cursor ->
            Instant.fromEpochMilliseconds(cursor.lastSyncTimestamp)
        }
    }

    override suspend fun updateLastSyncTime(entityType: EntityType, syncedAt: Instant) {
        updateCursorIfNewer(entityType, syncedAt)
    }

    override suspend fun enqueuePending(entityId: String, entityType: EntityType, operation: PendingOperation) {
        val existing = dao.getPending(entityType.name, entityId)
        val resolvedOperation = resolveOperation(existing?.operation, operation)
        if (resolvedOperation == null) {
            dao.deletePending(entityType.name, entityId)
            return
        }

        val createdAt = existing?.createdAt ?: kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val retryCount = existing?.retryCount ?: 0
        dao.insertPending(
            PendingUploadEntity(
                entityType = entityType.name,
                entityId = entityId,
                operation = resolvedOperation.name,
                createdAt = createdAt,
                retryCount = retryCount
            )
        )
    }

    override suspend fun resetSyncStatus(entityId: String, entityType: EntityType) {
        enqueuePending(entityId, entityType, PendingOperation.UPDATE)
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
        entityId: String,
        entityType: EntityType,
        operation: String
    ) {
        dao.insertPending(
            PendingUploadEntity(
                entityType = entityType.name,
                entityId = entityId,
                operation = operation,
                createdAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    /**
     * Updates the sync cursor for a specific entity type.
     */
    suspend fun updateCursor(entityType: EntityType, timestamp: Instant) {
        updateCursorIfNewer(entityType, timestamp)
    }

    /**
     * Clears all sync metadata (for logout/reset).
     */
    suspend fun clearAll() {
        dao.deleteAllPending()
        dao.deleteAllCursors()
    }

    private suspend fun updateCursorIfNewer(entityType: EntityType, syncedAt: Instant) {
        val current = dao.getCursor(entityType.name)?.lastSyncTimestamp ?: 0L
        val next = syncedAt.toEpochMilliseconds()
        if (next >= current) {
            dao.upsertCursor(
                SyncCursorEntity(
                    entityType = entityType.name,
                    lastSyncTimestamp = next
                )
            )
        }
    }

    private fun resolveOperation(existingOperation: String?, incoming: PendingOperation): PendingOperation? {
        val existing = existingOperation?.let { PendingOperation.fromStorage(it) } ?: return incoming

        return when (existing) {
            PendingOperation.CREATE -> when (incoming) {
                PendingOperation.UPDATE -> PendingOperation.CREATE
                PendingOperation.DELETE -> null
                PendingOperation.CREATE -> PendingOperation.CREATE
            }
            PendingOperation.UPDATE -> when (incoming) {
                PendingOperation.DELETE -> PendingOperation.DELETE
                PendingOperation.CREATE -> PendingOperation.CREATE
                PendingOperation.UPDATE -> PendingOperation.UPDATE
            }
            PendingOperation.DELETE -> when (incoming) {
                PendingOperation.CREATE -> PendingOperation.CREATE
                PendingOperation.UPDATE -> PendingOperation.CREATE
                PendingOperation.DELETE -> PendingOperation.DELETE
            }
        }
    }
}
