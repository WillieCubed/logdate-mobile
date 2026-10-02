package app.logdate.client.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncBackoff
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.test.InMemorySyncDeadLetterStore
import app.logdate.client.sync.test.InMemorySyncRetryScheduleStore
import app.logdate.client.sync.test.fakeSyncMetadataService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SyncOperationScopeTest {
    @Test
    fun `old completion cannot clear replacement work or its backoff`() =
        runTest {
            val original =
                PendingUpload(
                    "note",
                    PendingOperation.UPDATE,
                    scope = UploadScope("owner", "https://first.example"),
                    operationId = Uuid.random().toString(),
                )
            val replacement = original.copy(operation = PendingOperation.DELETE, operationId = Uuid.random().toString())
            var current: PendingUpload? = replacement
            val metadata =
                object : SyncMetadataService by fakeSyncMetadataService() {
                    override suspend fun getPendingUploads(entityType: EntityType) = listOfNotNull(current)

                    override suspend fun incrementRetryIfCurrent(
                        entityType: EntityType,
                        pending: PendingUpload,
                    ): Boolean = current?.operationId == pending.operationId && current?.scope == pending.scope

                    override suspend fun settleIfCurrent(
                        entityType: EntityType,
                        pending: PendingUpload,
                        syncedAt: Instant,
                        version: Long,
                    ): Boolean {
                        if (current?.operationId != pending.operationId || current?.scope != pending.scope) return false
                        current = null
                        return true
                    }

                    override suspend fun markAsSynced(
                        entityId: String,
                        entityType: EntityType,
                        syncedAt: Instant,
                        version: Long,
                    ) {
                        current = null
                    }
                }
            val coordinator =
                SyncRetryCoordinator(
                    InMemorySyncRetryScheduleStore(),
                    metadata,
                    InMemorySyncDeadLetterStore(),
                    SyncBackoff(),
                    { _, _, _, _, _, _, _ -> },
                )
            coordinator.handleRetryFailure(EntityType.NOTE, replacement, IllegalStateException("network"))
            coordinator.markUploadSettled(EntityType.NOTE, original, Instant.fromEpochMilliseconds(10), 1)
            assertNotNull(current)
            assertFalse(coordinator.shouldAttempt(EntityType.NOTE, "note"))
        }

    @Test
    fun `late failure cannot increment another server queue or a replacement operation`() =
        runTest {
            val original =
                PendingUpload(
                    "note",
                    PendingOperation.UPDATE,
                    scope = UploadScope("owner", "https://first.example"),
                    operationId = Uuid.random().toString(),
                )
            for (replacement in listOf(
                original.copy(scope = UploadScope("owner", "https://second.example"), operationId = Uuid.random().toString()),
                original.copy(operation = PendingOperation.DELETE, operationId = Uuid.random().toString()),
            )) {
                var current = replacement
                val metadata =
                    object : SyncMetadataService by fakeSyncMetadataService() {
                        override suspend fun getPendingUploads(entityType: EntityType) = listOf(current)

                        override suspend fun incrementRetryIfCurrent(
                            entityType: EntityType,
                            pending: PendingUpload,
                        ): Boolean {
                            if (current.operationId != pending.operationId || current.scope != pending.scope) return false
                            incrementRetryCount(pending.entityId, entityType)
                            return true
                        }

                        override suspend fun incrementRetryCount(
                            entityId: String,
                            entityType: EntityType,
                        ) {
                            current = current.copy(retryCount = current.retryCount + 1)
                        }
                    }
                val coordinator =
                    SyncRetryCoordinator(
                        InMemorySyncRetryScheduleStore(),
                        metadata,
                        InMemorySyncDeadLetterStore(),
                        SyncBackoff(),
                        { _, _, _, _, _, _, _ -> },
                    )
                coordinator.handleRetryFailure(EntityType.NOTE, original, IllegalStateException("network"))
                assertEquals(0, current.retryCount)
            }
        }
}
