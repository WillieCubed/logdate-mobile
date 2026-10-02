package app.logdate.client.sync.diagnostics

import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** A preview and its exact export entries are prepared together before sharing. */
class DiagnosticReportBundle internal constructor(
    val summary: String,
    val entries: Map<String, String>,
)

object DiagnosticReportBundles {
    fun prepare(report: SyncDiagnosticReport): DiagnosticReportBundle {
        DiagnosticReportCodec.validate(report)
        val exported = exportCopy(report)
        val encoded = DiagnosticReportCodec.encode(exported)
        val unresolved = unresolvedEvents(exported.events)
        val summary = summarize(exported, unresolved, suggestedActions(unresolved))
        return DiagnosticReportBundle(
            summary,
            linkedMapOf(
                "summary.md" to summary,
                "report.json" to encoded,
                "events.jsonl" to
                    exported.events.joinToString("\n", postfix = if (exported.events.isEmpty()) "" else "\n") { Json.encodeToString(it) },
                "schema.json" to "{\"schemaVersion\":1,\"bundleVersion\":1,\"timing\":\"relative-milliseconds\"}",
            ),
        )
    }

    private fun exportCopy(report: SyncDiagnosticReport): SyncDiagnosticReport {
        val aliases = mutableMapOf<String, String>()
        val start = report.events.minOfOrNull { it.elapsedMs } ?: 0L
        return report.copy(
            reportId = Uuid.random().toString(),
            events =
                report.events.map { event ->
                    event.copy(
                        elapsedMs = event.elapsedMs - start,
                        recordAlias = event.recordAlias?.let { aliases.getOrPut(it) { Uuid.random().toString() } },
                    )
                },
        )
    }

    private fun correlation(event: SyncDiagnosticEvent): String? =
        event.operationId?.let { "operation:$it" }
            ?: event.attemptId?.let { "attempt:$it" }
            ?: event.requestId?.let { "request:$it" }

    private fun unresolvedEvents(events: List<SyncDiagnosticEvent>): List<SyncDiagnosticEvent> {
        val latest = events.filter { correlation(it) != null }.associateBy { correlation(it) }
        return events.filter {
            (correlation(it) == null || latest[correlation(it)] === it) && it.outcome != DiagnosticOutcome.SUCCEEDED
        }
    }

    private fun suggestedActions(unresolved: List<SyncDiagnosticEvent>): List<DiagnosticAction> =
        unresolved
            .map {
                when {
                    it.action != DiagnosticAction.NONE -> it.action
                    it.outcome == DiagnosticOutcome.STARTED || it.outcome == DiagnosticOutcome.INTERRUPTED -> DiagnosticAction.RETRY
                    else -> recommendedAction(it.reason)
                }
            }.filter { it != DiagnosticAction.NONE }
            .distinct()

    private fun summarize(
        exported: SyncDiagnosticReport,
        unresolved: List<SyncDiagnosticEvent>,
        actions: List<DiagnosticAction>,
    ): String =
        buildString {
            appendLine("# Sync diagnostic report")
            appendLine()
            appendLine("## Observed facts")
            appendLine("Events: ${exported.events.size}; omitted events: ${exported.droppedEvents}.")
            appendLine(
                "Last successful phase: ${exported.events
                    .lastOrNull {
                        it.outcome == DiagnosticOutcome.SUCCEEDED
                    }?.phase ?: "UNKNOWN"}.",
            )
            exported.events.forEach {
                appendLine(
                    "- +${it.elapsedMs} ms: ${it.phase} ${it.outcome}; ${it.reason}; " +
                        "attempt ${it.attemptCount}; pending ${it.pendingCount}.",
                )
            }
            appendLine()
            appendLine("## Inferred causes")
            appendLine("Recorded reasons classify failures; they do not establish the underlying cause.")
            if (unresolved.any { it.outcome == DiagnosticOutcome.STARTED || it.outcome == DiagnosticOutcome.INTERRUPTED }) {
                appendLine("An unfinished attempt may indicate interruption or missing retained events.")
            }
            appendLine()
            appendLine("## Missing evidence")
            appendLine(
                "This bounded report does not prove content equality, media integrity, current server state, or complete recovery.",
            )
            appendLine()
            appendLine("## Suggested actions")
            if (actions.isEmpty()) appendLine("No corrective action is established by the retained events.")
            actions.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Saved or shared copies are outside the app's local retention and deletion controls.")
        }
}

internal fun recommendedAction(reason: DiagnosticReason): DiagnosticAction =
    when (reason) {
        DiagnosticReason.NONE -> DiagnosticAction.NONE
        DiagnosticReason.OFFLINE -> DiagnosticAction.CONNECT
        DiagnosticReason.SIGN_IN_REQUIRED -> DiagnosticAction.SIGN_IN
        DiagnosticReason.LOCAL_STORAGE, DiagnosticReason.PERSISTENCE_FAILED -> DiagnosticAction.FREE_SPACE
        DiagnosticReason.KEY_RECOVERY_REQUIRED -> DiagnosticAction.RECOVER_KEY
        DiagnosticReason.INCOMPATIBLE_SERVER -> DiagnosticAction.UPDATE_SERVER
        DiagnosticReason.UNSUPPORTED_FORMAT -> DiagnosticAction.UPDATE_APP
        DiagnosticReason.CONFLICT -> DiagnosticAction.REVIEW_CONFLICT
        DiagnosticReason.SERVER_UNAVAILABLE, DiagnosticReason.RATE_LIMITED -> DiagnosticAction.RETRY
        else -> DiagnosticAction.CONTACT_SUPPORT
    }
