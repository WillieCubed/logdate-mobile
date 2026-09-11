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

    /** Fails uploads until the coordinator gives up, so the test never restates the retry budget. */
    private suspend fun exhaustRetries() {
        metadataService.enqueuePending(entityId, EntityType.NOTE, PendingOperation.CREATE)
        val deadLettered =
            (0 until 100).any { attempt ->
                coordinator.handleRetryFailure(
                    entityType = EntityType.NOTE,
                    pending = PendingUpload(entityId, PendingOperation.CREATE, retryCount = attempt),
                    error = IllegalStateException("No identity key found"),
                )
            }
        assertTrue(deadLettered, "Entry was never dead-lettered")
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

    @Test
    fun `discarding a dead-lettered entry also drains it from the pending queue`() =
        runTest {
            exhaustRetries()

            coordinator.discardDeadLetter("${EntityType.NOTE.name}:$entityId")

            assertTrue(deadLetterStore.list().isEmpty())
            assertFalse(
                metadataService.getPendingUploads(EntityType.NOTE).any { it.entityId == entityId },
                "A discarded entry must not stay queued forever",
            )
        }

    @Test
    fun `retrying a dead-lettered entry lifts its throttle`() =
        runTest {
            exhaustRetries()

            coordinator.retryDeadLetter("${EntityType.NOTE.name}:$entityId")

            assertTrue(
                coordinator.shouldAttempt(EntityType.NOTE, entityId),
                "Asking for a retry means asking for it now, not in a day",
            )
        }
}
