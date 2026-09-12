package app.logdate.server

import app.logdate.server.logdate.LogDateCollectionsRepository
import app.logdate.server.logdate.RepoBackedLogDateCollectionsRepository
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.server.sync.SyncRepository
import io.github.aakira.napier.Napier
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import java.time.Duration

/**
 * Starts the tombstone retention sweep against the Koin-provided repository and cancels it when
 * the application stops. Without a database the sweep is forced off: there is nothing durable to
 * purge and the in-memory repositories don't accumulate tombstones across restarts.
 */
internal fun Application.installSyncMaintenance(isDatabaseAvailable: Boolean) {
    val collectionsRepository by inject<RepoBackedLogDateCollectionsRepository>()
    val syncMetrics by inject<SyncMetricsRegistry>()

    val maintenanceReadEnv: (String) -> String? =
        if (isDatabaseAvailable) {
            System::getenv
        } else {
            { name -> if (name == "SYNC_TOMBSTONE_PURGE_ENABLED") "false" else null }
        }
    val maintenanceJob: Job? = startSyncMaintenance(collectionsRepository, syncMetrics, maintenanceReadEnv)
    monitor.subscribe(ApplicationStopped) {
        maintenanceJob?.cancel()
    }
}

/**
 * Schedules the tombstone retention sweep.
 *
 * This drove [SyncRepository] and therefore the legacy `sync_*` tables, which no route has
 * written since sync moved to [LogDateCollectionsRepository] - so the purge ran cleanly against
 * data nobody produces any more while real tombstones accumulated forever.
 */
internal fun startSyncMaintenance(
    collectionsRepository: LogDateCollectionsRepository,
    metrics: SyncMetricsRegistry,
    readEnv: (String) -> String?,
): Job? {
    val enabled = readEnv("SYNC_TOMBSTONE_PURGE_ENABLED")?.toBooleanStrictOrNull() ?: true
    if (!enabled) return null

    val intervalMinutes = readEnv("SYNC_TOMBSTONE_PURGE_INTERVAL_MINUTES")?.toLongOrNull() ?: 60L
    val retentionDays = readEnv("SYNC_TOMBSTONE_RETENTION_DAYS")?.toLongOrNull() ?: 30L

    // Detached scope so cancelling this job doesn't take the rest of the
    // app down with it. Wrapping in runBlocking { launch { … } } here
    // would block the main thread for the lifetime of the loop and the
    // server would never reach engine.start() — that's exactly what kept
    // /health off in the canonical production deploy.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    return scope.launch {
        while (isActive) {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val cutoff = System.currentTimeMillis() - retentionDays * 24 * 60 * 60 * 1000
                val purged = collectionsRepository.purgeTombstones(cutoff)
                Napier.i(
                    "Purged sync tombstones older than $retentionDays days " +
                        "(entries=${purged.entryPurged} journals=${purged.journalPurged} " +
                        "associations=${purged.associationPurged})",
                )
                success = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Napier.e("Failed to purge sync tombstones", e)
            } finally {
                metrics.recordOperation(
                    "maintenance.tombstone_purge",
                    System.currentTimeMillis() - start,
                    success,
                )
            }
            delay(Duration.ofMinutes(intervalMinutes).toMillis())
        }
    }
}
