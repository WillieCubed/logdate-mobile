package app.logdate.client.sync

import app.logdate.client.datastore.UserSession
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit-level coverage for the offline-first metadata service. The queue always belongs to the
 * local person; authentication only controls when it may be uploaded.
 */
class OfflineFirstMetadataTest {
    @Test
    fun enqueue_is_retained_while_signed_out() =
        runTest {
            val session = fakeSessionStorage(authenticated = false)
            val metadata = fakeSyncMetadataService(session)

            metadata.enqueuePending(
                entityId = Uuid.random().toString(),
                entityType = EntityType.JOURNAL,
                operation = PendingOperation.CREATE,
            )

            assertTrue(
                metadata.getPendingUploads(EntityType.JOURNAL).isNotEmpty(),
                "Offline writes must remain queued until Cloud backup is available",
            )
            assertEquals(1, metadata.getPendingCount())
        }

    @Test
    fun pending_count_survives_sign_out() =
        runTest {
            val session = fakeSessionStorage(authenticated = true)
            val metadata = fakeSyncMetadataService(session)

            // Enqueue while signed-in succeeds.
            metadata.enqueuePending(
                entityId = Uuid.random().toString(),
                entityType = EntityType.NOTE,
                operation = PendingOperation.CREATE,
            )
            assertEquals(1, metadata.getPendingCount())

            // Signing out pauses transport but must not make durable work disappear.
            session.clearSession()
            assertEquals(1, metadata.getPendingCount())
        }

    @Test
    fun queued_offline_work_is_available_after_sign_in() =
        runTest {
            val session = fakeSessionStorage(authenticated = false)
            val metadata = fakeSyncMetadataService(session)

            metadata.enqueuePending(
                entityId = "before-signin",
                entityType = EntityType.JOURNAL,
                operation = PendingOperation.CREATE,
            )
            assertEquals(1, metadata.getPendingUploads(EntityType.JOURNAL).size)

            session.saveSession(
                UserSession(accessToken = "a", refreshToken = "r", accountId = "acct-1"),
            )

            metadata.enqueuePending(
                entityId = "after-signin",
                entityType = EntityType.JOURNAL,
                operation = PendingOperation.CREATE,
            )
            val pending = metadata.getPendingUploads(EntityType.JOURNAL)
            assertEquals(2, pending.size)
            assertTrue(pending.any { it.entityId == "before-signin" })
            assertTrue(pending.any { it.entityId == "after-signin" })
        }

    @Test
    fun clearPending_empties_queue_unconditionally() =
        runTest {
            val session = fakeSessionStorage(authenticated = true)
            val metadata = fakeSyncMetadataService(session)

            metadata.enqueuePending(
                entityId = "j-1",
                entityType = EntityType.JOURNAL,
                operation = PendingOperation.CREATE,
            )
            metadata.enqueuePending(
                entityId = "n-1",
                entityType = EntityType.NOTE,
                operation = PendingOperation.CREATE,
            )
            assertEquals(2, metadata.getPendingCount())

            metadata.clearPending()
            assertEquals(0, metadata.getPendingCount())
            assertTrue(metadata.getPendingUploads(EntityType.JOURNAL).isEmpty())
            assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        }
}
