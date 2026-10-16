package app.logdate.wear.sync

import app.logdate.client.sync.BackupRequestState
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/**
 * The watch's one [SyncManager]. It reports a single status for both ways a capture leaves the watch:
 * [phone], which sends to the phone over the Data Layer and hands captures to version 2 delivery, and
 * the watch's own cloud engine, whose status arrives as [cloudStatus].
 *
 * Every call that moves data goes to [phone]: captures released to the cloud engine are uploaded from
 * inside its runs. [cloudStatus] is null while the cloud path does not apply to this watch, and the
 * phone path's status is then reported unchanged.
 */
class WatchSyncFacade(
    private val phone: SyncManager,
    private val cloudStatus: Flow<SyncStatus?>,
    scope: CoroutineScope,
) : SyncManager by phone {
    override val syncStatusFlow: StateFlow<SyncStatus> =
        combine(phone.syncStatusFlow, cloudStatus, ::combinedWatchSyncStatus)
            .stateIn(scope, SharingStarted.WhileSubscribed(STATUS_SUBSCRIPTION_TIMEOUT_MS), phone.syncStatusFlow.value)

    override suspend fun getSyncStatus(): SyncStatus = combinedWatchSyncStatus(phone.getSyncStatus(), cloudStatus.first())

    private companion object {
        /** How long the status goes on following its sources after the last screen stops collecting it. */
        const val STATUS_SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}

/**
 * One status for the phone path and the cloud path, or [phone] alone without a [cloud] status.
 *
 * - The worse problem is reported, as each path described it: a pause outranks by how much the person
 *   has to do to clear it, and an error that cannot be retried outranks one that can.
 * - Both paths count the watch's one outbox, so a capture waiting on both is one capture: the larger
 *   count is the number waiting, and the smaller can only lag behind it.
 * - Last synced is the later of the two paths' last syncs.
 */
internal fun combinedWatchSyncStatus(
    phone: SyncStatus,
    cloud: SyncStatus?,
): SyncStatus {
    if (cloud == null) return phone
    val running = if (cloud.isSyncing) cloud else phone
    return SyncStatus(
        isEnabled = phone.isEnabled || cloud.isEnabled,
        lastSyncTime = listOfNotNull(phone.lastSyncTime, cloud.lastSyncTime).maxOrNull(),
        pendingUploads = maxOf(phone.pendingUploads, cloud.pendingUploads),
        isSyncing = phone.isSyncing || cloud.isSyncing,
        hasErrors = phone.hasErrors || cloud.hasErrors,
        lastError = worseError(phone.lastError, cloud.lastError),
        pausedReason = worsePause(phone.pausedReason, cloud.pausedReason),
        totalForRun = running.totalForRun,
        completedInRun = running.completedInRun,
        conflictCount = phone.conflictCount + cloud.conflictCount,
        unreadableCloudCount = phone.unreadableCloudCount + cloud.unreadableCloudCount,
        queueReadable = phone.queueReadable && cloud.queueReadable,
        requestState = phone.requestState.takeIf { it != BackupRequestState.NONE } ?: cloud.requestState,
        backgroundWorkLimited = phone.backgroundWorkLimited || cloud.backgroundWorkLimited,
        pendingDownloads = phone.pendingDownloads + cloud.pendingDownloads,
    )
}

private fun worseError(
    phone: SyncError?,
    cloud: SyncError?,
): SyncError? = listOfNotNull(cloud, phone).minByOrNull { if (it.retryable) 1 else 0 }

private fun worsePause(
    phone: SyncPausedReason?,
    cloud: SyncPausedReason?,
): SyncPausedReason? = listOfNotNull(cloud, phone).maxByOrNull { it.effortToClear() }

/** How much the person has to do before sync can go on: linking again from the phone is the most. */
private fun SyncPausedReason.effortToClear(): Int =
    when (this) {
        SyncPausedReason.NEEDS_DEVICE_LINK -> 6
        SyncPausedReason.NEEDS_RECOVERY_PHRASE -> 5
        SyncPausedReason.NOT_SIGNED_IN -> 4
        SyncPausedReason.BACKGROUND_DATA_OFF -> 3
        SyncPausedReason.OFFLINE -> 2
        SyncPausedReason.MEDIA_WAITING_FOR_WIFI -> 1
    }
