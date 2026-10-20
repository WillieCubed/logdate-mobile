package app.logdate.client.sync.recovery

import app.logdate.client.database.dao.sync.DownloadInboxDao
import app.logdate.client.database.entities.sync.DownloadInboxEntity
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.diagnostics.DiagnosticSource
import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import kotlin.uuid.Uuid

/** Retains download retry policy and diagnostic classification independently of inbox application. */
internal class DownloadInboxFailureRecorder(
    private val dao: DownloadInboxDao,
    private val transactions: SyncTransactionManager,
    private val now: () -> Long,
    private val recordDiagnostic: (SyncDiagnosticEvent, DiagnosticSource?) -> Unit,
) {
    suspend fun record(
        type: String,
        id: String,
        reason: String,
        version: Long?,
        selected: DownloadScope,
        attemptId: String?,
        source: DiagnosticSource?,
    ) {
        var retried: DownloadInboxEntity? = null
        transactions.withTransaction {
            val row = dao.get(selected.owner, selected.origin, type, id) ?: return@withTransaction
            if (row.state == "APPLIED" || (version != null && row.version != version)) return@withTransaction
            val policyDeferred = reason == DiagnosticReason.NETWORK_POLICY.name
            val attempt = if (policyDeferred) row.attempts else (row.attempts + 1).coerceAtMost(30)
            val delay = if (policyDeferred) 30_000L else (1000L shl attempt.coerceAtMost(12)).coerceAtMost(60 * 60 * 1000L)
            val updated =
                row.copy(
                    state = if (policyDeferred) "WAITING" else "FAILED",
                    attempts = attempt,
                    nextAttemptAt = now() + delay,
                    reason = DiagnosticReason.entries.firstOrNull { it.name == reason }?.name ?: DiagnosticReason.UNKNOWN.name,
                )
            dao.put(updated)
            retried = updated
        }
        retried?.let { row ->
            val classified = DiagnosticReason.entries.firstOrNull { it.name == row.reason } ?: DiagnosticReason.UNKNOWN
            recordDiagnostic(
                SyncDiagnosticEvent(
                    phase = if (type.startsWith("MEDIA_")) DiagnosticPhase.MEDIA else DiagnosticPhase.APPLY,
                    outcome =
                        if (classified ==
                            DiagnosticReason.NETWORK_POLICY
                        ) {
                            DiagnosticOutcome.PAUSED
                        } else {
                            DiagnosticOutcome.RETRY_SCHEDULED
                        },
                    reason = classified,
                    action = actionFor(classified),
                    operationId = row.operationId,
                    attemptId = attemptId ?: Uuid.random().toString(),
                    attemptCount = row.attempts,
                    retryable = true,
                ),
                source,
            )
        }
    }

    private fun actionFor(reason: DiagnosticReason): DiagnosticAction =
        when (reason) {
            DiagnosticReason.NETWORK_POLICY -> DiagnosticAction.NONE
            DiagnosticReason.OFFLINE -> DiagnosticAction.CONNECT
            DiagnosticReason.SIGN_IN_REQUIRED -> DiagnosticAction.SIGN_IN
            DiagnosticReason.LOCAL_STORAGE -> DiagnosticAction.RETRY
            DiagnosticReason.KEY_RECOVERY_REQUIRED -> DiagnosticAction.RECOVER_KEY
            DiagnosticReason.INCOMPATIBLE_SERVER -> DiagnosticAction.UPDATE_SERVER
            DiagnosticReason.UNSUPPORTED_FORMAT -> DiagnosticAction.UPDATE_APP
            DiagnosticReason.CONFLICT -> DiagnosticAction.REVIEW_CONFLICT
            else -> DiagnosticAction.RETRY
        }
}
