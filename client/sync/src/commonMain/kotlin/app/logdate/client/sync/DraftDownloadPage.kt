package app.logdate.client.sync

import app.logdate.client.database.entities.sync.DownloadInboxEntity
import app.logdate.client.sync.cloud.DraftSyncResult
import app.logdate.client.sync.diagnostics.DiagnosticSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import kotlin.uuid.Uuid

/** One fetched page, the inbox rows staged for it, and how each of its records was settled. */
internal class DraftDownloadPage(
    val result: DraftSyncResult,
    val stagedRows: Map<String, DownloadInboxEntity>,
    val capturedSource: DiagnosticSource?,
    val applyAttempts: Map<String, String>,
    private val downloadInbox: DownloadInbox?,
    private val downloadEngine: SyncDownloadEngine,
) {
    val appliedIds = mutableListOf<Pair<String, Long>>()
    val skippedIds = mutableListOf<Pair<String, Long>>()
    val failedIds = mutableListOf<Pair<String, Long>>()

    suspend fun settleApplied(downloadScope: DownloadScope?) {
        for ((id, version) in appliedIds) {
            downloadEngine.markRecovered(EntityType.DRAFT, Uuid.parse(id))
            if (downloadScope != null) downloadInbox?.applied("DRAFT", id, version, downloadScope)
            val row = stagedRows[id]
            val attemptId = applyAttempts[id]
            if (row != null && attemptId != null) {
                recordApply(row, attemptId, DiagnosticOutcome.SUCCEEDED)
            }
        }
    }

    suspend fun settleSkipped(downloadScope: DownloadScope?) {
        for ((id, version) in skippedIds) {
            if (downloadScope != null) downloadInbox?.skipped("DRAFT", id, version, downloadScope)
            val row = stagedRows[id]
            val attemptId = applyAttempts[id]
            if (row != null && attemptId != null) {
                recordApply(row, attemptId, DiagnosticOutcome.CONFLICT, DiagnosticReason.CONFLICT, DiagnosticAction.REVIEW_CONFLICT)
            }
        }
    }

    /** Schedules a retry for records that failed to apply, could not be decrypted, or used an unknown format. */
    suspend fun settleFailed(downloadScope: DownloadScope?) {
        for ((id, version) in failedIds) {
            if (downloadScope != null) {
                downloadInbox?.failed(
                    "DRAFT",
                    id,
                    DiagnosticReason.LOCAL_STORAGE.name,
                    version,
                    downloadScope,
                    attemptId = applyAttempts[id],
                    source = capturedSource,
                )
            }
        }
        for (id in result.unreadable) {
            val version = result.unreadableVersions[id] ?: continue
            if (downloadScope != null) {
                downloadInbox?.failed("DRAFT", id.toString(), DiagnosticReason.KEY_RECOVERY_REQUIRED.name, version, downloadScope)
            }
        }
        for (id in result.incompatible) {
            val version = stagedRows[id.toString()]?.version ?: continue
            if (downloadScope != null) {
                downloadInbox?.failed("DRAFT", id.toString(), DiagnosticReason.CORRUPT_PAYLOAD.name, version, downloadScope)
            }
        }
    }

    fun recordApply(
        row: DownloadInboxEntity,
        attemptId: String,
        outcome: DiagnosticOutcome,
        reason: DiagnosticReason = DiagnosticReason.NONE,
        action: DiagnosticAction = DiagnosticAction.NONE,
    ) {
        downloadInbox?.recordDiagnostic(
            SyncDiagnosticEvent(
                DiagnosticPhase.APPLY,
                outcome,
                reason = reason,
                action = action,
                operationId = row.operationId,
                attemptId = attemptId,
            ),
            capturedSource,
        )
    }
}
