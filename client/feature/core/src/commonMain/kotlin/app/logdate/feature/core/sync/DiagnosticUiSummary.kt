package app.logdate.feature.core.sync

import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport

enum class DiagnosticRetryCondition {
    UNKNOWN,
    AUTOMATIC,
    NETWORK,
    SIGN_IN,
    KEY_RECOVERY,
    FREE_SPACE,
    UPDATE_APP,
    UPDATE_SERVER,
    REVIEW_CONFLICT,
    USER_RETRY,
}

data class DiagnosticUiSummary(
    val lastAttemptPhase: DiagnosticPhase? = null,
    val lastAttemptOutcome: DiagnosticOutcome? = null,
    val nextRetryCondition: DiagnosticRetryCondition = DiagnosticRetryCondition.UNKNOWN,
    val lastSuccessfulPhase: DiagnosticPhase? = null,
    val blockerPhase: DiagnosticPhase? = null,
    val blockerReason: DiagnosticReason? = null,
    val nextAction: DiagnosticAction = DiagnosticAction.NONE,
)

fun summarizeDiagnostics(report: SyncDiagnosticReport): DiagnosticUiSummary {
    val latestByWork = linkedMapOf<String, app.logdate.shared.model.diagnostics.SyncDiagnosticEvent>()
    report.events.forEach { event ->
        val key =
            when {
                event.operationId != null -> "operation:${event.operationId}"
                event.attemptId != null -> "attempt:${event.attemptId}"
                event.runId != null -> "run:${event.runId}:${event.phase}"
                else -> "phase:${event.phase}"
            }
        latestByWork.remove(key)
        latestByWork[key] = event
    }
    val blocker =
        latestByWork.values.lastOrNull { event ->
            event.outcome in
                setOf(
                    DiagnosticOutcome.FAILED,
                    DiagnosticOutcome.INTERRUPTED,
                    DiagnosticOutcome.CONFLICT,
                    DiagnosticOutcome.PAUSED,
                    DiagnosticOutcome.RETRY_SCHEDULED,
                )
        }
    val action =
        blocker?.let { event ->
            if (event.action != DiagnosticAction.NONE) event.action else recommendedDiagnosticAction(event.reason, event.outcome)
        } ?: DiagnosticAction.NONE
    val retryCondition =
        when {
            blocker?.reason == DiagnosticReason.OFFLINE -> DiagnosticRetryCondition.NETWORK
            blocker?.outcome == DiagnosticOutcome.RETRY_SCHEDULED -> DiagnosticRetryCondition.AUTOMATIC
            else ->
                when (action) {
                    DiagnosticAction.CONNECT -> DiagnosticRetryCondition.NETWORK
                    DiagnosticAction.SIGN_IN -> DiagnosticRetryCondition.SIGN_IN
                    DiagnosticAction.RECOVER_KEY -> DiagnosticRetryCondition.KEY_RECOVERY
                    DiagnosticAction.FREE_SPACE -> DiagnosticRetryCondition.FREE_SPACE
                    DiagnosticAction.UPDATE_APP -> DiagnosticRetryCondition.UPDATE_APP
                    DiagnosticAction.UPDATE_SERVER -> DiagnosticRetryCondition.UPDATE_SERVER
                    DiagnosticAction.REVIEW_CONFLICT -> DiagnosticRetryCondition.REVIEW_CONFLICT
                    DiagnosticAction.RETRY -> DiagnosticRetryCondition.USER_RETRY
                    else -> DiagnosticRetryCondition.UNKNOWN
                }
        }
    return DiagnosticUiSummary(
        lastAttemptPhase = report.events.lastOrNull()?.phase,
        lastAttemptOutcome = report.events.lastOrNull()?.outcome,
        nextRetryCondition = retryCondition,
        lastSuccessfulPhase = report.events.lastOrNull { it.outcome == DiagnosticOutcome.SUCCEEDED }?.phase,
        blockerPhase = blocker?.phase,
        blockerReason = blocker?.reason,
        nextAction = action,
    )
}

private fun recommendedDiagnosticAction(
    reason: DiagnosticReason,
    outcome: DiagnosticOutcome,
): DiagnosticAction =
    when (reason) {
        DiagnosticReason.OFFLINE -> DiagnosticAction.CONNECT
        DiagnosticReason.SIGN_IN_REQUIRED -> DiagnosticAction.SIGN_IN
        DiagnosticReason.LOCAL_STORAGE, DiagnosticReason.PERSISTENCE_FAILED -> DiagnosticAction.FREE_SPACE
        DiagnosticReason.KEY_RECOVERY_REQUIRED -> DiagnosticAction.RECOVER_KEY
        DiagnosticReason.INCOMPATIBLE_SERVER -> DiagnosticAction.UPDATE_SERVER
        DiagnosticReason.UNSUPPORTED_FORMAT -> DiagnosticAction.UPDATE_APP
        DiagnosticReason.CONFLICT -> DiagnosticAction.REVIEW_CONFLICT
        DiagnosticReason.SERVER_UNAVAILABLE, DiagnosticReason.RATE_LIMITED -> DiagnosticAction.RETRY
        DiagnosticReason.NONE ->
            if (outcome == DiagnosticOutcome.INTERRUPTED || outcome == DiagnosticOutcome.RETRY_SCHEDULED) {
                DiagnosticAction.RETRY
            } else {
                DiagnosticAction.NONE
            }
        else -> DiagnosticAction.CONTACT_SUPPORT
    }
