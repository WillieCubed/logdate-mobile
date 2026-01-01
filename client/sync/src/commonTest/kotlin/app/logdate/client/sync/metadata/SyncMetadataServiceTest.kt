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
        private val pendingUploads = mutableMapOf<EntityType, MutableSet<Uuid>>()
        private val syncTimes = mutableMapOf<EntityType, Instant>()
        private val _pendingCount = MutableStateFlow(0)

        override suspend fun getPendingUploads(entityType: EntityType): List<Uuid> {
            return pendingUploads[entityType]?.toList() ?: emptyList()
        }

        override suspend fun markAsSynced(entityId: Uuid, entityType: EntityType, syncedAt: Instant, version: Int) {
            pendingUploads[entityType]?.remove(entityId)
            syncTimes[entityType] = syncedAt
            updatePendingCount()
        }

        override suspend fun getLastSyncTime(entityType: EntityType): Instant? {
            return syncTimes[entityType]
        }

        override suspend fun resetSyncStatus(entityId: Uuid, entityType: EntityType) {
            pendingUploads.getOrPut(entityType) { mutableSetOf() }.add(entityId)
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
        fun addPending(entityId: Uuid, entityType: EntityType) {
            pendingUploads.getOrPut(entityType) { mutableSetOf() }.add(entityId)
            updatePendingCount()
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
        val entityId = Uuid.random()

        service.resetSyncStatus(entityId, EntityType.NOTE)

        val pending = service.getPendingUploads(EntityType.NOTE)
        assertEquals(1, pending.size)
        assertEquals(entityId, pending.first())
        assertEquals(1, service.getPendingCount())
    }

    @Test
    fun `markAsSynced removes entity from pending and updates sync time`() = runTest {
        val service = InMemorySyncMetadataService()
        val entityId = Uuid.random()
        val syncTime = Clock.System.now()

        // Add to pending first
        service.resetSyncStatus(entityId, EntityType.NOTE)
        assertEquals(1, service.getPendingCount())

        // Mark as synced
        service.markAsSynced(entityId, EntityType.NOTE, syncTime, 1)

        assertEquals(0, service.getPendingCount())
        assertTrue(service.getPendingUploads(EntityType.NOTE).isEmpty())
        assertEquals(syncTime, service.getLastSyncTime(EntityType.NOTE))
    }

    @Test
    fun `pending uploads are tracked per entity type`() = runTest {
        val service = InMemorySyncMetadataService()
        val noteId = Uuid.random()
        val journalId = Uuid.random()

        service.resetSyncStatus(noteId, EntityType.NOTE)
        service.resetSyncStatus(journalId, EntityType.JOURNAL)

        assertEquals(1, service.getPendingUploads(EntityType.NOTE).size)
        assertEquals(1, service.getPendingUploads(EntityType.JOURNAL).size)
        assertEquals(2, service.getPendingCount())
    }

    @Test
    fun `sync times are tracked per entity type`() = runTest {
        val service = InMemorySyncMetadataService()
        val noteId = Uuid.random()
        val journalId = Uuid.random()
        val noteTime = Instant.fromEpochMilliseconds(1000)
        val journalTime = Instant.fromEpochMilliseconds(2000)

        service.markAsSynced(noteId, EntityType.NOTE, noteTime, 1)
        service.markAsSynced(journalId, EntityType.JOURNAL, journalTime, 1)

        assertEquals(noteTime, service.getLastSyncTime(EntityType.NOTE))
        assertEquals(journalTime, service.getLastSyncTime(EntityType.JOURNAL))
    }

    @Test
    fun `observePendingCount emits updates`() = runTest {
        val service = InMemorySyncMetadataService()
        val entityId = Uuid.random()

        // Initial state
        assertEquals(0, service.observePendingCount().first())

        // After adding pending
        service.resetSyncStatus(entityId, EntityType.NOTE)
        assertEquals(1, service.observePendingCount().first())

        // After syncing
        service.markAsSynced(entityId, EntityType.NOTE, Clock.System.now(), 1)
        assertEquals(0, service.observePendingCount().first())
    }

    @Test
    fun `multiple pending entities for same type are tracked correctly`() = runTest {
        val service = InMemorySyncMetadataService()
        val id1 = Uuid.random()
        val id2 = Uuid.random()
        val id3 = Uuid.random()

        service.resetSyncStatus(id1, EntityType.NOTE)
        service.resetSyncStatus(id2, EntityType.NOTE)
        service.resetSyncStatus(id3, EntityType.NOTE)

        assertEquals(3, service.getPendingUploads(EntityType.NOTE).size)
        assertEquals(3, service.getPendingCount())

        // Sync one
        service.markAsSynced(id1, EntityType.NOTE, Clock.System.now(), 1)
        assertEquals(2, service.getPendingUploads(EntityType.NOTE).size)
        assertEquals(2, service.getPendingCount())
    }

    @Test
    fun `AlwaysSyncMetadataService returns empty pending for backwards compatibility`() = runTest {
        val service = AlwaysSyncMetadataService()

        assertTrue(service.getPendingUploads(EntityType.NOTE).isEmpty())
        assertTrue(service.getPendingUploads(EntityType.JOURNAL).isEmpty())
        assertEquals(0, service.getPendingCount())
        assertNull(service.getLastSyncTime(EntityType.NOTE))
    }

    @Test
    fun `AlwaysSyncMetadataService observePendingCount emits zero`() = runTest {
        val service = AlwaysSyncMetadataService()

        assertEquals(0, service.observePendingCount().first())
    }
}
