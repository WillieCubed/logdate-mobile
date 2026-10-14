package app.logdate.wear.sync.v2.media

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.logdate.client.domain.watch.WatchAudioDownloadPolicy
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Follows the persisted flag; an initial in-memory placeholder cannot cancel a valid run. */
class WatchMediaPrefetchScheduler(
    private val work: WorkManager,
    private val enabled: Flow<Boolean>,
    private val policy: Flow<WatchAudioDownloadPolicy> = flowOf(WatchAudioDownloadPolicy.WIFI_OR_CHARGING),
) {
    private var observing: Job? = null

    fun start(scope: CoroutineScope) {
        if (observing?.isActive == true) return
        observing =
            scope.launch {
                combine(enabled, policy) { active, selection -> active to selection }
                    .catch { error ->
                        Napier.e("Could not read audio download policy; disabling downloads", error)
                        emit(false to WatchAudioDownloadPolicy.ON_DEMAND)
                    }.distinctUntilChanged()
                    .collect { (active, selection) -> setPolicy(active, selection) }
            }
    }

    fun setEnabled(value: Boolean): Operation = setPolicy(value, WatchAudioDownloadPolicy.WIFI_OR_CHARGING)

    private fun setPolicy(
        enabled: Boolean,
        policy: WatchAudioDownloadPolicy,
    ): Operation {
        work.cancelUniqueWork(WatchMediaPrefetchWorker.UNIQUE_WORK_NAME)
        work.cancelUniqueWork(CHARGING_WORK)
        if (!enabled || policy == WatchAudioDownloadPolicy.ON_DEMAND) return work.cancelUniqueWork(WIFI_WORK)
        var operation: Operation? = null
        if (policy != WatchAudioDownloadPolicy.CHARGING_ONLY) {
            operation = schedule(WIFI_WORK, charging = false, NetworkType.UNMETERED)
        } else {
            work.cancelUniqueWork(WIFI_WORK)
        }
        if (policy != WatchAudioDownloadPolicy.WIFI_ONLY) operation = schedule(CHARGING_WORK, charging = true, NetworkType.CONNECTED)
        return checkNotNull(operation)
    }

    private fun schedule(
        name: String,
        charging: Boolean,
        network: NetworkType,
    ): Operation {
        val request =
            PeriodicWorkRequestBuilder<WatchMediaPrefetchWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiresCharging(charging)
                        .setRequiredNetworkType(network)
                        .build(),
                ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
        return work.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val WIFI_WORK = "watch_verified_media_prefetch_wifi"
        const val CHARGING_WORK = "watch_verified_media_prefetch_charging"
    }
}
