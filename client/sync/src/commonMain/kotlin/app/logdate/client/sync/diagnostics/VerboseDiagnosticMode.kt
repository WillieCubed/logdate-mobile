package app.logdate.client.sync.diagnostics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/** A private, time-limited setting that retains repeated allowlisted diagnostic progress. */
class VerboseDiagnosticMode(
    private val storage: DiagnosticStorage,
    private val nowMillis: () -> Long,
    private val elapsedMillis: () -> Long = { monotonicOrigin.elapsedNow().inWholeMilliseconds },
) {
    private val mutex = Mutex()
    private var loaded = false
    private var expiresAt = 0L
    private var elapsedAtAnchor = 0L
    private var budgetAtAnchor = 0L

    suspend fun enable(): Long =
        mutex.withLock {
            val until = nowMillis() + DURATION_MS
            try {
                storage.write(until.toString())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw IllegalStateException("DIAGNOSTIC_VERBOSE_ENABLE_FAILED")
            }
            expiresAt = until
            elapsedAtAnchor = elapsedMillis()
            budgetAtAnchor = DURATION_MS
            loaded = true
            until
        }

    suspend fun isEnabled(): Boolean = remainingMillis() > 0L

    suspend fun remainingMillis(): Long =
        mutex.withLock {
            load()
            val wallRemaining = (expiresAt - nowMillis()).takeIf { it in 1L..DURATION_MS } ?: 0L
            val elapsed = (elapsedMillis() - elapsedAtAnchor).coerceAtLeast(0L)
            val budgetRemaining = (budgetAtAnchor - elapsed).coerceAtLeast(0L)
            val remaining = minOf(wallRemaining, budgetRemaining)
            if (remaining == 0L && expiresAt != 0L) {
                expiresAt = 0L
                budgetAtAnchor = 0L
                try {
                    storage.clear()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Expiry remains authoritative even if deleting its persisted setting fails.
                }
            }
            remaining
        }

    suspend fun disable() =
        mutex.withLock {
            try {
                storage.clear()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw IllegalStateException("DIAGNOSTIC_VERBOSE_DISABLE_FAILED")
            }
            expiresAt = 0L
            budgetAtAnchor = 0L
            loaded = true
        }

    private suspend fun load() {
        if (loaded) return
        expiresAt =
            try {
                storage.read()?.toLongOrNull()?.takeIf { it > 0L } ?: 0L
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                0L
            }
        budgetAtAnchor = (expiresAt - nowMillis()).takeIf { it in 1L..DURATION_MS } ?: 0L
        elapsedAtAnchor = elapsedMillis()
        loaded = true
    }

    private companion object {
        const val DURATION_MS = 30 * 60 * 1000L
        val monotonicOrigin = TimeSource.Monotonic.markNow()
    }
}
