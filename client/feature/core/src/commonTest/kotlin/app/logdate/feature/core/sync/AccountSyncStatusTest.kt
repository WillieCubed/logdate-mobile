package app.logdate.feature.core.sync

import app.logdate.client.sync.BackupRequestState
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.SyncDeadLetterReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AccountSyncStatusTest {
    private val clear = SyncStatus(true, null, 0, false, false)

    @Test
    fun `unfinished recovery or an unreadable queue cannot say up to date`() {
        for (status in listOf(
            clear.copy(pendingDownloads = 1),
            clear.copy(unreadableCloudCount = 1),
            clear.copy(pendingUploads = 1),
            clear.copy(queueReadable = false),
            clear.copy(hasErrors = true),
            clear.copy(conflictCount = 1),
            clear.copy(requestState = BackupRequestState.FAILED),
            clear.copy(isEnabled = false),
        )) {
            assertNotEquals(AccountSyncStatus.UP_TO_DATE, accountSyncStatus(status))
        }
        assertNotEquals(AccountSyncStatus.UP_TO_DATE, accountSyncStatus(clear, setOf(SyncDeadLetterReason.MISSING_FILE)))
        assertEquals(AccountSyncStatus.UP_TO_DATE, accountSyncStatus(clear))
    }

    @Test
    fun `actual syncing takes priority over an earlier failure`() {
        assertEquals(AccountSyncStatus.SYNCING, accountSyncStatus(clear.copy(isSyncing = true, hasErrors = true)))
        assertEquals(AccountSyncStatus.SYNCING, accountSyncStatus(clear.copy(requestState = BackupRequestState.RUNNING)))
    }

    @Test
    fun `failure cause distinguishes device connection from the cloud service`() {
        assertEquals(AccountSyncStatus.OFFLINE, accountSyncStatus(clear.copy(pausedReason = SyncPausedReason.OFFLINE)))
        assertEquals(
            AccountSyncStatus.SERVER_UNAVAILABLE,
            accountSyncStatus(clear.copy(lastError = SyncError(SyncErrorType.SERVER_ERROR, "private details"))),
        )
        assertEquals(
            AccountSyncStatus.CONNECTION_UNAVAILABLE,
            accountSyncStatus(clear.copy(lastError = SyncError(SyncErrorType.NETWORK_ERROR, "private details"))),
        )
    }

    @Test
    fun `retained failures explain the cause without exposing operations or raw errors`() {
        assertEquals(AccountSyncStatus.LOCAL_DATA_UNAVAILABLE, accountSyncStatus(clear, setOf(SyncDeadLetterReason.MISSING_FILE)))
        assertEquals(AccountSyncStatus.MEDIA_TOO_LARGE, accountSyncStatus(clear, setOf(SyncDeadLetterReason.FILE_TOO_LARGE)))
        assertEquals(AccountSyncStatus.DEVICE_ACCESS_REQUIRED, accountSyncStatus(clear.copy(unreadableCloudCount = 1)))
    }
}
