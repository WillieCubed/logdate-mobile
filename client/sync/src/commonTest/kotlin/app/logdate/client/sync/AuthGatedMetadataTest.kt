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
 * Unit-level coverage for the auth-gated metadata service. The journey test in
 * `journey/AccountlessToCloudJourneyTest.kt` exercises the full path; these tests pin the
 * tighter invariants:
 *
 * - `enqueuePending` is a no-op while signed-out.
 * - `getPendingCount()` reports zero while signed-out, regardless of any rows that may exist.
 * - First sign-in unblocks enqueue without losing the gate semantics.
 * - `clearPending()` empties the queue at any auth state.
 *
 * The fakes mirror production `DatabaseSyncMetadataService` behavior — the fake takes a real
 * `SessionStorage` and gates against it, same shape as the prod impl.
 */
class AuthGatedMetadataTest {
    @Test
    fun enqueue_is_no_op_while_signed_out() =
        runTest {
            val session = fakeSessionStorage(authenticated = false)
            val metadata = fakeSyncMetadataService(session)

            metadata.enqueuePending(
                entityId = Uuid.random().toString(),
                entityType = EntityType.JOURNAL,
                operation = PendingOperation.CREATE,
            )

            assertTrue(
                metadata.getPendingUploads(EntityType.JOURNAL).isEmpty(),
                "Accountless writes must not enqueue",
            )
            assertEquals(0, metadata.getPendingCount())
        }

    @Test
    fun getPendingCount_returns_zero_when_signed_out_even_after_signing_back_out() =
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

            // Signing out flips the count to zero — the UI must never see queue state without auth.
            session.clearSession()
            assertEquals(0, metadata.getPendingCount())
        }

    @Test
    fun first_sign_in_unblocks_enqueue() =
        runTest {
            val session = fakeSessionStorage(authenticated = false)
            val metadata = fakeSyncMetadataService(session)

            metadata.enqueuePending(
                entityId = "before-signin",
                entityType = EntityType.JOURNAL,
                operation = PendingOperation.CREATE,
            )
            assertTrue(metadata.getPendingUploads(EntityType.JOURNAL).isEmpty())

            session.saveSession(
                UserSession(accessToken = "a", refreshToken = "r", accountId = "acct-1"),
            )

            metadata.enqueuePending(
                entityId = "after-signin",
                entityType = EntityType.JOURNAL,
                operation = PendingOperation.CREATE,
            )
            val pending = metadata.getPendingUploads(EntityType.JOURNAL)
            assertEquals(1, pending.size)
            assertEquals("after-signin", pending.single().entityId)
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
