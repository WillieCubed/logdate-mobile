package app.logdate.client.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncBackoff
import app.logdate.client.sync.test.InMemorySyncDeadLetterStore
import app.logdate.client.sync.test.InMemorySyncRetryScheduleStore
import app.logdate.client.sync.test.fakeSyncMetadataService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * An entry that exhausts its retries has still never reached the server. Dropping it from the
 * pending queue at that point reports it as synced to every count and indicator the user can see,
 * so the entry is gone with nothing to show it ever failed. It must stay queued -- throttled hard
 * enough not to spin, but still visibly unsynced and still able to recover once whatever broke it
 * is fixed.
 */
class SyncDeadLetterRetentionTest {
    private val retryScheduleStore = InMemorySyncRetryScheduleStore()
    private val deadLetterStore = InMemorySyncDeadLetterStore()
    private val metadataService = fakeSyncMetadataService()

    private val coordinator =
        SyncRetryCoordinator(
            retryScheduleStore = retryScheduleStore,
            syncMetadataService = metadataService,
            deadLetterStore = deadLetterStore,
            backoff = SyncBackoff(),
            recordConflict = { _, _, _, _, _, _, _ -> },
        )

    private val entityId = Uuid.random().toString()

    private suspend fun exhaustRetries() {
        metadataService.enqueuePending(entityId, EntityType.NOTE, PendingOperation.CREATE)
        // Drives real failures until the coordinator itself reports the item dead-lettered, so the
        // test does not have to restate the retry budget.
        var attempt = 0
        while (attempt < 100) {
            val deadLettered =
                coordinator.handleRetryFailure(
                    entityType = EntityType.NOTE,
                    pending = PendingUpload(entityId, PendingOperation.CREATE, retryCount = attempt),
                    error = IllegalStateException("No identity key found"),
                )
            attempt += 1
            if (deadLettered) return
        }
        error("Entry was never dead-lettered after $attempt attempts")
    }

    @Test
    fun `a dead-lettered entry stays in the pending queue`() =
        runTest {
            exhaustRetries()

            assertTrue(
                metadataService.getPendingUploads(EntityType.NOTE).any { it.entityId == entityId },
                "A dead-lettered entry that never reached the server must not be reported as synced",
            )
            assertEquals(1, deadLetterStore.list().size)
        }

    @Test
    fun `a dead-lettered entry is throttled rather than retried immediately`() =
        runTest {
            exhaustRetries()

            assertFalse(
                coordinator.shouldAttempt(EntityType.NOTE, entityId),
                "A dead-lettered entry must not be retried on the very next sync",
            )
        }
}
