package app.logdate.client.sync.metadata

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests for SyncMetadataService interface behavior.
 * Uses an in-memory implementation for testing.
 */
class SyncMetadataServiceTest {

    /**
     * In-memory implementation for testing.
     */
    private class InMemorySyncMetadataService : SyncMetadataService {
        private val pendingUploads = mutableMapOf<EntityType, MutableMap<String, PendingOperation>>()
        private val syncTimes = mutableMapOf<EntityType, Instant>()
        private val _pendingCount = MutableStateFlow(0)

        override suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload> {
            return pendingUploads[entityType]
                ?.map { (entityId, operation) -> PendingUpload(entityId, operation) }
                ?: emptyList()
        }

        override suspend fun markAsSynced(entityId: String, entityType: EntityType, syncedAt: Instant, version: Long) {
            pendingUploads[entityType]?.remove(entityId)
            updateSyncTime(entityType, syncedAt)
            updatePendingCount()
        }

        override suspend fun getLastSyncTime(entityType: EntityType): Instant? {
            return syncTimes[entityType]
        }

        override suspend fun updateLastSyncTime(entityType: EntityType, syncedAt: Instant) {
            updateSyncTime(entityType, syncedAt)
        }

        override suspend fun enqueuePending(entityId: String, entityType: EntityType, operation: PendingOperation) {
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

        override suspend fun getPendingCount(): Int {
            return pendingUploads.values.sumOf { it.size }
        }

        override fun observePendingCount(): Flow<Int> {
            return _pendingCount
        }

        private fun updatePendingCount() {
            _pendingCount.value = pendingUploads.values.sumOf { it.size }
        }

        // Test helper
        fun addPending(entityId: String, entityType: EntityType, operation: PendingOperation = PendingOperation.UPDATE) {
            pendingUploads.getOrPut(entityType) { mutableMapOf() }[entityId] = operation
            updatePendingCount()
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

    @Test
    fun `initially has no pending uploads`() = runTest {
        val service = InMemorySyncMetadataService()

        assertEquals(0, service.getPendingCount())
        assertTrue(service.getPendingUploads(EntityType.NOTE).isEmpty())
        assertTrue(service.getPendingUploads(EntityType.JOURNAL).isEmpty())
    }

    @Test
    fun `initially has no last sync time`() = runTest {
        val service = InMemorySyncMetadataService()

        assertNull(service.getLastSyncTime(EntityType.NOTE))
        assertNull(service.getLastSyncTime(EntityType.JOURNAL))
    }

    @Test
    fun `resetSyncStatus adds entity to pending uploads`() = runTest {
        val service = InMemorySyncMetadataService()
        val entityId = Uuid.random().toString()

        service.resetSyncStatus(entityId, EntityType.NOTE)

        val pending = service.getPendingUploads(EntityType.NOTE)
        assertEquals(1, pending.size)
        assertEquals(entityId, pending.first().entityId)
        assertEquals(1, service.getPendingCount())
    }

    @Test
    fun `markAsSynced removes entity from pending and updates sync time`() = runTest {
        val service = InMemorySyncMetadataService()
        val entityId = Uuid.random().toString()
        val syncTime = Clock.System.now()

        // Add to pending first
        service.resetSyncStatus(entityId, EntityType.NOTE)
        assertEquals(1, service.getPendingCount())

        // Mark as synced
        service.markAsSynced(entityId, EntityType.NOTE, syncTime, 1L)

        assertEquals(0, service.getPendingCount())
        assertTrue(service.getPendingUploads(EntityType.NOTE).isEmpty())
        assertEquals(syncTime, service.getLastSyncTime(EntityType.NOTE))
    }

    @Test
    fun `pending uploads are tracked per entity type`() = runTest {
        val service = InMemorySyncMetadataService()
        val noteId = Uuid.random().toString()
        val journalId = Uuid.random().toString()

        service.resetSyncStatus(noteId, EntityType.NOTE)
        service.resetSyncStatus(journalId, EntityType.JOURNAL)

        assertEquals(1, service.getPendingUploads(EntityType.NOTE).size)
        assertEquals(1, service.getPendingUploads(EntityType.JOURNAL).size)
        assertEquals(2, service.getPendingCount())
    }

    @Test
    fun `sync times are tracked per entity type`() = runTest {
        val service = InMemorySyncMetadataService()
        val noteId = Uuid.random().toString()
        val journalId = Uuid.random().toString()
        val noteTime = Instant.fromEpochMilliseconds(1000)
        val journalTime = Instant.fromEpochMilliseconds(2000)

        service.markAsSynced(noteId, EntityType.NOTE, noteTime, 1L)
        service.markAsSynced(journalId, EntityType.JOURNAL, journalTime, 1L)

        assertEquals(noteTime, service.getLastSyncTime(EntityType.NOTE))
        assertEquals(journalTime, service.getLastSyncTime(EntityType.JOURNAL))
    }

    @Test
    fun `observePendingCount emits updates`() = runTest {
        val service = InMemorySyncMetadataService()
        val entityId = Uuid.random().toString()

        // Initial state
        assertEquals(0, service.observePendingCount().first())

        // After adding pending
        service.resetSyncStatus(entityId, EntityType.NOTE)
        assertEquals(1, service.observePendingCount().first())

        // After syncing
        service.markAsSynced(entityId, EntityType.NOTE, Clock.System.now(), 1L)
        assertEquals(0, service.observePendingCount().first())
    }

    @Test
    fun `multiple pending entities for same type are tracked correctly`() = runTest {
        val service = InMemorySyncMetadataService()
        val id1 = Uuid.random().toString()
        val id2 = Uuid.random().toString()
        val id3 = Uuid.random().toString()

        service.resetSyncStatus(id1, EntityType.NOTE)
        service.resetSyncStatus(id2, EntityType.NOTE)
        service.resetSyncStatus(id3, EntityType.NOTE)

        assertEquals(3, service.getPendingUploads(EntityType.NOTE).size)
        assertEquals(3, service.getPendingCount())

        // Sync one
        service.markAsSynced(id1, EntityType.NOTE, Clock.System.now(), 1L)
        assertEquals(2, service.getPendingUploads(EntityType.NOTE).size)
        assertEquals(2, service.getPendingCount())
    }

    @Test
    fun `create then delete clears pending entry`() = runTest {
        val service = InMemorySyncMetadataService()
        val entityId = Uuid.random().toString()

        service.enqueuePending(entityId, EntityType.NOTE, PendingOperation.CREATE)
        service.enqueuePending(entityId, EntityType.NOTE, PendingOperation.DELETE)

        assertTrue(service.getPendingUploads(EntityType.NOTE).isEmpty())
        assertEquals(0, service.getPendingCount())
    }

}
