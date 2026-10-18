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
    fun `pending restored downloads are reported as recovery in progress`() {
        assertEquals(AccountSyncStatus.RECOVERING, accountSyncStatus(clear.copy(pendingDownloads = 2, hasErrors = true)))
        assertEquals(
            AccountSyncStatus.KEY_RECOVERY_REQUIRED,
            accountSyncStatus(
                clear.copy(
                    pendingDownloads = 2,
                    lastError = SyncError(SyncErrorType.KEY_RECOVERY_ERROR, "missing key"),
                ),
            ),
        )
    }

    @Test
    fun `actual syncing takes priority over an earlier failure`() {
        assertEquals(AccountSyncStatus.SYNCING, accountSyncStatus(clear.copy(isSyncing = true, hasErrors = true)))
        assertEquals(AccountSyncStatus.SYNCING, accountSyncStatus(clear.copy(requestState = BackupRequestState.RUNNING)))
    }

    @Test
    fun `active restored downloads remain labeled as recovery`() {
        assertEquals(
            AccountSyncStatus.RECOVERING,
            accountSyncStatus(clear.copy(isSyncing = true, pendingDownloads = 2)),
        )
        assertEquals(
            AccountSyncStatus.RECOVERING,
            accountSyncStatus(clear.copy(requestState = BackupRequestState.RUNNING, pendingDownloads = 2)),
        )
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
        assertEquals(
            AccountSyncStatus.KEY_RECOVERY_REQUIRED,
            accountSyncStatus(clear.copy(lastError = SyncError(SyncErrorType.KEY_RECOVERY_ERROR, "private details"))),
        )
        assertEquals(
            AccountSyncStatus.MEDIA_TOO_LARGE,
            accountSyncStatus(clear.copy(lastError = SyncError(SyncErrorType.MEDIA_TOO_LARGE_ERROR, "private details"))),
        )
    }

    @Test
    fun `a linked watch waiting for its phone says so`() {
        val waiting = clear.copy(pausedReason = SyncPausedReason.NEEDS_DEVICE_LINK)

        assertEquals(AccountSyncStatus.DEVICE_LINK_REQUIRED, accountSyncStatus(waiting))
    }

    @Test
    fun `retained failures explain the cause without exposing operations or raw errors`() {
        assertEquals(AccountSyncStatus.LOCAL_DATA_UNAVAILABLE, accountSyncStatus(clear, setOf(SyncDeadLetterReason.MISSING_FILE)))
        assertEquals(AccountSyncStatus.MEDIA_TOO_LARGE, accountSyncStatus(clear, setOf(SyncDeadLetterReason.FILE_TOO_LARGE)))
        assertEquals(AccountSyncStatus.DEVICE_ACCESS_REQUIRED, accountSyncStatus(clear.copy(unreadableCloudCount = 1)))
    }
}
