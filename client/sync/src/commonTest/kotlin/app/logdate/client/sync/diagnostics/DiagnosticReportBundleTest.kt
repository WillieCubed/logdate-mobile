package app.logdate.client.sync.diagnostics

import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DiagnosticReportBundleTest {
    @Test
    fun `preview matches export and explains observations uncertainty and safe actions`() {
        val alias = Uuid.random().toString()
        val report =
            SyncDiagnosticReport(
                Uuid.random().toString(),
                events =
                    listOf(
                        SyncDiagnosticEvent(DiagnosticPhase.PERSIST, DiagnosticOutcome.SUCCEEDED),
                        SyncDiagnosticEvent(
                            DiagnosticPhase.MEDIA,
                            DiagnosticOutcome.FAILED,
                            reason = DiagnosticReason.MISSING_MEDIA,
                            recordAlias = alias,
                            attemptCount = 3,
                        ),
                    ),
            )
        val bundle = DiagnosticReportBundles.prepare(report)
        assertEquals(setOf("summary.md", "report.json", "events.jsonl", "schema.json"), bundle.entries.keys)
        assertEquals(bundle.summary, bundle.entries["summary.md"])
        listOf(
            "Observed facts",
            "Inferred causes",
            "Missing evidence",
            "Suggested actions",
            "MEDIA",
            "MISSING_MEDIA",
            "CONTACT_SUPPORT",
        ).forEach {
            assertTrue(bundle.summary.contains(it), "Missing approved report detail: $it")
        }
        val exported = DiagnosticReportCodec.decode(bundle.entries.getValue("report.json"))
        assertNotEquals(alias, exported.events.last().recordAlias)
        assertEquals(
            2,
            bundle.entries
                .getValue("events.jsonl")
                .trim()
                .lines()
                .size,
        )
        val another = DiagnosticReportCodec.decode(DiagnosticReportBundles.prepare(report).entries.getValue("report.json"))
        assertNotEquals(exported.events.last().recordAlias, another.events.last().recordAlias)
    }

    @Test
    fun `unvalidated private values cannot reach preview or archive entries`() {
        val report =
            SyncDiagnosticReport(
                Uuid.random().toString(),
                events =
                    listOf(
                        SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.FAILED, recordAlias = "private-marker"),
                    ),
            )
        assertFailsWith<IllegalArgumentException> { DiagnosticReportBundles.prepare(report) }
    }

    @Test
    fun `interruption has a safe retry action and a completed request is not inferred unfinished`() {
        val attempt = Uuid.random().toString()
        val start = SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.STARTED, attemptId = attempt)
        val completed =
            DiagnosticReportBundles.prepare(
                SyncDiagnosticReport(Uuid.random().toString(), events = listOf(start, start.copy(outcome = DiagnosticOutcome.SUCCEEDED))),
            )
        kotlin.test.assertFalse(completed.summary.contains("An unfinished attempt"))
        val interrupted =
            DiagnosticReportBundles.prepare(
                SyncDiagnosticReport(Uuid.random().toString(), events = listOf(start, start.copy(outcome = DiagnosticOutcome.INTERRUPTED))),
            )
        assertTrue(interrupted.summary.contains("- RETRY"))
    }
}
