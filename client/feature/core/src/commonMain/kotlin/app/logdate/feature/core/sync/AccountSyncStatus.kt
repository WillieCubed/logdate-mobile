package app.logdate.feature.core.sync

import app.logdate.client.sync.BackupRequestState
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.SyncDeadLetterReason

enum class AccountSyncStatus {
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
    when (status.pausedReason) {
        SyncPausedReason.NOT_SIGNED_IN -> return AccountSyncStatus.SIGN_IN_REQUIRED
        SyncPausedReason.OFFLINE -> return AccountSyncStatus.OFFLINE
        SyncPausedReason.BACKGROUND_DATA_OFF -> return AccountSyncStatus.BACKGROUND_RESTRICTED
        SyncPausedReason.MEDIA_WAITING_FOR_WIFI -> return AccountSyncStatus.WAITING_FOR_WIFI
        SyncPausedReason.NEEDS_RECOVERY_PHRASE -> return AccountSyncStatus.DEVICE_ACCESS_REQUIRED
        null -> Unit
    }
    if (!status.isEnabled) return AccountSyncStatus.DISABLED
    if (!status.queueReadable) return AccountSyncStatus.LOCAL_DATA_UNAVAILABLE
    when (status.lastError?.type) {
        SyncErrorType.AUTHENTICATION_ERROR -> return AccountSyncStatus.SIGN_IN_REQUIRED
        SyncErrorType.STORAGE_ERROR -> return AccountSyncStatus.STORAGE_FULL
        SyncErrorType.CONFLICT_ERROR -> return AccountSyncStatus.CONFLICT
        SyncErrorType.NETWORK_ERROR -> return AccountSyncStatus.CONNECTION_UNAVAILABLE
        SyncErrorType.SERVER_ERROR -> return AccountSyncStatus.SERVER_UNAVAILABLE
        SyncErrorType.UNKNOWN_ERROR, null -> Unit
    }
    if (status.unreadableCloudCount > 0) return AccountSyncStatus.DEVICE_ACCESS_REQUIRED
    if (status.conflictCount > 0) return AccountSyncStatus.CONFLICT
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
        AccountSyncStatus.WAITING
    } else {
        AccountSyncStatus.UP_TO_DATE
    }
}
