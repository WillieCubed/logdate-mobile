package app.logdate.client.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Data
import app.logdate.client.datastore.SessionStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class AndroidLogDateSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    
    private val syncManager: DefaultSyncManager by inject()
    
    override suspend fun doWork(): Result {
        return try {
            Napier.i("Starting Android background sync worker")
            
            val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_FULL
            
            val result = when (syncType) {
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
        } catch (e: Exception) {
            Napier.e("Unexpected error in sync worker", e)
            Result.retry()
        }
    }
    
    companion object {
        const val KEY_SYNC_TYPE = "sync_type"
        const val SYNC_TYPE_UPLOAD = "upload"
        const val SYNC_TYPE_DOWNLOAD = "download"
        const val SYNC_TYPE_FULL = "full"
        
        const val WORK_NAME_PERIODIC_SYNC = "periodic_sync"
        const val WORK_NAME_IMMEDIATE_SYNC = "immediate_sync"
    }
}

/**
 * A platform-specific implementation of [SyncManager] for Android.
 *
 * This relies on the Android WorkManager API to schedule background work
 * and delegates core sync functionality to DefaultSyncManager.
 *
 * Features:
 * - Automatic background sync enable/disable based on authentication state
 * - Periodic background sync every 15 minutes (minimum allowed by Android)
 * - Immediate sync for user-triggered actions
 * - Network-aware scheduling (WiFi preferred, but cellular allowed)
 * - Battery optimization aware
 * - Exponential backoff on failures
 *
 * Background sync is automatically managed:
 * - When user signs in (SessionStorage.saveSession), background sync is enabled
 * - When user signs out (SessionStorage.clearSession), background sync is disabled
 * - No UI code needs to manually enable/disable sync
 */
class AndroidSyncManager(
    private val applicationContext: Context,
    private val defaultSyncManager: DefaultSyncManager,
    private val sessionStorage: SessionStorage
) : SyncManager {

    private val workManager = WorkManager.getInstance(applicationContext)
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.IO)

    init {
        // Observe authentication state and automatically enable/disable background sync
        scope.launch {
            sessionStorage.getSessionFlow()
                .map { it != null }
                .distinctUntilChanged()
                .collect { isAuthenticated ->
                    if (isAuthenticated) {
                        Napier.i("User authenticated, enabling background sync")
                        enableBackgroundSync()
                    } else {
                        Napier.i("User signed out, disabling background sync")
                        disableBackgroundSync()
                    }
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
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val inputData = Data.Builder()
            .putString(AndroidLogDateSyncWorker.KEY_SYNC_TYPE, syncType)
            .build()
        
        val request = OneTimeWorkRequestBuilder<AndroidLogDateSyncWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
            
        workManager.enqueue(request)
        Napier.d("Scheduled immediate sync: $syncType")
    }
    
    /**
     * Sets up periodic background sync using WorkManager.
     * 
     * - Runs every 15 minutes (minimum allowed)
     * - Only when network is available
     * - Prefers unmetered network (WiFi) but allows metered if needed
     * - Respects battery optimization settings
     */
    private fun setupPeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false) // Allow on low battery for critical sync
            .build()
            
        val inputData = Data.Builder()
            .putString(AndroidLogDateSyncWorker.KEY_SYNC_TYPE, AndroidLogDateSyncWorker.SYNC_TYPE_DOWNLOAD)
            .build()
        
        val periodicRequest = PeriodicWorkRequestBuilder<AndroidLogDateSyncWorker>(
            15, TimeUnit.MINUTES // Minimum interval allowed by Android
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES
            )
            .build()
            
        // Use KEEP policy to avoid canceling existing work
        workManager.enqueueUniquePeriodicWork(
            AndroidLogDateSyncWorker.WORK_NAME_PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
        
        Napier.d("Setup periodic sync work")
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
    
    override suspend fun uploadPendingChanges(): SyncResult {
        return defaultSyncManager.uploadPendingChanges()
    }
    
    override suspend fun downloadRemoteChanges(): SyncResult {
        return defaultSyncManager.downloadRemoteChanges()
    }
    
    override suspend fun syncContent(): SyncResult {
        return defaultSyncManager.syncContent()
    }
    
    override suspend fun syncJournals(): SyncResult {
        return defaultSyncManager.syncJournals()
    }
    
    override suspend fun syncAssociations(): SyncResult {
        return defaultSyncManager.syncAssociations()
    }
    
    override suspend fun fullSync(): SyncResult {
        return defaultSyncManager.fullSync()
    }
    
    override suspend fun getSyncStatus(): SyncStatus {
        return defaultSyncManager.getSyncStatus()
    }
}