package app.logdate.wear.v2.surfaces

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Asks the system to draw the watch's tiles and complications again. */
fun interface SurfaceRefresher {
    fun refreshAll()
}

/** [SurfaceRefresher] for every tile and complication in [SurfaceCatalog]. */
class AndroidSurfaceRefresher(
    private val context: Context,
) : SurfaceRefresher {
    override fun refreshAll() {
        val tiles = TileService.getUpdater(context)
        SurfaceCatalog.TILES.forEach(tiles::requestUpdate)
        SurfaceCatalog.COMPLICATIONS.forEach { service ->
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, service)).requestUpdateAll()
        }
    }
}

/**
 * Refreshes the watch's surfaces: at once after something the person does on the watch, and at most
 * once every [minSyncInterval] for changes sync brings in, the pace complications are meant to update
 * at. While inactive it does nothing, and the current watch app's surfaces update on their own
 * schedule.
 */
class SurfaceUpdater(
    private val refresher: SurfaceRefresher,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val minSyncInterval: Duration = DEFAULT_MIN_SYNC_INTERVAL,
) {
    private val lock = Any()
    private var active = false
    private var lastRefreshAt: Instant? = null
    private var pending: Job? = null

    fun setActive(active: Boolean) {
        synchronized(lock) {
            this.active = active
            if (!active) cancelPending()
        }
    }

    /** Refreshes now, for something the person did on the watch; a refresh waiting for sync is then not needed. */
    fun refreshNow() {
        synchronized(lock) {
            if (!active) return
            cancelPending()
            lastRefreshAt = clock.now()
            refreshSafely()
        }
    }

    /** Refreshes for a change sync brought in, no sooner than [minSyncInterval] after the last refresh. */
    fun refreshForSync() {
        synchronized(lock) {
            if (!active || pending?.isActive == true) return
            val last = lastRefreshAt
            val wait = if (last == null) Duration.ZERO else (last + minSyncInterval - clock.now()).coerceAtLeast(Duration.ZERO)
            pending =
                scope.launch {
                    delay(wait)
                    synchronized(lock) {
                        if (active) {
                            lastRefreshAt = clock.now()
                            refreshSafely()
                        }
                    }
                }
        }
    }

    private fun cancelPending() {
        pending?.cancel()
        pending = null
    }

    private fun refreshSafely() {
        try {
            refresher.refreshAll()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't ask the system to redraw the watch's tiles and complications", e)
        }
    }

    companion object {
        val DEFAULT_MIN_SYNC_INTERVAL: Duration = 5.minutes
    }
}
