package app.logdate.client.handoff

import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.feature.editor.handoff.DraftRefreshResult
import app.logdate.feature.editor.handoff.DraftRefresher

/**
 * Fetches drafts from other devices through backup's draft sync, which also uploads this device's
 * drafts, so the same call serves the device sending a handoff and the one receiving it.
 */
class SyncDraftRefresher(
    private val syncManager: SyncManager,
) : DraftRefresher {
    override suspend fun refresh(): DraftRefreshResult = syncManager.syncDrafts().toRefreshResult()
}

/**
 * A draft sync that failed without an error never reached the server: backup is off, signed out,
 * or waiting for its identity to be recovered. Any other failure carries the errors that say why.
 */
internal fun SyncResult.toRefreshResult(): DraftRefreshResult =
    when {
        success -> DraftRefreshResult.Refreshed
        errors.isEmpty() -> DraftRefreshResult.Unavailable
        else -> DraftRefreshResult.Failed(errors.joinToString(separator = "; ") { it.message })
    }
