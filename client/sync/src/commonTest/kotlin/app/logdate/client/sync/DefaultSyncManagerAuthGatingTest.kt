package app.logdate.client.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    fun `status reports disabled with no session`() =
        runTest {
            val session = fakeSessionStorage(authenticated = false)
            val manager =
                testDefaultSyncManager(
                    sessionStorage = session,
                    syncMetadataService = fakeSyncMetadataService(session),
                )

            val status = manager.getSyncStatus()
            assertFalse(status.isEnabled, "Without a session, isEnabled must be false")
            assertEquals(0, status.pendingUploads, "An empty offline queue has no pending work")
        }

    @Test
    fun `status reports enabled after session appears`() =
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
    fun `status flips back to disabled after sign out`() =
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
    fun `full sync enrolls the authenticated device`() =
        runTest {
            var enrollments = 0
            val session = fakeSessionStorage(authenticated = true)
            val deviceId = MutableStateFlow(Uuid.random())
            val manager =
                testDefaultSyncManager(
                    sessionStorage = session,
                    deviceIdProvider =
                        object : app.logdate.client.device.identity.DeviceIdProvider {
                            override fun getDeviceId() = deviceId

                            override suspend fun refreshDeviceId() {
                                deviceId.value = Uuid.random()
                            }
                        },
                    deviceEnrollment = { enrollments++ },
                )

            manager.fullSync()
            manager.fullSync()

            assertEquals(1, enrollments)

            session.saveSession(UserSession(accessToken = "b", refreshToken = "r", accountId = "other-account"))
            manager.fullSync()

            assertEquals(2, enrollments, "a new account needs its own device enrollment")

            session.currentOrigin = "https://another-server.example"
            manager.fullSync()

            assertEquals(3, enrollments, "the same account at a different server needs its own enrollment")

            deviceId.value = Uuid.random()
            manager.fullSync()

            assertEquals(4, enrollments, "a replacement device identity needs a fresh enrollment")
        }

    @Test
    fun `full sync does not enroll a device without an account`() =
        runTest {
            var enrollments = 0
            val manager =
                testDefaultSyncManager(
                    sessionStorage = fakeSessionStorage(authenticated = false),
                    deviceEnrollment = { enrollments++ },
                )

            manager.fullSync()

            assertEquals(0, enrollments)
        }

    @Test
    fun `device enrollment failure does not fail journal recovery`() =
        runTest {
            var attempts = 0
            val manager =
                testDefaultSyncManager(
                    sessionStorage = fakeSessionStorage(authenticated = true),
                    deviceEnrollment = {
                        attempts++
                        if (attempts == 1) error("registration unavailable")
                    },
                )

            val first = manager.fullSync()
            val retry = manager.fullSync()

            assertTrue(first.success)
            assertTrue(retry.success)
            assertEquals(2, attempts, "failed enrollment must remain eligible for retry")
        }

    @Test
    fun `startup never clears pending queue when auth is loaded but cache is still empty`() =
        runTest {
            val session =
                object : SessionStorage {
                    override fun getSession(): UserSession? = null

                    override fun getSessionFlow(): Flow<UserSession?> = kotlinx.coroutines.flow.flowOf(null)

                    override suspend fun hasValidSession(): Boolean = true

                    override suspend fun saveSession(session: UserSession) = Unit

                    override suspend fun clearSession() = Unit
                }
            val metadata = fakeSyncMetadataService(session)
            val manager =
                testDefaultSyncManager(
                    sessionStorage = session,
                    syncMetadataService = metadata,
                    syncScope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            testScheduler.advanceUntilIdle()

            assertEquals(0, metadata.clearPendingCalls, "Startup must never destroy offline work")
            // Sanity: the manager stays usable once the session cache catches up.
            assertFalse(manager.getSyncStatus().isEnabled, "Cached session is still empty in this test")
        }
}
