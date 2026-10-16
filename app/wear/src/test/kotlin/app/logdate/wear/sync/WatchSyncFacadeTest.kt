package app.logdate.wear.sync

import app.cash.turbine.test
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WatchSyncFacadeTest {
    private val earlier = Instant.fromEpochMilliseconds(1_000)
    private val later = Instant.fromEpochMilliseconds(2_000)

    @Test
    fun `without the cloud path the phone path's status stands unchanged`() {
        val phone = status(pending = 2, hasErrors = true, lastSyncTime = earlier)

        assertSame(phone, combinedWatchSyncStatus(phone, cloud = null))
    }

    @Test
    fun `a cloud engine that needs a device link says so even while sends to the phone fail`() {
        val combined =
            combinedWatchSyncStatus(
                status(pending = 2, hasErrors = true),
                status(pending = 2, pausedReason = SyncPausedReason.NEEDS_DEVICE_LINK),
            )

        assertEquals(SyncPausedReason.NEEDS_DEVICE_LINK, combined.pausedReason)
        assertTrue(combined.hasErrors)
    }

    @Test
    fun `the worse pause of the two paths wins`() {
        val combined =
            combinedWatchSyncStatus(
                status(pausedReason = SyncPausedReason.OFFLINE),
                status(pausedReason = SyncPausedReason.NEEDS_DEVICE_LINK),
            )

        assertEquals(SyncPausedReason.NEEDS_DEVICE_LINK, combined.pausedReason)
        assertEquals(
            SyncPausedReason.NEEDS_DEVICE_LINK,
            combinedWatchSyncStatus(
                status(pausedReason = SyncPausedReason.NEEDS_DEVICE_LINK),
                status(pausedReason = SyncPausedReason.MEDIA_WAITING_FOR_WIFI),
            ).pausedReason,
        )
    }

    @Test
    fun `the cloud engine's own error reaches the status unchanged`() {
        val error = SyncError(SyncErrorType.AUTHENTICATION_ERROR, "The server refused this watch's session", retryable = true)

        val combined = combinedWatchSyncStatus(status(pending = 1), status(pending = 1, hasErrors = true, lastError = error))

        assertSame(error, combined.lastError)
        assertTrue(combined.hasErrors)
    }

    @Test
    fun `an error that cannot be retried outranks one that can`() {
        val retryable = SyncError(SyncErrorType.NETWORK_ERROR, "The connection dropped", retryable = true)
        val final = SyncError(SyncErrorType.SERVER_ERROR, "The server rejected the capture", retryable = false)

        assertSame(final, combinedWatchSyncStatus(status(lastError = final), status(lastError = retryable)).lastError)
        assertSame(final, combinedWatchSyncStatus(status(lastError = retryable), status(lastError = final)).lastError)
    }

    @Test
    fun `a capture waiting on both paths is counted once`() {
        assertEquals(3, combinedWatchSyncStatus(status(pending = 3), status(pending = 3)).pendingUploads)
        assertEquals(4, combinedWatchSyncStatus(status(pending = 4), status(pending = 1)).pendingUploads)
        assertEquals(2, combinedWatchSyncStatus(status(pending = 1), status(pending = 2)).pendingUploads)
    }

    @Test
    fun `last synced is the latest success of either path`() {
        assertEquals(later, combinedWatchSyncStatus(status(lastSyncTime = earlier), status(lastSyncTime = later)).lastSyncTime)
        assertEquals(later, combinedWatchSyncStatus(status(lastSyncTime = later), status(lastSyncTime = earlier)).lastSyncTime)
        assertEquals(earlier, combinedWatchSyncStatus(status(lastSyncTime = earlier), status(lastSyncTime = null)).lastSyncTime)
        assertEquals(later, combinedWatchSyncStatus(status(lastSyncTime = null), status(lastSyncTime = later)).lastSyncTime)
        assertNull(combinedWatchSyncStatus(status(lastSyncTime = null), status(lastSyncTime = null)).lastSyncTime)
    }

    @Test
    fun `either path running or able to sync shows in the combined status`() {
        val cloudRunning = combinedWatchSyncStatus(status(enabled = false), status(enabled = true, syncing = true))
        assertTrue(cloudRunning.isSyncing)
        assertTrue(cloudRunning.isEnabled)

        val idle = combinedWatchSyncStatus(status(enabled = false), status(enabled = false))
        assertFalse(idle.isSyncing)
        assertFalse(idle.isEnabled)
    }

    @Test
    fun `cloud downloads conflicts and unreadable records carry through`() {
        val combined =
            combinedWatchSyncStatus(
                status(),
                status().copy(pendingDownloads = 4, conflictCount = 1, unreadableCloudCount = 2, queueReadable = false),
            )

        assertEquals(4, combined.pendingDownloads)
        assertEquals(1, combined.conflictCount)
        assertEquals(2, combined.unreadableCloudCount)
        assertFalse(combined.queueReadable)
    }

    @Test
    fun `the status follows both paths as they change`() =
        runTest {
            val phoneStatus = MutableStateFlow(status(pending = 1, lastSyncTime = earlier))
            val cloudStatus = MutableStateFlow<SyncStatus?>(null)
            val facade = WatchSyncFacade(phone = phoneManager(phoneStatus), cloudStatus = cloudStatus, scope = backgroundScope)

            facade.syncStatusFlow.test {
                assertEquals(phoneStatus.value, expectMostRecentItem())

                cloudStatus.value = status(pending = 1, pausedReason = SyncPausedReason.NEEDS_DEVICE_LINK)
                assertEquals(SyncPausedReason.NEEDS_DEVICE_LINK, awaitItem().pausedReason)

                cloudStatus.value = status(pending = 1, lastSyncTime = later)
                val linked = awaitItem()
                assertNull(linked.pausedReason)
                assertEquals(later, linked.lastSyncTime)

                cloudStatus.value = status(pending = 0, lastSyncTime = later)
                phoneStatus.value = status(pending = 0, lastSyncTime = earlier)
                assertEquals(0, awaitItem().pendingUploads)

                cloudStatus.value = null
                assertEquals(phoneStatus.value, awaitItem())
            }
        }

    @Test
    fun `a one-off status read combines both paths`() =
        runTest {
            val phoneStatus = MutableStateFlow(status(pending = 2, hasErrors = true))
            val phone = phoneManager(phoneStatus)
            coEvery { phone.getSyncStatus() } returns phoneStatus.value
            val cloud = status(pending = 2, pausedReason = SyncPausedReason.NEEDS_DEVICE_LINK, lastSyncTime = later)
            val facade = WatchSyncFacade(phone = phone, cloudStatus = MutableStateFlow(cloud), scope = backgroundScope)

            val read = facade.getSyncStatus()

            assertEquals(SyncPausedReason.NEEDS_DEVICE_LINK, read.pausedReason)
            assertEquals(2, read.pendingUploads)
            assertEquals(later, read.lastSyncTime)
        }

    @Test
    fun `runs and set-aside items go through the phone path`() =
        runTest {
            val phone = phoneManager(MutableStateFlow(status()))
            coEvery { phone.fullSync() } returns SyncResult(success = true, uploadedItems = 3)
            val facade = WatchSyncFacade(phone = phone, cloudStatus = MutableStateFlow(null), scope = backgroundScope)

            assertEquals(3, facade.fullSync().uploadedItems)
            facade.retryDeadLetter("NOTE:1")

            coVerify(exactly = 1) { phone.fullSync() }
            coVerify(exactly = 1) { phone.retryDeadLetter("NOTE:1") }
        }

    private fun phoneManager(statusFlow: MutableStateFlow<SyncStatus>): SyncManager =
        mockk(relaxed = true) { every { syncStatusFlow } returns statusFlow }

    private fun status(
        pending: Int = 0,
        enabled: Boolean = true,
        syncing: Boolean = false,
        hasErrors: Boolean = false,
        lastError: SyncError? = null,
        pausedReason: SyncPausedReason? = null,
        lastSyncTime: Instant? = null,
    ) = SyncStatus(
        isEnabled = enabled,
        lastSyncTime = lastSyncTime,
        pendingUploads = pending,
        isSyncing = syncing,
        hasErrors = hasErrors,
        lastError = lastError,
        pausedReason = pausedReason,
    )
}
