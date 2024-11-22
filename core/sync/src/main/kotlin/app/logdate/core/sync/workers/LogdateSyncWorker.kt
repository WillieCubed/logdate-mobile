package app.logdate.core.sync.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.logdate.core.coroutines.AppDispatcher
import app.logdate.core.coroutines.Dispatcher
import app.logdate.core.notifications.service.RemoteNotificationProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

@HiltWorker
internal class LogdateSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    private val remoteNotificationProvider: RemoteNotificationProvider,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val SYNC_WORK_NAME = "LogdateSyncWorker"
        const val DEFAULT_SYNC_INTERVAL = 15L

        /**
         * Local time of day to sync data, measured in seconds since midnight (00:00).
         */
        private const val DEFAULT_SYNC_HOUR_OFFSET = 3

        private val syncConstraints
            get() = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)

                .build()

        /**
         * Initialize the sync worker to run periodically.
         */
        fun initialize(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                startUpSyncWork(),
            )
        }

        /**
         * Expedited one time work to sync data on app startup
         */
        internal fun startUpSyncWork() = PeriodicWorkRequestBuilder<LogdateSyncWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
        )
            .apply {
                // Calculate the time until first sync using the current time of day
                val currentTime = System.currentTimeMillis()
                val currentHour =
                    (currentTime % TimeUnit.DAYS.toMillis(1)) / TimeUnit.HOURS.toMillis(1)
                val timeUntilSync = TimeUnit.HOURS.toMillis(DEFAULT_SYNC_HOUR_OFFSET - currentHour)
                setInitialDelay(timeUntilSync, TimeUnit.MILLISECONDS)
            }
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .setConstraints(syncConstraints)
            .setInputData(LogdateSyncWorker::class.delegatedData())
            .build()

    }

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
//        remoteNotificationProvider.subscribe()
        Result.success()
    }
}


/**
 * An entry point to retrieve the [HiltWorkerFactory] at runtime
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltWorkerFactoryEntryPoint {
    fun hiltWorkerFactory(): HiltWorkerFactory
}

private const val WORKER_CLASS_NAME = "RouterWorkerDelegateClassName"

/**
 * Adds metadata to a WorkRequest to identify what [CoroutineWorker] the [DelegatingWorker] should
 * delegate to
 */
internal fun KClass<out CoroutineWorker>.delegatedData() =
    Data.Builder()
        .putString(WORKER_CLASS_NAME, qualifiedName)
        .build()