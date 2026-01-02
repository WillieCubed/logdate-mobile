package app.logdate.client.data.fakes

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

class FakeSyncMetadataService : SyncMetadataService {
    private val pendingUploads = mutableMapOf<EntityType, MutableMap<String, PendingOperation>>()
    private val syncTimes = mutableMapOf<EntityType, Instant>()
    private val _pendingCount = MutableStateFlow(0)

    override suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload> {
        return pendingUploads[entityType]
            ?.map { (entityId, operation) -> PendingUpload(entityId, operation) }
            ?: emptyList()
    }

    override suspend fun markAsSynced(
        entityId: String,
        entityType: EntityType,
        syncedAt: Instant,
        version: Long
    ) {
        pendingUploads[entityType]?.remove(entityId)
        updateSyncTime(entityType, syncedAt)
        updatePendingCount()
    }

    override suspend fun getLastSyncTime(entityType: EntityType): Instant? = syncTimes[entityType]

    override suspend fun updateLastSyncTime(entityType: EntityType, syncedAt: Instant) {
        updateSyncTime(entityType, syncedAt)
    }

    override suspend fun enqueuePending(
        entityId: String,
        entityType: EntityType,
        operation: PendingOperation
    ) {
        val existing = pendingUploads[entityType]?.get(entityId)
        val resolved = resolveOperation(existing, operation)
        if (resolved == null) {
            pendingUploads[entityType]?.remove(entityId)
        } else {
            pendingUploads.getOrPut(entityType) { mutableMapOf() }[entityId] = resolved
        }
        updatePendingCount()
    }

    override suspend fun resetSyncStatus(entityId: String, entityType: EntityType) {
        enqueuePending(entityId, entityType, PendingOperation.UPDATE)
        updatePendingCount()
    }

    override suspend fun getPendingCount(): Int = pendingUploads.values.sumOf { it.size }

    override fun observePendingCount(): Flow<Int> = _pendingCount

    private fun updatePendingCount() {
        _pendingCount.value = pendingUploads.values.sumOf { it.size }
    }

    private fun updateSyncTime(entityType: EntityType, syncedAt: Instant) {
        val current = syncTimes[entityType]
        if (current == null || syncedAt >= current) {
            syncTimes[entityType] = syncedAt
        }
    }

    private fun resolveOperation(
        existing: PendingOperation?,
        incoming: PendingOperation
    ): PendingOperation? {
        if (existing == null) return incoming
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
