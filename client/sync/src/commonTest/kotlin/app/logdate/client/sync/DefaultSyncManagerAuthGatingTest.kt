package app.logdate.client.sync

import app.logdate.client.datastore.UserSession
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Pins the auth-derived shape of [SyncStatus] returned by [DefaultSyncManager.getSyncStatus].
 * Direct UI implication: when these invariants hold, the new `SyncStatusObserver` (in
 * `client.feature.core`) maps to `SyncPresentation.Hidden` for the no-session case and the chip
 * + banner never compose. That's the load-bearing UI fix for "264 items waiting to sync"
 * appearing without an account.
 *
 * Uses the imperative [DefaultSyncManager.getSyncStatus] entry point rather than collecting
 * [DefaultSyncManager.syncStatusFlow] because the manager's republish coroutines run on
 * `platformIODispatcher` and don't interleave deterministically with the test scheduler.
 * `getSyncStatus()` reads the same auth-derived value synchronously.
 */
class DefaultSyncManagerAuthGatingTest {
    @Test
    fun status_reports_disabled_with_no_session() =
        runTest {
            val session = fakeSessionStorage(authenticated = false)
            val manager =
                testDefaultSyncManager(
                    sessionStorage = session,
                    syncMetadataService = fakeSyncMetadataService(session),
                )

            val status = manager.getSyncStatus()
            assertFalse(status.isEnabled, "Without a session, isEnabled must be false")
            assertEquals(0, status.pendingUploads, "Pending count must be zero without a session")
        }

    @Test
    fun status_reports_enabled_after_session_appears() =
        runTest {
            val session = fakeSessionStorage(authenticated = false)
            val manager =
                testDefaultSyncManager(
                    sessionStorage = session,
                    syncMetadataService = fakeSyncMetadataService(session),
                )

            // Sign in. getSyncStatus() reads the live session on each call.
            session.saveSession(
                UserSession(accessToken = "a", refreshToken = "r", accountId = Uuid.random().toString()),
            )

            val status = manager.getSyncStatus()
            assertTrue(status.isEnabled, "After saving a session, isEnabled must be true")
        }

    @Test
    fun status_flips_back_to_disabled_after_sign_out() =
        runTest {
            val session = fakeSessionStorage(authenticated = true)
            val manager =
                testDefaultSyncManager(
                    sessionStorage = session,
                    syncMetadataService = fakeSyncMetadataService(session),
                )

            assertTrue(manager.getSyncStatus().isEnabled, "Sanity: enabled while signed-in")

            session.clearSession()

            val status = manager.getSyncStatus()
            assertFalse(status.isEnabled, "After clearing session, isEnabled must be false")
        }
}
