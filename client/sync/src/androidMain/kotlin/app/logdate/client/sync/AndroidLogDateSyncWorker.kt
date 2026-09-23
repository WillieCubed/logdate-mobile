package app.logdate.client.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class AndroidLogDateSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val syncManager: DefaultSyncManager by inject()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        SyncForegroundNotification.info(
            applicationContext,
            applicationContext.getString(R.string.sync_notification_starting),
        )

    override suspend fun doWork(): Result =
        try {
            Napier.i("Starting Android background sync worker")
            // Without this the sync is ordinary background work and dies when the app leaves the
            // screen - part way through, with the queue half drained. Failing to promote is not
            // fatal (the permission may be denied); the sync just goes back to being cancellable.
            val promoted =
                runCatching { setForeground(getForegroundInfo()) }
                    .onFailure { Napier.w("Backup running without a foreground notification", it) }
                    .isSuccess

            val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_FULL
            val result = if (promoted) withProgressNotification { runSync(syncType) } else runSync(syncType)

            if (result.success) {
                val uploaded = result.uploadedItems
                val downloaded = result.downloadedItems
                val conflicts = result.conflictsResolved

                Napier.i("Sync completed successfully: $uploaded uploaded, $downloaded downloaded, $conflicts conflicts resolved")
                Result.success()
            } else {
                val errorMessages = result.errors.joinToString { it.message }
                Napier.w("Sync failed: $errorMessages")

                // Retry on transient errors, fail permanently on auth errors
                val hasAuthError = result.errors.any { it.type == SyncErrorType.AUTHENTICATION_ERROR }
                if (hasAuthError) {
                    Napier.e("Authentication error in sync, not retrying")
                    Result.failure()
                } else {
                    Napier.w("Transient sync error, will retry")
                    Result.retry()
                }
            }
        } catch (e: CancellationException) {
            // WorkManager cancellation is terminal. Propagating it prevents a cancelled job from
            // being reported as a retry and accidentally resurrected by the scheduler.
            throw e
        } catch (e: Exception) {
            Napier.e("Unexpected error in sync worker", e)
            Result.retry()
        }

    private suspend fun runSync(syncType: String): SyncResult =
        when (syncType) {
            SYNC_TYPE_UPLOAD -> {
                Napier.d("Performing upload sync")
                syncManager.uploadPendingChanges()
            }
            SYNC_TYPE_DOWNLOAD -> {
                Napier.d("Performing download sync")
                syncManager.downloadRemoteChanges()
            }
            SYNC_TYPE_FULL -> {
                Napier.d("Performing full sync")
                syncManager.fullSync()
            }
            else -> {
                Napier.w("Unknown sync type: $syncType, defaulting to full sync")
                syncManager.fullSync()
            }
        }

    /**
     * Runs [sync] while the notification follows the run's progress, and stops following it as
     * soon as [sync] returns. A backup of several hundred entries takes minutes, and a notification
     * that says "Starting…" for all of them reads as a backup that has stopped.
     */
    private suspend fun <T> withProgressNotification(sync: suspend () -> T): T =
        coroutineScope {
            val statuses = syncManager.syncStatusFlow
            val since = statuses.value
            val updates =
                launch {
                    statuses
                        .runProgressUpdates(since = since, period = PROGRESS_UPDATE_PERIOD)
                        .takeWhile { progress -> showProgress(progress) }
                        .collect()
                }
            try {
                sync()
            } finally {
                updates.cancel()
            }
        }

    /**
     * Shows [progress] in the foreground notification. Returns false once the notification can no
     * longer be updated; the backup carries on regardless, under the notification it already has.
     */
    private suspend fun showProgress(progress: SyncRunProgress): Boolean =
        try {
            val text = applicationContext.getString(R.string.sync_notification_progress, progress.completed, progress.total)
            setForeground(SyncForegroundNotification.info(applicationContext, text, progress.completed, progress.total))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not show backup progress in the notification", e)
            false
        }

    companion object {
        const val KEY_SYNC_TYPE = "sync_type"
        const val SYNC_TYPE_UPLOAD = "upload"
        const val SYNC_TYPE_DOWNLOAD = "download"
        const val SYNC_TYPE_FULL = "full"

        const val WORK_NAME_PERIODIC_SYNC = "periodic_sync"
        const val WORK_NAME_IMMEDIATE_SYNC = "immediate_sync"

        /** Often enough to watch a backup move, rarely enough not to redraw on every entry. */
        private val PROGRESS_UPDATE_PERIOD = 1.seconds
    }
}

/**
 * A platform-specific implementation of [SyncManager] for Android.
 *
 * This relies on the Android WorkManager API to schedule background work
 * and delegates core sync functionality to DefaultSyncManager.
 *
 * Features:
 * - Automatic background sync enable/disable based on authentication state and user preference
 * - Periodic background sync every 15 minutes (minimum allowed by Android)
 * - Immediate sync for user-triggered actions
 * - Network-aware scheduling (WiFi preferred, but cellular allowed)
 * - Battery optimization aware
 * - Exponential backoff on failures
 * - Automatic sync retry on network restoration
 *
 * Background sync is automatically managed:
 * - When user signs in and background sync is enabled in settings, periodic sync is enabled
 * - When user signs out or disables background sync, periodic sync is disabled
 * - No UI code needs to manually schedule WorkManager sync
 * - Network transitions (offline→online) automatically trigger sync retry if needed
 */
@OptIn(FlowPreview::class)
class AndroidSyncManager(
    private val applicationContext: Context,
    private val defaultSyncManager: DefaultSyncManager,
    private val sessionStorage: SessionStorage,
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val networkMonitor: NetworkAvailabilityMonitor,
    private val dataUsagePolicy: DataUsagePolicy,
) : SyncManager {
    private val workManager = WorkManager.getInstance(applicationContext)
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.IO)

    init {
        // Observe authentication state and automatically enable/disable background sync
        scope.launch {
            // Being signed in is the whole condition; see ForegroundSyncManager for why the
            // stored preference that used to be ANDed in here had to go.
            sessionStorage
                .getSessionFlow()
                .map { it != null }
                .distinctUntilChanged()
                .collect { shouldEnable ->
                    if (shouldEnable) {
                        Napier.i("Background sync enabled")
                        enableBackgroundSync()
                    } else {
                        Napier.i("Background sync disabled")
                        disableBackgroundSync()
                    }
                }
        }

        // Observe network state and trigger sync retry on network restoration
        scope.launch {
            var lastNetworkState: NetworkState? = null
            networkMonitor
                .observeNetwork()
                .debounce(2000) // Wait 2 seconds for network to stabilize
                .distinctUntilChanged()
                .collect { currentState ->
                    // Check if transitioned from offline to online
                    if (lastNetworkState is NetworkState.NotConnected &&
                        currentState is NetworkState.Connected
                    ) {
                        handleNetworkRestored()
                    }
                    lastNetworkState = currentState
                }
        }

        // Observe data usage policy changes and re-enqueue periodic sync with updated constraints
        scope.launch {
            dataUsagePolicy.policy
                .distinctUntilChanged()
                .collect { mode ->
                    Napier.d("Data usage policy changed to $mode, updating periodic sync constraints")
                    setupPeriodicSync(mode)
                }
        }
    }

    /**
     * Cancels the background sync observation coroutine.
     * Should be called when the sync manager is no longer needed (e.g., during testing cleanup).
     */
    fun cancel() {
        supervisorJob.cancel()
        Napier.d("AndroidSyncManager scope cancelled")
    }

    /**
     * Handles network restoration by triggering a sync retry if the last sync
     * failed due to a transient error (not authentication).
     */
    private suspend fun handleNetworkRestored() {
        Napier.i("Network stable after offline period, checking if sync retry needed")
        val lastError = defaultSyncManager.getLastSyncError()

        // Only retry if last error was transient (not auth)
        if (lastError != null && lastError.type != SyncErrorType.AUTHENTICATION_ERROR) {
            Napier.d("Previous sync failed transiently (${lastError.type}), triggering immediate retry after network restoration")
            scheduleImmediateSync(AndroidLogDateSyncWorker.SYNC_TYPE_FULL)
        } else if (lastError == null) {
            Napier.d("No previous sync error or last sync succeeded, skipping retry")
        } else {
            Napier.d("Last sync failed with auth error, not retrying on network restoration")
        }
    }

    override fun sync(startNow: Boolean) {
        if (startNow) {
            scheduleImmediateSync(AndroidLogDateSyncWorker.SYNC_TYPE_FULL)
        } else {
            // For non-immediate sync, ensure periodic sync is running
            setupPeriodicSync()
        }
    }

    /**
     * Schedules immediate sync work with high priority.
     */
    fun scheduleImmediateSync(syncType: String = AndroidLogDateSyncWorker.SYNC_TYPE_FULL) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val inputData =
            Data
                .Builder()
                .putString(AndroidLogDateSyncWorker.KEY_SYNC_TYPE, syncType)
                .build()

        val request =
            OneTimeWorkRequestBuilder<AndroidLogDateSyncWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                // Out of expedited quota, run it as ordinary work rather than drop it. This is what
                // "Back up now" schedules, and dropping it threw the request away with nothing on
                // screen changing: the backup waited for the next periodic run, hours later, while
                // the app showed items waiting and a button that seemed to do nothing.
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

        // Unique, and an already-running sync wins. Stacking runs would put several writers on
        // the same account's repo at once, which is exactly the contention that used to drop
        // records and starve the server of request threads.
        workManager.enqueueUniqueWork(
            AndroidLogDateSyncWorker.WORK_NAME_IMMEDIATE_SYNC,
            ExistingWorkPolicy.KEEP,
            request,
        )
        Napier.d("Scheduled immediate sync: $syncType")
    }

    /**
     * Sets up periodic background sync using WorkManager.
     *
     * Constraints adapt to the current data usage mode:
     * - [DataUsageMode.Restricted]: Require unmetered network (WiFi only)
     * - [DataUsageMode.Conservative] / [DataUsageMode.Unrestricted]: Any connected network
     */
    private fun setupPeriodicSync(mode: DataUsageMode = DataUsageMode.Unrestricted) {
        val networkType =
            when (mode) {
                is DataUsageMode.Restricted -> NetworkType.UNMETERED
                else -> NetworkType.CONNECTED
            }

        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()

        // A download-only schedule meant nothing ever retried an upload on its own: an entry that
        // failed could only be attempted again when the user wrote something new or tapped sync,
        // so it never worked through its retry budget and simply sat unsynced indefinitely.
        val inputData =
            Data
                .Builder()
                .putString(AndroidLogDateSyncWorker.KEY_SYNC_TYPE, AndroidLogDateSyncWorker.SYNC_TYPE_FULL)
                .build()

        val periodicRequest =
            PeriodicWorkRequestBuilder<AndroidLogDateSyncWorker>(
                15,
                TimeUnit.MINUTES, // Minimum interval allowed by Android
            ).setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.MINUTES,
                ).build()

        // Use REPLACE so constraint changes take effect immediately
        workManager.enqueueUniquePeriodicWork(
            AndroidLogDateSyncWorker.WORK_NAME_PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicRequest,
        )

        Napier.d("Setup periodic sync work (networkType=$networkType)")
    }

    /**
     * Enables periodic background sync.
     * Called automatically when authentication state changes to authenticated.
     */
    private fun enableBackgroundSync() {
        setupPeriodicSync()
        Napier.i("Enabled background sync")
    }

    /**
     * Disables all background sync work.
     * Called automatically when authentication state changes to unauthenticated.
     */
    private fun disableBackgroundSync() {
        workManager.cancelUniqueWork(AndroidLogDateSyncWorker.WORK_NAME_PERIODIC_SYNC)
        Napier.i("Disabled background sync")
    }

    /**
     * Schedules upload-only sync for immediate changes.
     */
    fun scheduleUploadSync() {
        scheduleImmediateSync(AndroidLogDateSyncWorker.SYNC_TYPE_UPLOAD)
    }

    /**
     * Schedules download-only sync for pulling remote changes.
     */
    fun scheduleDownloadSync() {
        scheduleImmediateSync(AndroidLogDateSyncWorker.SYNC_TYPE_DOWNLOAD)
    }

    override suspend fun uploadPendingChanges(): SyncResult = defaultSyncManager.uploadPendingChanges()

    override suspend fun downloadRemoteChanges(): SyncResult = defaultSyncManager.downloadRemoteChanges()

    override suspend fun syncContent(): SyncResult = defaultSyncManager.syncContent()

    override suspend fun syncJournals(): SyncResult = defaultSyncManager.syncJournals()

    override suspend fun syncAssociations(): SyncResult = defaultSyncManager.syncAssociations()

    override suspend fun syncDrafts(): SyncResult = defaultSyncManager.syncDrafts()

    override suspend fun fullSync(): SyncResult = defaultSyncManager.fullSync()

    override val syncStatusFlow = defaultSyncManager.syncStatusFlow

    override suspend fun getSyncStatus(): SyncStatus = defaultSyncManager.getSyncStatus()

    override fun observeDeadLetters(): kotlinx.coroutines.flow.Flow<List<app.logdate.client.sync.metadata.SyncDeadLetterRecord>> =
        defaultSyncManager.observeDeadLetters()

    override suspend fun retryDeadLetter(id: String) = defaultSyncManager.retryDeadLetter(id)

    override suspend fun discardDeadLetter(id: String) = defaultSyncManager.discardDeadLetter(id)
}
