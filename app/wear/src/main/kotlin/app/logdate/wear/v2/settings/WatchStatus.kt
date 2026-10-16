package app.logdate.wear.v2.settings

import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncStatus
import app.logdate.wear.sync.ReachablePhone

/** What the status card says. It says nothing while sync is healthy. */
sealed interface WatchStatus {
    data object Healthy : WatchStatus

    /** Moments made on the watch wait for the phone, which is out of reach. */
    data class WaitingForPhone(
        val pending: Int,
    ) : WatchStatus

    /** Sending moments to the phone failed while it was in reach, and no send is running. */
    data class SendFailing(
        val pending: Int,
    ) : WatchStatus

    /** The watch cannot sync until LogDate on the phone sets it up again, after a move or a failed link. */
    data object NeedsDeviceLink : WatchStatus

    /** The watch's own backup of its moments failed, and no backup is running. */
    data class BackupFailing(
        val pending: Int,
    ) : WatchStatus
}

/**
 * The status for [sync] while [phone] is in reach, or while no phone is when it is null.
 *
 * A needed device link is reported first, since nothing else clears it. The phone path reports its
 * failures as items set aside and the cloud engine as its last error, so a last error is a failed
 * backup, whether or not the phone is in reach.
 */
fun watchStatusOf(
    sync: SyncStatus,
    phone: ReachablePhone?,
): WatchStatus {
    if (sync.pausedReason == SyncPausedReason.NEEDS_DEVICE_LINK) return WatchStatus.NeedsDeviceLink
    if (sync.pendingUploads == 0) return WatchStatus.Healthy
    if (sync.lastError != null && !sync.isSyncing) return WatchStatus.BackupFailing(sync.pendingUploads)
    if (phone == null) return WatchStatus.WaitingForPhone(sync.pendingUploads)
    if (sync.hasErrors && !sync.isSyncing) return WatchStatus.SendFailing(sync.pendingUploads)
    return WatchStatus.Healthy
}
