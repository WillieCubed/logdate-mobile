package app.logdate.client.sync

import app.logdate.client.networking.DataUsageMode
import app.logdate.client.sync.metadata.UploadScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class PeriodicSyncScheduleTarget(
    val mode: DataUsageMode,
    val owner: UploadScope?,
    val origin: String = owner?.serverOrigin.orEmpty(),
)

internal class PeriodicSyncScheduleController(
    private val isCurrent: suspend (PeriodicSyncScheduleTarget) -> Boolean,
    private val apply: suspend (PeriodicSyncScheduleChange) -> Unit,
) {
    private val decider = PeriodicSyncScheduleDecider()

    suspend fun collect(targets: Flow<PeriodicSyncScheduleTarget>) {
        targets.distinctUntilChanged().collectLatest { target ->
            var failures = 0
            while (true) {
                try {
                    if (isCurrent(target)) {
                        val change = decider.next(target.owner != null, target.mode, target.owner)
                        apply(change)
                        if (isCurrent(target)) {
                            decider.acknowledge(target.owner != null, target.mode, target.owner)
                            return@collectLatest
                        }
                    }
                    decider.invalidateSchedule()
                } catch (cancelled: CancellationException) {
                    decider.invalidateSchedule()
                    throw cancelled
                } catch (_: Exception) {
                    decider.invalidateSchedule()
                    Napier.w("Authenticated recovery scheduling will retry when storage is available")
                }
                delay((1_000L shl failures.coerceAtMost(5)).coerceAtMost(30_000L))
                if (failures < 5) failures++
            }
        }
    }
}
