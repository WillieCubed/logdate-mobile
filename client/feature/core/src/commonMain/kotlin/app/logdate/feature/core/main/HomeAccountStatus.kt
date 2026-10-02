package app.logdate.feature.core.main

import app.logdate.feature.core.sync.SyncPresentation
import app.logdate.ui.workspace.WorkspaceAccountIndicator

internal fun SyncPresentation.accountIndicator(): WorkspaceAccountIndicator =
    when (this) {
        SyncPresentation.Hidden -> WorkspaceAccountIndicator.None
        is SyncPresentation.Pending -> if (pendingCount > 0) WorkspaceAccountIndicator.Waiting else WorkspaceAccountIndicator.None
        is SyncPresentation.Syncing -> WorkspaceAccountIndicator.Working
        else -> WorkspaceAccountIndicator.Attention
    }
