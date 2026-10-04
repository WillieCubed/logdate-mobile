package app.logdate.feature.core.main

import app.logdate.feature.core.sync.SyncPresentation
import app.logdate.ui.workspace.WorkspaceAccountIndicator
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeAccountStatusTest {
    @Test fun healthyAndEmptyQueuesKeepTheAccountQuiet() {
        assertEquals(WorkspaceAccountIndicator.None, SyncPresentation.Hidden.accountIndicator())
        assertEquals(WorkspaceAccountIndicator.None, SyncPresentation.Pending(0).accountIndicator())
    }

    @Test fun backgroundWorkUsesAQuietAccountMarker() {
        assertEquals(WorkspaceAccountIndicator.Working, SyncPresentation.Syncing(25).accountIndicator())
        assertEquals(WorkspaceAccountIndicator.Waiting, SyncPresentation.Pending(4).accountIndicator())
    }

    @Test fun automaticFailuresKeepAQuietWaitingMarker() {
        val blocked =
            listOf(
                SyncPresentation.NeedsRecovery,
                SyncPresentation.StatusUnavailable,
                SyncPresentation.StorageError(0),
                SyncPresentation.ConflictError(1),
                SyncPresentation.NetworkError(0),
            )
        blocked.forEach { assertEquals(WorkspaceAccountIndicator.Waiting, it.accountIndicator()) }
        assertEquals(WorkspaceAccountIndicator.Attention, SyncPresentation.AuthError.accountIndicator())
    }
}
