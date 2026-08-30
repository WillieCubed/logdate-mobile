package app.logdate.client.sync.metadata

import app.logdate.client.database.dao.sync.SyncMetadataDao
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.entities.sync.SyncCursorEntity
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Room-backed implementation of [SyncMetadataService].
 *
 * Persists sync cursors and pending uploads to the local database. The outbox is local-first:
 * a missing Cloud session pauses transport but never suppresses or deletes a local mutation.
 */
class DatabaseSyncMetadataService(
    private val dao: SyncMetadataDao,
    private val configRepository: LogDateConfigRepository,
    private val canonicalOwnerProvider: CanonicalOwnerProvider,
) : SyncMetadataService {
    private val metadataMutex = Mutex()

    override suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload> =
        metadataMutex.withLock {
            val serverOrigin = currentOrigin()
            val ownerId = currentOwnerId()
            promoteLegacyPendingIfNeeded(ownerId, serverOrigin, entityType)
            dao.getPendingByType(ownerId, serverOrigin, entityType.name).map { entity ->
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
        dao.deletePending(currentOwnerId(), currentOrigin(), entityType.name, entityId)
    }

    override suspend fun getLastSyncTime(entityType: EntityType): Instant? {
        val serverOrigin = currentOrigin()
        val ownerId = currentOwnerId()
        promoteLegacyCursorIfNeeded(ownerId, serverOrigin, entityType)
        // A cursor row can exist before anything has actually synced, carrying a zero
        // timestamp. Mapping that straight through reports the Unix epoch as a sync time -
        // "Last synced: December 31, 1969" in any timezone west of UTC - instead of letting
        // the caller fall back to "never synced".
        return dao
            .getCursor(ownerId, serverOrigin, entityType.name)
            ?.lastSyncTimestamp
            ?.takeIf { it > 0L }
            ?.let { Instant.fromEpochMilliseconds(it) }
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
        metadataMutex.withLock {
            val serverOrigin = currentOrigin()
            val ownerId = currentOwnerId()
            promoteLegacyPendingIfNeeded(ownerId, serverOrigin, entityType)
            val existing = dao.getPending(ownerId, serverOrigin, entityType.name, entityId)
            val existingOp = existing?.operation?.let { PendingOperation.fromStorage(it) }
            val resolvedOperation = PendingOperation.coalesce(existingOp, operation)
            if (resolvedOperation == null) {
                dao.deletePending(ownerId, serverOrigin, entityType.name, entityId)
                return@withLock
            }
            dao.insertPending(
                PendingUploadEntity(
                    ownerId = ownerId,
                    serverOrigin = serverOrigin,
                    entityType = entityType.name,
                    entityId = entityId,
                    operation = resolvedOperation.name,
                    createdAt = existing?.createdAt ?: Clock.System.now().toEpochMilliseconds(),
                    retryCount = existing?.retryCount ?: 0,
                ),
            )
        }
    }

    override suspend fun resetSyncStatus(
        entityId: String,
        entityType: EntityType,
    ) {
        enqueuePending(entityId, entityType, PendingOperation.UPDATE)
    }

    override suspend fun getPendingCount(): Int =
        metadataMutex.withLock {
            val ownerId = currentOwnerId()
            val serverOrigin = currentOrigin()
            EntityType.entries.forEach { promoteLegacyPendingIfNeeded(ownerId, serverOrigin, it) }
            dao.getPendingCount(ownerId, serverOrigin)
        }

    override fun observePendingCount(): Flow<Int> =
        flow {
            getPendingCount()
            emitAll(dao.observePendingCount(currentOwnerId(), currentOrigin()))
        }

    override suspend fun clearPending() {
        // Origin-scoped: only clears the queue tied to the current backend, not other backends
        // the user may have used. Cursors are intentionally preserved.
        dao.deletePendingForOrigin(currentOwnerId(), currentOrigin())
    }

    override suspend fun incrementRetryCount(
        entityId: String,
        entityType: EntityType,
    ) {
        dao.incrementRetryCount(currentOwnerId(), currentOrigin(), entityType.name, entityId)
    }

    /**
     * Adds an entity to the pending upload queue.
     */
    suspend fun addPendingUpload(
        entityId: String,
        entityType: EntityType,
        operation: String,
    ) {
        enqueuePending(entityId, entityType, PendingOperation.fromStorage(operation))
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
        val ownerId = currentOwnerId()
        val serverOrigin = currentOrigin()
        dao.deletePendingForOrigin(ownerId, serverOrigin)
        dao.deleteCursorsForOrigin(ownerId, serverOrigin)
    }

    private suspend fun updateCursorIfNewer(
        entityType: EntityType,
        syncedAt: Instant,
    ) {
        val serverOrigin = currentOrigin()
        val ownerId = currentOwnerId()
        promoteLegacyCursorIfNeeded(ownerId, serverOrigin, entityType)
        val current = dao.getCursor(ownerId, serverOrigin, entityType.name)?.lastSyncTimestamp ?: 0L
        val next = syncedAt.toEpochMilliseconds()
        if (next >= current) {
            dao.upsertCursor(
                SyncCursorEntity(
                    ownerId = ownerId,
                    serverOrigin = serverOrigin,
                    entityType = entityType.name,
                    lastSyncTimestamp = next,
                ),
            )
        }
    }

    private fun currentOrigin(): String = configRepository.getCurrentBackendUrl().trimEnd('/')

    private suspend fun currentOwnerId(): String = canonicalOwnerProvider.getCanonicalOwnerId()

    private suspend fun promoteLegacyCursorIfNeeded(
        ownerId: String,
        serverOrigin: String,
        entityType: EntityType,
    ) {
        if (dao.getCursor(ownerId, serverOrigin, entityType.name) != null) {
            return
        }

        val legacyCursor = dao.getLegacyCursor(serverOrigin, entityType.name) ?: return
        dao.upsertCursor(legacyCursor.copy(ownerId = ownerId))
    }

    private suspend fun promoteLegacyPendingIfNeeded(
        ownerId: String,
        serverOrigin: String,
        entityType: EntityType,
    ) {
        if (dao.getPendingByType(ownerId, serverOrigin, entityType.name).isNotEmpty()) {
            return
        }

        val legacyPending = dao.getLegacyPendingByType(serverOrigin, entityType.name)
        if (legacyPending.isEmpty()) {
            return
        }

        legacyPending.forEach { pending ->
            dao.insertPending(pending.copy(ownerId = ownerId))
        }
    }
}
