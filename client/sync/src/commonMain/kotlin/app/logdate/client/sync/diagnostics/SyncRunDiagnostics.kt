package app.logdate.client.sync.diagnostics

import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncResult
import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** Correlates coordinator phases without retaining exception text or user identifiers. */
internal class SyncRunDiagnostics(
    private val recorder: SyncDiagnosticRecorder?,
    private val source: DiagnosticSource? = null,
) {
    private fun record(event: SyncDiagnosticEvent) {
        recorder?.record(event, source?.scope, source?.epoch)
    }

    private val runId = Uuid.random().toString()

    suspend fun phase(
        phase: DiagnosticPhase,
        work: suspend () -> SyncResult,
    ): SyncResult {
        val started = TimeSource.Monotonic.markNow()
        val event = SyncDiagnosticEvent(phase, DiagnosticOutcome.STARTED, runId = runId, attemptId = Uuid.random().toString())
        record(event)
        try {
            val result = withContext(DiagnosticCorrelation(runId)) { work() }
            val reason =
                when (result.errors.firstOrNull()?.type) {
                    SyncErrorType.NETWORK_ERROR -> DiagnosticReason.OFFLINE
                    SyncErrorType.AUTHENTICATION_ERROR -> DiagnosticReason.SIGN_IN_REQUIRED
                    SyncErrorType.SERVER_ERROR -> DiagnosticReason.SERVER_UNAVAILABLE
                    SyncErrorType.CONFLICT_ERROR -> DiagnosticReason.CONFLICT
                    SyncErrorType.STORAGE_ERROR -> DiagnosticReason.LOCAL_STORAGE
                    SyncErrorType.UNKNOWN_ERROR -> DiagnosticReason.UNKNOWN
                    null -> if (result.success) DiagnosticReason.NONE else DiagnosticReason.UNKNOWN
                }
            val action =
                when (reason) {
                    DiagnosticReason.NONE -> DiagnosticAction.NONE
                    DiagnosticReason.OFFLINE -> DiagnosticAction.CONNECT
                    DiagnosticReason.SIGN_IN_REQUIRED -> DiagnosticAction.SIGN_IN
                    DiagnosticReason.CONFLICT -> DiagnosticAction.REVIEW_CONFLICT
                    DiagnosticReason.LOCAL_STORAGE -> DiagnosticAction.RETRY
                    else -> DiagnosticAction.RETRY
                }
            record(
                event.copy(
                    outcome = if (result.success) DiagnosticOutcome.SUCCEEDED else DiagnosticOutcome.FAILED,
                    reason = reason,
                    action = action,
                    retryable = !result.success && result.errors.all { it.retryable },
                    durationMs = started.elapsedNow().inWholeMilliseconds.coerceAtLeast(0),
                ),
            )
            return result
        } catch (cancelled: CancellationException) {
            record(event.copy(outcome = DiagnosticOutcome.INTERRUPTED))
            throw cancelled
        } catch (failure: Exception) {
            record(
                event.copy(
                    outcome = DiagnosticOutcome.FAILED,
                    reason = DiagnosticReason.UNKNOWN,
                    action = DiagnosticAction.RETRY,
                    retryable = true,
                    durationMs = started.elapsedNow().inWholeMilliseconds.coerceAtLeast(0),
                ),
            )
            throw failure
        }
    }
}
