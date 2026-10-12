package app.logdate.wear.sync.v2.window

import app.logdate.client.sync.datalayer.v2.WearPaths
import java.util.concurrent.atomic.AtomicBoolean

/** Scoped updates cannot fall through to the legacy note/deletion handler. */
class WindowDataEvents(
    private val active: suspend () -> Boolean,
    private val applier: WindowApplier,
    private val reconciler: WindowReconciler,
) {
    private val needsCatchup = AtomicBoolean()

    suspend fun consume(item: WindowItem): Boolean {
        val note = WearPaths.parseWindowItem(item.path) != null
        val removal = WearPaths.parseWindowTombstone(item.path) != null
        if (!note && !removal) return false
        if (!active()) return true
        if (note) applier.onItem(item) else applier.onTombstone(item)
        needsCatchup.set(true)
        return true
    }

    suspend fun finishBatch() {
        if (needsCatchup.getAndSet(false)) reconciler.resync()
    }

    suspend fun legacyNotesAllowed(): Boolean = !active()
}
