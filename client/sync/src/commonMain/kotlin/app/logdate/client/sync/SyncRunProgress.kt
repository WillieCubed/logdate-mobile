package app.logdate.client.sync

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.sample
import kotlin.time.Duration

/** How far an upload run has got: [completed] of the [total] it set out to upload. */
data class SyncRunProgress(
    val completed: Int,
    val total: Int,
)

/** This status's run progress, or null when no run with anything to upload has started. */
internal fun SyncStatus.runProgress(): SyncRunProgress? {
    val total = totalForRun?.takeIf { it > 0 } ?: return null
    return SyncRunProgress(completed = completedInRun.coerceIn(0, total), total = total)
}

/**
 * The progress of the upload runs that start after [since], at most once per [period].
 *
 * A status keeps the last run's counters until the next run begins, so anything still carrying
 * [since]'s counters describes an earlier run and is skipped rather than shown as current.
 * Sampling keeps a run of hundreds of quick uploads from asking for hundreds of redraws.
 */
@OptIn(FlowPreview::class)
internal fun Flow<SyncStatus>.runProgressUpdates(
    since: SyncStatus,
    period: Duration,
): Flow<SyncRunProgress> =
    dropWhile { it.totalForRun == since.totalForRun && it.completedInRun == since.completedInRun }
        .mapNotNull { it.runProgress() }
        .distinctUntilChanged()
        .sample(period)
