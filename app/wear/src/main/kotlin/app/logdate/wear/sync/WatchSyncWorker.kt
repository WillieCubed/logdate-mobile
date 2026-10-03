package app.logdate.wear.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.logdate.client.sync.SyncManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Runs the watch's sync when [WatchSyncScheduler] says something is due, then sets the next run.
 *
 * The next run is set before this one returns, while the watch is still awake for this work. Setting
 * it replaces this work, which stops it, so it is the last thing a run does. The sync itself runs to
 * the end even if the work is stopped part way through, because an upload cut off mid-file would only
 * start again.
 */
class WatchSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val syncManager: SyncManager by inject()
    private val scheduler: WatchSyncScheduler by inject()

    override suspend fun doWork(): Result {
        val result =
            try {
                withContext(NonCancellable) { syncManager.fullSync() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Watch sync run failed", e)
                return Result.retry()
            }
        if (!result.success) {
            Napier.w("Watch sync run finished with errors: ${result.errors.joinToString { it.message }}")
        }
        scheduler.scheduleNext()
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "watch_sync"
    }
}

/** [WatchSyncWork] as unique WorkManager work, so only one run ever waits. */
class WorkManagerWatchSyncWork(
    private val workManager: WorkManager,
) : WatchSyncWork {
    override fun runAfter(delayMs: Long) {
        val request =
            OneTimeWorkRequestBuilder<WatchSyncWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniqueWork(WatchSyncWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
