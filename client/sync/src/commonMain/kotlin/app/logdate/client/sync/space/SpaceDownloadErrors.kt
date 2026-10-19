package app.logdate.client.sync.space

import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.shared.model.diagnostics.DiagnosticReason

/** The error to report when this download couldn't reach the personal space, or null when it could. */
internal suspend fun DownloadInbox?.spaceFetchError(): SyncError? {
    val failure = this?.fetchFailure(SPACE_CHECKPOINT) ?: return null
    if (failure == DiagnosticReason.NONE) return null
    val type =
        when (failure) {
            DiagnosticReason.SIGN_IN_REQUIRED -> SyncErrorType.AUTHENTICATION_ERROR
            DiagnosticReason.OFFLINE -> SyncErrorType.NETWORK_ERROR
            else -> SyncErrorType.SERVER_ERROR
        }
    return SyncError(type, "Remote fetch failed; saved records are still being recovered")
}
