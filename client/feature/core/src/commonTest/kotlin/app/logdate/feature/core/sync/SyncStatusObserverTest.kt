package app.logdate.feature.core.sync

import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncStatus
import app.logdate.ui.sync.SyncPresentation
import kotlin.test.Test
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
}
