package app.logdate.feature.core.sync

import app.logdate.client.sync.BackupRequestState
import app.logdate.client.sync.SyncIssue
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.issue
import app.logdate.client.sync.metadata.SyncDeadLetterReason

enum class AccountSyncStatus {
    CHECKING,
    RETRYING,
    UP_TO_DATE,
    SYNCING,
    WAITING,
    OFFLINE,
    SERVER_UNAVAILABLE,
    CONNECTION_UNAVAILABLE,
    SIGN_IN_REQUIRED,
    STORAGE_FULL,
    WAITING_FOR_WIFI,
    BACKGROUND_RESTRICTED,
    DEVICE_ACCESS_REQUIRED,
    CONFLICT,
    LOCAL_DATA_UNAVAILABLE,
    MEDIA_TOO_LARGE,
    UNKNOWN,
    DISABLED,
}

/** Expose whether sync is working and a known failure cause, never the operation queue. */
internal fun accountSyncStatus(
    status: SyncStatus,
    reasons: Set<SyncDeadLetterReason> = emptySet(),
): AccountSyncStatus {
    if (status.isSyncing || status.requestState == BackupRequestState.RUNNING) return AccountSyncStatus.SYNCING
    when (status.issue()) {
        SyncIssue.Waiting.Network -> return AccountSyncStatus.OFFLINE
        SyncIssue.Waiting.WiFi -> return AccountSyncStatus.WAITING_FOR_WIFI
        SyncIssue.Waiting.BackgroundData -> return AccountSyncStatus.BACKGROUND_RESTRICTED
        SyncIssue.Waiting.ScheduledWork -> return AccountSyncStatus.WAITING
        SyncIssue.Waiting.RetryBackoff -> return AccountSyncStatus.RETRYING
        SyncIssue.Failure.SignIn -> return AccountSyncStatus.SIGN_IN_REQUIRED
        SyncIssue.Failure.JournalAccess -> return AccountSyncStatus.DEVICE_ACCESS_REQUIRED
        SyncIssue.Failure.CloudStorage -> return AccountSyncStatus.STORAGE_FULL
        SyncIssue.Failure.Conflict -> return AccountSyncStatus.CONFLICT
        SyncIssue.Failure.LocalData -> return AccountSyncStatus.LOCAL_DATA_UNAVAILABLE
        SyncIssue.Failure.Connection -> return AccountSyncStatus.CONNECTION_UNAVAILABLE
        SyncIssue.Failure.CloudService -> return AccountSyncStatus.SERVER_UNAVAILABLE
        SyncIssue.Failure.Unknown -> Unit
        null -> Unit
    }
    if (!status.isEnabled) return AccountSyncStatus.DISABLED
    for ((reason, state) in listOf(
        SyncDeadLetterReason.MISSING_FILE to AccountSyncStatus.LOCAL_DATA_UNAVAILABLE,
        SyncDeadLetterReason.FILE_TOO_LARGE to AccountSyncStatus.MEDIA_TOO_LARGE,
        SyncDeadLetterReason.SIGN_IN_REQUIRED to AccountSyncStatus.SIGN_IN_REQUIRED,
        SyncDeadLetterReason.SERVER_UNAVAILABLE to AccountSyncStatus.SERVER_UNAVAILABLE,
        SyncDeadLetterReason.NETWORK_UNAVAILABLE to AccountSyncStatus.CONNECTION_UNAVAILABLE,
    )) {
        if (reason in reasons) return state
    }
    if (status.hasErrors || status.lastError != null || reasons.isNotEmpty() || status.requestState == BackupRequestState.FAILED) {
        return AccountSyncStatus.UNKNOWN
    }
    return if (status.pendingUploads > 0 ||
        status.pendingDownloads > 0 ||
        status.requestState == BackupRequestState.QUEUED ||
        status.requestState == BackupRequestState.RETRYING
    ) {
        AccountSyncStatus.CHECKING
    } else {
        AccountSyncStatus.UP_TO_DATE
    }
}
