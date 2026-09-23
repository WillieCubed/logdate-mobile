package app.logdate.feature.core.sync

import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * An unclassified sync failure used to map to [SyncPresentation.Pending] or [SyncPresentation.Hidden]
 * -- exactly what a perfectly healthy sync also looks like. That meant an unexpected exception
 * during sync produced no signal at all, even to someone looking for a problem.
 */
class SyncStatusObserverTest {
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
