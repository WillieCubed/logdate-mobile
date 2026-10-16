package app.logdate.feature.core.restore

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Serializes source replacement and attachment repair for the same recovery. */
internal object ArchiveRecoveryLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(
        id: String,
        action: suspend () -> T,
    ): T = locks.getOrPut(id) { Mutex() }.withLock { action() }
}
