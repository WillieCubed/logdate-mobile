package app.logdate.wear.v2.settings

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

    private fun status(
        pending: Int,
        errors: Boolean = false,
        syncing: Boolean = false,
    ) = SyncStatus(isEnabled = true, lastSyncTime = null, pendingUploads = pending, isSyncing = syncing, hasErrors = errors)
}
