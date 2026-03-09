package app.logdate.client.sync.metadata

import app.logdate.client.database.dao.sync.SyncMetadataDao
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.entities.sync.SyncCursorEntity
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Room-backed implementation of [SyncMetadataService].
 * Persists sync cursors and pending uploads to the local database.
 */
class DatabaseSyncMetadataService(
    private val dao: SyncMetadataDao,
    private val configRepository: LogDateConfigRepository,
) : SyncMetadataService {
    override suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload> {
        val serverOrigin = currentOrigin()
        promoteLegacyPendingIfNeeded(serverOrigin, entityType)
        return dao.getPendingByType(serverOrigin, entityType.name).map { entity ->
            PendingUpload(
                entityId = entity.entityId,
                operation = PendingOperation.fromStorage(entity.operation),
                retryCount = entity.retryCount,
            )
        }
    }

    override suspend fun markAsSynced(
        entityId: String,
        entityType: EntityType,
        syncedAt: Instant,
        version: Long,
    ) {
        dao.deletePending(currentOrigin(), entityType.name, entityId)
    }

    override suspend fun getLastSyncTime(entityType: EntityType): Instant? {
        val serverOrigin = currentOrigin()
        promoteLegacyCursorIfNeeded(serverOrigin, entityType)
        return dao.getCursor(serverOrigin, entityType.name)?.let { cursor ->
            Instant.fromEpochMilliseconds(cursor.lastSyncTimestamp)
        }
    }

    override suspend fun updateLastSyncTime(
        entityType: EntityType,
        syncedAt: Instant,
    ) {
        updateCursorIfNewer(entityType, syncedAt)
    }

    override suspend fun enqueuePending(
        entityId: String,
        entityType: EntityType,
        operation: PendingOperation,
    ) {
        val serverOrigin = currentOrigin()
        promoteLegacyPendingIfNeeded(serverOrigin, entityType)
        val existing = dao.getPending(serverOrigin, entityType.name, entityId)
        val resolvedOperation = resolveOperation(existing?.operation, operation)
        if (resolvedOperation == null) {
            dao.deletePending(serverOrigin, entityType.name, entityId)
            return
        }

        val createdAt = existing?.createdAt ?: Clock.System.now().toEpochMilliseconds()
        val retryCount = existing?.retryCount ?: 0
        dao.insertPending(
            PendingUploadEntity(
                serverOrigin = serverOrigin,
                entityType = entityType.name,
                entityId = entityId,
                operation = resolvedOperation.name,
                createdAt = createdAt,
                retryCount = retryCount,
            ),
        )
    }

    override suspend fun resetSyncStatus(
        entityId: String,
        entityType: EntityType,
    ) {
        enqueuePending(entityId, entityType, PendingOperation.UPDATE)
    }

    override suspend fun getPendingCount(): Int = dao.getPendingCount(currentOrigin())

    override fun observePendingCount(): Flow<Int> = dao.observePendingCount(currentOrigin())

    override suspend fun incrementRetryCount(
        entityId: String,
        entityType: EntityType,
    ) {
        dao.incrementRetryCount(currentOrigin(), entityType.name, entityId)
    }

    /**
     * Adds an entity to the pending upload queue.
     */
    suspend fun addPendingUpload(
        entityId: String,
        entityType: EntityType,
        operation: String,
    ) {
        dao.insertPending(
            PendingUploadEntity(
                serverOrigin = currentOrigin(),
                entityType = entityType.name,
                entityId = entityId,
                operation = operation,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }

    /**
     * Updates the sync cursor for a specific entity type.
     */
    suspend fun updateCursor(
        entityType: EntityType,
        timestamp: Instant,
    ) {
        updateCursorIfNewer(entityType, timestamp)
    }

    /**
     * Clears all sync metadata (for logout/reset).
     */
    suspend fun clearAll() {
        dao.deleteAllPending()
        dao.deleteAllCursors()
    }

    private suspend fun updateCursorIfNewer(
        entityType: EntityType,
        syncedAt: Instant,
    ) {
        val serverOrigin = currentOrigin()
        promoteLegacyCursorIfNeeded(serverOrigin, entityType)
        val current = dao.getCursor(serverOrigin, entityType.name)?.lastSyncTimestamp ?: 0L
        val next = syncedAt.toEpochMilliseconds()
        if (next >= current) {
            dao.upsertCursor(
                SyncCursorEntity(
                    serverOrigin = serverOrigin,
                    entityType = entityType.name,
                    lastSyncTimestamp = next,
                ),
            )
        }
    }

    private fun resolveOperation(
        existingOperation: String?,
        incoming: PendingOperation,
    ): PendingOperation? {
        val existing = existingOperation?.let { PendingOperation.fromStorage(it) } ?: return incoming

        return when (existing) {
            PendingOperation.CREATE ->
                when (incoming) {
                    PendingOperation.UPDATE -> PendingOperation.CREATE
                    PendingOperation.DELETE -> null
                    PendingOperation.CREATE -> PendingOperation.CREATE
                }
            PendingOperation.UPDATE ->
                when (incoming) {
                    PendingOperation.DELETE -> PendingOperation.DELETE
                    PendingOperation.CREATE -> PendingOperation.CREATE
                    PendingOperation.UPDATE -> PendingOperation.UPDATE
                }
            PendingOperation.DELETE ->
                when (incoming) {
                    PendingOperation.CREATE -> PendingOperation.CREATE
                    PendingOperation.UPDATE -> PendingOperation.CREATE
                    PendingOperation.DELETE -> PendingOperation.DELETE
                }
        }
    }

    private fun currentOrigin(): String = configRepository.getCurrentBackendUrl().trimEnd('/')

    private suspend fun promoteLegacyCursorIfNeeded(
        serverOrigin: String,
        entityType: EntityType,
    ) {
        if (dao.getCursor(serverOrigin, entityType.name) != null) {
            return
        }

        val legacyCursor = dao.getLegacyCursor(entityType.name) ?: return
        dao.upsertCursor(legacyCursor.copy(serverOrigin = serverOrigin))
        dao.deleteLegacyCursor(entityType.name)
    }

    private suspend fun promoteLegacyPendingIfNeeded(
        serverOrigin: String,
        entityType: EntityType,
    ) {
        if (dao.getPendingByType(serverOrigin, entityType.name).isNotEmpty()) {
            return
        }

        val legacyPending = dao.getLegacyPendingByType(entityType.name)
        if (legacyPending.isEmpty()) {
            return
        }

        legacyPending.forEach { pending ->
            dao.insertPending(pending.copy(serverOrigin = serverOrigin))
        }
        dao.deleteLegacyPendingByType(entityType.name)
    }
}
