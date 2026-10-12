package app.logdate.wear.sync.v2.media

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Follows the persisted flag; an initial in-memory placeholder cannot cancel a valid run. */
class WatchMediaPrefetchScheduler(
    private val work: WorkManager,
    private val enabled: Flow<Boolean>,
) {
    private var observing: Job? = null

    fun start(scope: CoroutineScope) {
        if (observing?.isActive == true) return
        observing =
            scope.launch {
                enabled
                    .catch { error ->
                        Napier.e("Could not read the watch media prefetch flag; disabling scheduled downloads", error)
                        emit(false)
                    }.distinctUntilChanged()
                    .collect { setEnabled(it) }
            }
    }

    fun setEnabled(value: Boolean): Operation {
        if (!value) return work.cancelUniqueWork(WatchMediaPrefetchWorker.UNIQUE_WORK_NAME)
        val request =
            PeriodicWorkRequestBuilder<WatchMediaPrefetchWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiresCharging(true)
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build(),
                ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
        return work.enqueueUniquePeriodicWork(WatchMediaPrefetchWorker.UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
