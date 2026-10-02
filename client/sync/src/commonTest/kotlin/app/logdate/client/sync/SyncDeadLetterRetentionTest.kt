package app.logdate.client.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncBackoff
import app.logdate.client.sync.test.FakeSyncMetadataService
import app.logdate.client.sync.test.InMemorySyncDeadLetterStore
import app.logdate.client.sync.test.InMemorySyncRetryScheduleStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
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
    private val metadataService = FakeSyncMetadataService(trackOperationIdentity = true)

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
    private suspend fun exhaustRetries(operation: PendingOperation = PendingOperation.CREATE) {
        metadataService.enqueuePending(entityId, EntityType.NOTE, operation)
        val deadLettered =
            (0 until 100).any { attempt ->
                coordinator.handleRetryFailure(
                    entityType = EntityType.NOTE,
                    pending = metadataService.getPendingUploads(EntityType.NOTE).single().copy(retryCount = attempt),
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

            coordinator.discardDeadLetter(deadLetterStore.list().single().id)

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

            coordinator.retryDeadLetter(deadLetterStore.list().single().id)

            assertTrue(
                coordinator.shouldAttempt(EntityType.NOTE, entityId),
                "Asking for a retry means asking for it now, not in a day",
            )
        }

    @Test
    fun `discard cannot remove a newer deletion after an earlier update failed`() =
        runTest {
            exhaustRetries(PendingOperation.UPDATE)
            val oldIssue = deadLetterStore.list().single().id
            metadataService.enqueuePending(entityId, EntityType.NOTE, PendingOperation.DELETE)
            coordinator.discardDeadLetter(oldIssue)
            assertEquals(PendingOperation.DELETE, metadataService.getPendingUploads(EntityType.NOTE).single().operation)
        }

    @Test
    fun `retry preserves a newer deletion instead of replaying the failed update`() =
        runTest {
            exhaustRetries(PendingOperation.UPDATE)
            metadataService.enqueuePending(entityId, EntityType.NOTE, PendingOperation.DELETE)

            coordinator.retryDeadLetter(deadLetterStore.list().single().id)

            assertEquals(PendingOperation.DELETE, metadataService.getPendingUploads(EntityType.NOTE).single().operation)
            assertTrue(coordinator.shouldAttempt(EntityType.NOTE, entityId))
        }

    @Test
    fun `retry preserves a repair compare and set constraint`() =
        runTest {
            metadataService.enqueueRepairIfAbsent(entityId, EntityType.NOTE, expectedServerVersion = 42)
            val pending = metadataService.getPendingUploads(EntityType.NOTE).single()
            coordinator.handleRetryFailure(EntityType.NOTE, pending, IllegalStateException("failure"), permanent = true)

            coordinator.retryDeadLetter(deadLetterStore.list().single().id)

            assertEquals(42L, metadataService.getPendingUploads(EntityType.NOTE).single().expectedServerVersion)
            assertTrue(coordinator.shouldAttempt(EntityType.NOTE, entityId))
        }

    @Test
    fun `retry does not recreate an operation that has already settled`() =
        runTest {
            exhaustRetries()
            metadataService.clearPending()

            coordinator.retryDeadLetter(deadLetterStore.list().single().id)

            assertTrue(metadataService.getPendingUploads(EntityType.NOTE).isEmpty())
        }

    @Test
    fun `a later successful upload clears its old sync issue`() =
        runTest {
            exhaustRetries()

            coordinator.markUploadSettled(EntityType.NOTE, entityId, Clock.System.now(), 1L)

            assertTrue(deadLetterStore.list().isEmpty())
            assertFalse(metadataService.getPendingUploads(EntityType.NOTE).any { it.entityId == entityId })
        }
}
