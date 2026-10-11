package app.logdate.wear.v2.surfaces

import app.logdate.wear.v2.today.TodayWindow
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Follows the new watch app's flag. While it is on, the new surfaces are offered and refreshed: when
 * the outbox gains a new mutation (a capture, edit or deletion made on the watch) and when the day changes. Changes
 * from the phone reach [SurfaceUpdater.refreshForSync] from the Data Layer listener.
 */
class SurfaceCoordinator(
    private val newAppEnabled: Flow<Boolean>,
    private val components: SurfaceComponents,
    private val updater: SurfaceUpdater,
    private val pendingUploads: Flow<Set<String>>,
    private val days: Flow<TodayWindow>,
) {
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            newAppEnabled.distinctUntilChanged().collectLatest { on ->
                applyComponents(on)
                updater.setActive(on)
                if (!on) return@collectLatest
                updater.refreshNow()
                coroutineScope {
                    launch { refreshWhenTheOutboxGrows() }
                    launch { days.drop(1).collect { updater.refreshNow() } }
                }
            }
        }

    private suspend fun refreshWhenTheOutboxGrows() {
        var previous: Set<String>? = null
        pendingUploads.collect { mutations ->
            val grew = previous?.let { (mutations - it).isNotEmpty() } ?: false
            previous = mutations
            if (grew) updater.refreshNow()
        }
    }

    private fun applyComponents(newAppOn: Boolean) {
        try {
            components.apply(newAppOn)
        } catch (e: Exception) {
            Napier.e("Couldn't switch the watch's tiles and complications for the ${if (newAppOn) "new" else "current"} app", e)
        }
    }
}
