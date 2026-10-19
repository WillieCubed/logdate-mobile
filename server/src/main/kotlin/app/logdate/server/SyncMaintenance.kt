package app.logdate.server

import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateCollectionsRepository
import app.logdate.server.logging.SpacePurgeNotices
import app.logdate.server.space.SpacePurgeResult
import app.logdate.server.space.SpaceStore
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
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Starts the tombstone retention sweep against the Koin-provided repository and cancels it when
 * the application stops. Without a database the sweep is forced off: there is nothing durable to
 * purge and the in-memory repositories don't accumulate tombstones across restarts.
 */
internal fun Application.installSyncMaintenance(isDatabaseAvailable: Boolean) {
    val collectionsRepository by inject<LogDateCollectionsRepository>()
    val syncMetrics by inject<SyncMetricsRegistry>()
    val spaceStore by inject<SpaceStore>()
    val blobStorage by inject<LogDateBlobStorage>()

    val maintenanceReadEnv: (String) -> String? =
        if (isDatabaseAvailable) {
            System::getenv
        } else {
            { name -> if (name == "SYNC_TOMBSTONE_PURGE_ENABLED") "false" else null }
        }
    val maintenanceJob: Job? =
        startSyncMaintenance(
            collectionsRepository = collectionsRepository,
            metrics = syncMetrics,
            readEnv = maintenanceReadEnv,
            spaceStore = spaceStore,
            blobStorage = blobStorage,
        )
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
    spaceStore: SpaceStore? = null,
    blobStorage: LogDateBlobStorage? = null,
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
            if (spaceStore != null) purgeSpacesRecorded(spaceStore, blobStorage, metrics, retentionDays.days)
            delay(intervalMinutes.minutes)
        }
    }
}

/**
 * Runs [purgeSpaces] and records it as `maintenance.space_purge`. A run that fails, or leaves a blob
 * it couldn't delete from storage, counts as a failure; a failure that stops the whole run is logged
 * as [SpacePurgeNotices.PURGE_FAILED].
 */
internal suspend fun purgeSpacesRecorded(
    store: SpaceStore,
    blobs: LogDateBlobStorage?,
    metrics: SyncMetricsRegistry,
    tombstoneRetention: Duration,
) {
    val start = System.currentTimeMillis()
    var success = false
    try {
        success = purgeSpaces(store, blobs, Clock.System.now(), tombstoneRetention).blobsFailed == 0
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Napier.e(SpacePurgeNotices.PURGE_FAILED, e)
    } finally {
        metrics.recordOperation("maintenance.space_purge", System.currentTimeMillis() - start, success)
    }
}

/**
 * Deletes space tombstones older than [tombstoneRetention], the same window as sync tombstones, and
 * blobs no record has referred to for a day, deleting each blob's object from [blobs] before its row.
 */
internal suspend fun purgeSpaces(
    store: SpaceStore,
    blobs: LogDateBlobStorage?,
    now: Instant,
    tombstoneRetention: Duration,
): SpacePurgeResult {
    val result =
        store.purge(tombstonesBefore = now - tombstoneRetention, unreferencedBlobsBefore = now - 1.days) { path ->
            blobs?.deleteBlobIfExists(path)
        }
    if (result.blobsDeleted > 0) Napier.i(SpacePurgeNotices.BLOBS_PURGED)
    return result
}
