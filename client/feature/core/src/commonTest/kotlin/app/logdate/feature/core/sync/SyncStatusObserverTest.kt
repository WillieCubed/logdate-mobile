package app.logdate.feature.core.sync

import app.logdate.client.sync.BackupRequestState
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * An unclassified sync failure used to map to [SyncPresentation.Pending] or [SyncPresentation.Hidden]
 * -- exactly what a perfectly healthy sync also looks like. That meant an unexpected exception
 * during sync produced no signal at all, even to someone looking for a problem.
 */
class SyncStatusObserverTest {
    @Test
    fun `an unreadable queue cannot appear fully backed up`() {
        val status = SyncStatus(true, null, 0, false, false, queueReadable = false)

        assertTrue(status.toPresentation() != SyncPresentation.Hidden)
    }

    @Test
    fun `a queued manual request remains visible before the worker starts`() {
        val status = SyncStatus(true, null, 0, false, false, requestState = BackupRequestState.QUEUED)

        assertTrue(status.toPresentation() != SyncPresentation.Hidden)
    }

    @Test
    fun `a running worker is visible before the upload phase starts`() {
        val status = SyncStatus(true, null, 3, false, false, requestState = BackupRequestState.RUNNING)

        assertIs<SyncPresentation.Syncing>(status.toPresentation())
    }

    @Test
    fun `a failed worker is visible even if the sync engine did not record an error`() {
        val status = SyncStatus(true, null, 0, false, false, requestState = BackupRequestState.FAILED)

        assertIs<SyncPresentation.NetworkError>(status.toPresentation())
    }

    @Test
    fun `completed work with an empty queue can leave the timeline uncluttered`() {
        val status = SyncStatus(true, null, 0, false, false, requestState = BackupRequestState.COMPLETED)

        assertEquals(SyncPresentation.Hidden, status.toPresentation())
    }

    @Test
    fun `an unknown error with pending items is not indistinguishable from a healthy backlog`() {
        val status =
            SyncStatus(
                isEnabled = true,
                lastSyncTime = null,
                pendingUploads = 5,
                isSyncing = false,
                hasErrors = true,
                lastError = SyncError(SyncErrorType.UNKNOWN_ERROR, "Something unexpected happened"),
            )

        assertIs<SyncPresentation.NetworkError>(status.toPresentation())
    }

    @Test
    fun `an unknown error with nothing pending is not indistinguishable from a fully idle state`() {
        val status =
            SyncStatus(
                isEnabled = true,
                lastSyncTime = null,
                pendingUploads = 0,
                isSyncing = false,
                hasErrors = true,
                lastError = SyncError(SyncErrorType.UNKNOWN_ERROR, "Something unexpected happened"),
            )

        assertIs<SyncPresentation.NetworkError>(status.toPresentation())
    }

    @Test
    fun `a run in flight reports its progress in steps rather than per item`() {
        val status =
            SyncStatus(
                isEnabled = true,
                lastSyncTime = null,
                pendingUploads = 12,
                isSyncing = true,
                hasErrors = false,
                totalForRun = 19,
                completedInRun = 7,
            )

        // 7 of 19 is 36%, stepped down to 35.
        assertEquals(SyncPresentation.Syncing(progressPercent = 35), status.toPresentation())
    }

    @Test
    fun `a run of unknown size has no progress`() {
        val status =
            SyncStatus(
                isEnabled = true,
                lastSyncTime = null,
                pendingUploads = 12,
                isSyncing = true,
                hasErrors = false,
            )

        assertEquals(SyncPresentation.Syncing(progressPercent = null), status.toPresentation())
    }
}
