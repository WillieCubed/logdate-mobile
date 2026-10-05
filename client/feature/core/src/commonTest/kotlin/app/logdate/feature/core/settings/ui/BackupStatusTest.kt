package app.logdate.feature.core.settings.ui

import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncStatus
import app.logdate.feature.core.sync.AccountSyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupStatusTest {
    private val synced = SyncStatus(true, null, 0, false, false)
    private val saved = CloudArchiveStatus(CloudArchivePhase.COMPLETE, 123L)

    @Test
    fun `backup is complete only when both operations are complete`() {
        assertEquals(AccountSyncStatus.UP_TO_DATE, backupStatus(synced, saved))
        assertEquals(AccountSyncStatus.CHECKING, backupStatus(synced.copy(pendingUploads = 1), saved))
        assertEquals(AccountSyncStatus.CHECKING, backupStatus(synced, CloudArchiveStatus(CloudArchivePhase.NEVER_BACKED_UP)))
        assertEquals(AccountSyncStatus.CHECKING, backupStatus(synced, CloudArchiveStatus(CloudArchivePhase.COMPLETE)))
    }

    @Test
    fun `archive activity and failures feed the same status`() {
        assertEquals(AccountSyncStatus.SYNCING, backupStatus(synced, CloudArchiveStatus(CloudArchivePhase.RUNNING)))
        assertEquals(AccountSyncStatus.RETRYING, backupStatus(synced, CloudArchiveStatus(CloudArchivePhase.RETRYING)))
        for (phase in listOf(CloudArchivePhase.FAILED, CloudArchivePhase.UNAVAILABLE)) {
            assertEquals(AccountSyncStatus.UNKNOWN, backupStatus(synced, CloudArchiveStatus(phase)))
        }
        assertEquals(AccountSyncStatus.DEVICE_ACCESS_REQUIRED, backupStatus(synced, CloudArchiveStatus(CloudArchivePhase.NEEDS_RECOVERY)))
    }

    @Test
    fun `background restriction explains queued archive work even with no pending entries`() {
        assertEquals(
            AccountSyncStatus.BACKGROUND_RESTRICTED,
            backupStatus(synced.copy(backgroundWorkLimited = true), CloudArchiveStatus(CloudArchivePhase.QUEUED)),
        )
    }

    @Test
    fun `known device pause explains why backup is waiting`() {
        val offline = synced.copy(pausedReason = SyncPausedReason.OFFLINE)
        assertEquals(AccountSyncStatus.OFFLINE, backupStatus(offline, CloudArchiveStatus(CloudArchivePhase.RETRYING)))
        assertEquals(AccountSyncStatus.SYNCING, backupStatus(synced.copy(isSyncing = true), CloudArchiveStatus(CloudArchivePhase.FAILED)))
    }
}
