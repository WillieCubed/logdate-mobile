package app.logdate.client.sync

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/**
 * Continues successful pages immediately, then yields unfinished work to the platform retry policy.
 * A pending flag can also describe a held upload, so it cannot authorize an unbounded loop.
 */
internal suspend fun runSyncBatches(run: suspend () -> SyncResult): SyncResult {
    repeat(MAX_SYNC_BATCHES - 1) {
        currentCoroutineContext().ensureActive()
        val result = run()
        if (!result.success || !result.hasMorePending) return result
        yield()
    }
    currentCoroutineContext().ensureActive()
    return run()
}

private const val MAX_SYNC_BATCHES = 8
