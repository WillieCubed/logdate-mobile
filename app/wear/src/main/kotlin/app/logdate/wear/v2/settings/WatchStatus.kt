package app.logdate.wear.v2.settings

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
}

/** The status for [sync] while [phone] is in reach, or while no phone is when it is null. */
fun watchStatusOf(
    sync: SyncStatus,
    phone: ReachablePhone?,
): WatchStatus {
    if (sync.pendingUploads == 0) return WatchStatus.Healthy
    if (phone == null) return WatchStatus.WaitingForPhone(sync.pendingUploads)
    if (sync.hasErrors && !sync.isSyncing) return WatchStatus.SendFailing(sync.pendingUploads)
    return WatchStatus.Healthy
}
