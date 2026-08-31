package app.logdate.client.sync.metadata

import app.logdate.client.database.dao.sync.SyncMetadataDao
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.entities.sync.SyncCursorEntity
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseSyncMetadataServiceTest {
    @Test
    fun `adopting matching legacy metadata retains the legacy rows`() =
        runTest {
            val ownerId = "2e10a582-197e-48fd-97df-5a1fc1669a9e"
            val serverOrigin = "https://cloud.logdate.app"
            val dao = InMemorySyncMetadataDao()
            dao.pendingRows +=
                PendingUploadEntity(
                    ownerId = "",
                    serverOrigin = serverOrigin,
                    entityType = EntityType.JOURNAL.name,
                    entityId = "journal-1",
                    operation = PendingOperation.CREATE.name,
                    createdAt = 1L,
                )
            dao.cursors +=
                SyncCursorEntity(
                    ownerId = "",
                    serverOrigin = serverOrigin,
                    entityType = EntityType.JOURNAL.name,
                    lastSyncTimestamp = 2L,
                )
            val service = service(dao, ownerId, serverOrigin)

            assertEquals(listOf("journal-1"), service.getPendingUploads(EntityType.JOURNAL).map { it.entityId })
            assertNotNull(service.getLastSyncTime(EntityType.JOURNAL))

            assertTrue(dao.pendingRows.any { it.ownerId.isEmpty() && it.entityId == "journal-1" })
            assertTrue(dao.cursors.any { it.ownerId.isEmpty() && it.entityType == EntityType.JOURNAL.name })
        }

    private fun service(
        dao: SyncMetadataDao,
        ownerId: String,
        serverOrigin: String,
    ): DatabaseSyncMetadataService =
        DatabaseSyncMetadataService(
            dao = dao,
            configRepository = DefaultLogDateConfigRepository(initialBackendUrl = serverOrigin),
            canonicalOwnerProvider =
                object : CanonicalOwnerProvider {
                    override suspend fun getCanonicalOwnerId(): String = ownerId
                },
        )

    private class InMemorySyncMetadataDao : SyncMetadataDao {
        val pendingRows = mutableListOf<PendingUploadEntity>()
        val cursors = mutableListOf<SyncCursorEntity>()
        private val pendingCount = MutableStateFlow(0)

        override suspend fun getCursor(
            ownerId: String,
            serverOrigin: String,
            entityType: String,
        ): SyncCursorEntity? =
            cursors.firstOrNull { it.ownerId == ownerId && it.serverOrigin == serverOrigin && it.entityType == entityType }

        override suspend fun getLegacyCursor(
            serverOrigin: String,
            entityType: String,
        ): SyncCursorEntity? = getCursor("", serverOrigin, entityType)

        override suspend fun upsertCursor(cursor: SyncCursorEntity) {
            cursors.removeAll {
                it.ownerId == cursor.ownerId && it.serverOrigin == cursor.serverOrigin && it.entityType == cursor.entityType
            }
            cursors += cursor
        }

        override suspend fun deleteCursorsForOrigin(
            ownerId: String,
            serverOrigin: String,
        ) {
            cursors.removeAll { it.ownerId == ownerId && it.serverOrigin == serverOrigin }
        }

        override suspend fun deleteLegacyCursor(
            serverOrigin: String,
            entityType: String,
        ) {
            cursors.removeAll { it.ownerId.isEmpty() && it.serverOrigin == serverOrigin && it.entityType == entityType }
        }

        override suspend fun getPendingByType(
            ownerId: String,
            serverOrigin: String,
            entityType: String,
        ): List<PendingUploadEntity> =
            pendingRows.filter { it.ownerId == ownerId && it.serverOrigin == serverOrigin && it.entityType == entityType }

        override suspend fun getLegacyPendingByType(
            serverOrigin: String,
            entityType: String,
        ): List<PendingUploadEntity> = getPendingByType("", serverOrigin, entityType)

        override suspend fun getPending(
            ownerId: String,
            serverOrigin: String,
            entityType: String,
            entityId: String,
        ): PendingUploadEntity? =
            pendingRows.firstOrNull {
                it.ownerId == ownerId && it.serverOrigin == serverOrigin && it.entityType == entityType && it.entityId == entityId
            }

        override suspend fun getAllPending(): List<PendingUploadEntity> = pendingRows.toList()

        override suspend fun insertPending(pending: PendingUploadEntity) {
            pendingRows.removeAll {
                it.ownerId == pending.ownerId &&
                    it.serverOrigin == pending.serverOrigin &&
                    it.entityType == pending.entityType &&
                    it.entityId == pending.entityId
            }
            pendingRows += pending
            pendingCount.value = pendingRows.size
        }

        override suspend fun deletePending(
            ownerId: String,
            serverOrigin: String,
            entityType: String,
            entityId: String,
        ) {
            pendingRows.removeAll {
                it.ownerId == ownerId && it.serverOrigin == serverOrigin && it.entityType == entityType && it.entityId == entityId
            }
            pendingCount.value = pendingRows.size
        }

        override suspend fun deletePendingForOrigin(
            ownerId: String,
            serverOrigin: String,
        ) {
            pendingRows.removeAll { it.ownerId == ownerId && it.serverOrigin == serverOrigin }
            pendingCount.value = pendingRows.size
        }

        override suspend fun getPendingCount(
            ownerId: String,
            serverOrigin: String,
        ): Int = pendingRows.count { it.ownerId == ownerId && it.serverOrigin == serverOrigin }

        override fun observePendingCount(
            ownerId: String,
            serverOrigin: String,
        ): Flow<Int> = pendingCount

        override suspend fun incrementRetryCount(
            ownerId: String,
            serverOrigin: String,
            entityType: String,
            entityId: String,
        ) = Unit

        override suspend fun deleteLegacyPendingByType(
            serverOrigin: String,
            entityType: String,
        ) {
            pendingRows.removeAll { it.ownerId.isEmpty() && it.serverOrigin == serverOrigin && it.entityType == entityType }
            pendingCount.value = pendingRows.size
        }
    }
}
