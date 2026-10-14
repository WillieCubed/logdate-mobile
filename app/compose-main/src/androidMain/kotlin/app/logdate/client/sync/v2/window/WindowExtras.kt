package app.logdate.client.sync.v2.window

import app.logdate.client.domain.watch.WindowExtraIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Persisted Rewind and starred note IDs, with invalidation when those selections change. */
fun interface WindowExtras {
    suspend fun ids(): WindowExtraIds

    val changes: Flow<Unit> get() = emptyFlow()

    companion object {
        val None = WindowExtras { WindowExtraIds() }
    }
}
