package app.logdate.feature.core.restore

import app.logdate.shared.model.diagnostics.DiagnosticReason
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal suspend fun <C, T> observeArchiveRecoveryReadiness(
    origins: Flow<C>,
    sessions: Flow<T>,
    inventory: Flow<Long> = flowOf(0L),
    identityChanges: Flow<Unit> = flowOf(Unit),
    networkChanges: Flow<Any> = flowOf(Unit),
    schedule: suspend () -> Unit,
) {
    var identityRevision = 0L
    combine(
        origins,
        sessions,
        inventory,
        identityChanges.map { ++identityRevision },
        networkChanges,
    ) { origin, session, media, keys, network ->
        Triple(origin to session, media to keys, network)
    }.distinctUntilChanged()
        .collectLatest {
            var interval = 30_000L
            while (true) {
                try {
                    schedule()
                    return@collectLatest
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Napier.w("Archive recovery scheduling remains pending and will retry")
                }
                delay(interval)
                interval = (interval * 2).coerceAtMost(900_000L)
            }
        }
}

internal fun RestoreSummary.shouldRetryMediaRecovery(): Boolean = recoveringMedia && (unavailableOriginals > 0 || unavailablePreviews > 0)

internal fun DiagnosticReason.shouldRetryArchiveMedia(hasSession: Boolean = true): Boolean =
    when (this) {
        DiagnosticReason.OFFLINE, DiagnosticReason.SERVER_UNAVAILABLE, DiagnosticReason.RATE_LIMITED,
        DiagnosticReason.QUOTA_EXCEEDED, DiagnosticReason.UNKNOWN, DiagnosticReason.NETWORK_POLICY,
        -> hasSession
        DiagnosticReason.LOCAL_STORAGE, DiagnosticReason.PERSISTENCE_FAILED -> true
        else -> false
    }

internal class ArchiveRecoveryScheduleGate {
    private val mutex = Mutex()

    suspend fun enqueue(
        hasWaiting: suspend () -> Boolean,
        enqueueSuccessor: suspend () -> Unit,
    ) {
        mutex.withLock {
            if (!hasWaiting()) enqueueSuccessor()
        }
    }
}
