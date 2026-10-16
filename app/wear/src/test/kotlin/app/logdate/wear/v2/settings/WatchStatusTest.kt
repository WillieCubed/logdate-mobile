package app.logdate.wear.v2.settings

import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncStatus
import app.logdate.wear.sync.ReachablePhone
import org.junit.Test
import kotlin.test.assertEquals

class WatchStatusTest {
    private val phone = ReachablePhone("Pixel")

    @Test
    fun `nothing waiting is healthy whether or not the phone is near`() {
        assertEquals(WatchStatus.Healthy, watchStatusOf(status(pending = 0), phone))
        assertEquals(WatchStatus.Healthy, watchStatusOf(status(pending = 0), null))
    }

    @Test
    fun `moments waiting with the phone away are waiting for the phone`() {
        assertEquals(WatchStatus.WaitingForPhone(3), watchStatusOf(status(pending = 3, errors = true), null))
    }

    @Test
    fun `failed sends with the phone in reach are failing`() {
        assertEquals(WatchStatus.SendFailing(2), watchStatusOf(status(pending = 2, errors = true), phone))
    }

    @Test
    fun `moments on their way to a phone in reach are healthy`() {
        assertEquals(WatchStatus.Healthy, watchStatusOf(status(pending = 2), phone))
        assertEquals(WatchStatus.Healthy, watchStatusOf(status(pending = 2, errors = true, syncing = true), phone))
    }

    @Test
    fun `a watch that needs a device link says so whatever is waiting`() {
        val needsLink = status(pending = 0, paused = SyncPausedReason.NEEDS_DEVICE_LINK)

        assertEquals(WatchStatus.NeedsDeviceLink, watchStatusOf(needsLink, phone))
        assertEquals(WatchStatus.NeedsDeviceLink, watchStatusOf(needsLink.copy(pendingUploads = 3, hasErrors = true), null))
    }

    @Test
    fun `a failed backup is reported as a backup failure and not as a failure to reach the phone`() {
        val failed = status(pending = 2, errors = true, error = cloudError)

        assertEquals(WatchStatus.BackupFailing(2), watchStatusOf(failed, phone))
        assertEquals(WatchStatus.BackupFailing(2), watchStatusOf(failed, null))
    }

    @Test
    fun `a backup in progress or with nothing waiting is not reported as failing`() {
        assertEquals(WatchStatus.Healthy, watchStatusOf(status(pending = 2, errors = true, syncing = true, error = cloudError), phone))
        assertEquals(WatchStatus.Healthy, watchStatusOf(status(pending = 0, errors = true, error = cloudError), phone))
    }

    @Test
    fun `other pauses leave the status to what is waiting`() {
        assertEquals(WatchStatus.WaitingForPhone(1), watchStatusOf(status(pending = 1, paused = SyncPausedReason.OFFLINE), null))
    }

    private val cloudError = SyncError(SyncErrorType.SERVER_ERROR, "The server rejected the capture")

    private fun status(
        pending: Int,
        errors: Boolean = false,
        syncing: Boolean = false,
        error: SyncError? = null,
        paused: SyncPausedReason? = null,
    ) = SyncStatus(
        isEnabled = true,
        lastSyncTime = null,
        pendingUploads = pending,
        isSyncing = syncing,
        hasErrors = errors,
        lastError = error,
        pausedReason = paused,
    )
}
