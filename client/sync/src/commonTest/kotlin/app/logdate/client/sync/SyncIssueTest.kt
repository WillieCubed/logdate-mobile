package app.logdate.client.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SyncIssueTest {
    private val status = SyncStatus(true, null, 0, false, false)

    @Test
    fun `waiting always names a known constraint`() {
        for ((pause, reason) in listOf(
            SyncPausedReason.OFFLINE to SyncIssue.Waiting.Network,
            SyncPausedReason.MEDIA_WAITING_FOR_WIFI to SyncIssue.Waiting.WiFi,
            SyncPausedReason.BACKGROUND_DATA_OFF to SyncIssue.Waiting.BackgroundData,
        )) {
            assertEquals(reason, status.copy(pausedReason = pause).issue())
        }
        assertEquals(SyncIssue.Waiting.ScheduledWork, status.copy(requestState = BackupRequestState.QUEUED).issue())
        assertEquals(SyncIssue.Waiting.RetryBackoff, status.copy(requestState = BackupRequestState.RETRYING).issue())
    }

    @Test
    fun `a failed attempt or repair is not a wait constraint`() {
        assertIs<SyncIssue.Failure>(status.copy(hasErrors = true).issue())
        assertEquals(SyncIssue.Failure.JournalAccess, status.copy(unreadableCloudCount = 1).issue())
        assertEquals(SyncIssue.Failure.JournalAccess, status.copy(pausedReason = SyncPausedReason.NEEDS_RECOVERY_PHRASE).issue())
        assertIs<SyncIssue.Failure>(status.copy(requestState = BackupRequestState.FAILED).issue())
    }

    @Test
    fun `system background restriction takes precedence over wifi consent`() {
        assertEquals(
            SyncIssue.Waiting.BackgroundData,
            status.copy(pendingUploads = 1, pausedReason = SyncPausedReason.MEDIA_WAITING_FOR_WIFI, backgroundWorkLimited = true).issue(),
        )
    }

    @Test
    fun `a backlog alone cannot invent a wait reason`() {
        assertNull(status.copy(pendingUploads = 5).issue())
        assertNull(status.copy(isSyncing = true, pausedReason = SyncPausedReason.OFFLINE).issue())
    }
}
