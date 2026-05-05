package app.logdate.feature.core.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncStatus
import app.logdate.ui.sync.SyncPresentation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Maps the sync engine's runtime [SyncStatus] plus auth state into a UI-only
 * [SyncPresentation]. Lives in `client.feature.core` because that's the smallest module that
 * depends on both `client.sync` (for [SyncManager]) and `client.ui` (for [SyncPresentation]).
 *
 * Behavior summary:
 * - No session ⇒ always [SyncPresentation.Hidden]. The chip and banner never render without
 *   an account, regardless of any orphan queue state. This is the load-bearing fix for the
 *   "264 items waiting to sync" surface that shouldn't have existed.
 * - In-flight sync wins over pending/error display so the user sees forward motion.
 * - Errors map by type. Network errors are demoted to a chip (quiet, retried automatically);
 *   auth/storage/conflict errors promote to a banner (the user must act).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun observeSyncPresentation(
    syncManager: SyncManager,
    sessionStorage: SessionStorage,
): Flow<SyncPresentation> =
    combine(
        syncManager.syncStatusFlow,
        sessionStorage.getSessionFlow(),
    ) { status, session ->
        if (session == null) {
            SyncPresentation.Hidden
        } else {
            status.toPresentation()
        }
    }

internal fun SyncStatus.toPresentation(): SyncPresentation {
    if (isSyncing) return SyncPresentation.Syncing(pendingCount = pendingUploads)

    val error = lastError
    if (error != null) {
        return when (error.type) {
            SyncErrorType.AUTHENTICATION_ERROR -> SyncPresentation.AuthError
            SyncErrorType.STORAGE_ERROR -> SyncPresentation.StorageError(pendingCount = pendingUploads)
            SyncErrorType.CONFLICT_ERROR -> SyncPresentation.ConflictError(conflictCount = 1)
            SyncErrorType.NETWORK_ERROR -> SyncPresentation.NetworkError(pendingCount = pendingUploads)
            SyncErrorType.SERVER_ERROR -> SyncPresentation.NetworkError(pendingCount = pendingUploads)
            SyncErrorType.UNKNOWN_ERROR ->
                if (pendingUploads > 0) {
                    SyncPresentation.Pending(pendingCount = pendingUploads)
                } else {
                    SyncPresentation.Hidden
                }
        }
    }

    return if (pendingUploads > 0) {
        SyncPresentation.Pending(pendingCount = pendingUploads)
    } else {
        SyncPresentation.Hidden
    }
}
