package app.logdate.client.sync

import app.logdate.client.datastore.UserSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

    @Test
    fun startup_cleanup_keeps_pending_queue_when_auth_is_loaded_but_cache_is_still_empty() =
        runTest {
            val session =
                object : SessionStorage {
                    override fun getSession(): UserSession? = null

                    override fun getSessionFlow(): Flow<UserSession?> = kotlinx.coroutines.flow.flowOf(null)

                    override suspend fun hasValidSession(): Boolean = true

                    override fun saveSession(session: UserSession) = Unit

                    override fun clearSession() = Unit
                }
            val metadata = fakeSyncMetadataService(session)
            val manager =
                testDefaultSyncManager(
                    sessionStorage = session,
                    syncMetadataService = metadata,
                    syncScope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            testScheduler.advanceUntilIdle()

            assertEquals(0, metadata.clearPendingCalls, "Cleanup must not run when durable auth exists")
            // Sanity: the manager stays usable once the session cache catches up.
            assertFalse(manager.getSyncStatus().isEnabled, "Cached session is still empty in this test")
        }
}
