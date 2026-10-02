package app.logdate.feature.core.sync

import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiagnosticUiSummaryTest {
    @Test
    fun `summary shows last attempt and separates network wait from scheduled retry`() {
        val event = SyncDiagnosticEvent(DiagnosticPhase.MEDIA, DiagnosticOutcome.FAILED, DiagnosticReason.OFFLINE)
        val offline = summarizeDiagnostics(SyncDiagnosticReport("a", events = listOf(event)))
        assertEquals(DiagnosticPhase.MEDIA, offline.lastAttemptPhase)
        assertEquals(DiagnosticOutcome.FAILED, offline.lastAttemptOutcome)
        assertEquals(DiagnosticRetryCondition.NETWORK, offline.nextRetryCondition)
        val scheduled =
            summarizeDiagnostics(
                SyncDiagnosticReport(
                    "a",
                    events =
                        listOf(
                            event.copy(outcome = DiagnosticOutcome.RETRY_SCHEDULED, reason = DiagnosticReason.SERVER_UNAVAILABLE),
                        ),
                ),
            )
        assertEquals(DiagnosticRetryCondition.AUTOMATIC, scheduled.nextRetryCondition)
        val recovered =
            summarizeDiagnostics(
                SyncDiagnosticReport(
                    "a",
                    events =
                        listOf(
                            event,
                            event.copy(outcome = DiagnosticOutcome.SUCCEEDED, reason = DiagnosticReason.NONE),
                        ),
                ),
            )
        assertEquals(DiagnosticRetryCondition.UNKNOWN, recovered.nextRetryCondition)
    }

    @Test
    fun `latest unresolved blocker exposes finite next action and last success`() {
        val report =
            SyncDiagnosticReport(
                reportId = "a",
                events =
                    listOf(
                        SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.SUCCEEDED),
                        SyncDiagnosticEvent(
                            DiagnosticPhase.MEDIA,
                            DiagnosticOutcome.FAILED,
                            reason = DiagnosticReason.OFFLINE,
                            operationId = "private-correlation",
                        ),
                    ),
            )

        val summary = summarizeDiagnostics(report)

        assertEquals(DiagnosticPhase.FETCH, summary.lastSuccessfulPhase)
        assertEquals(DiagnosticPhase.MEDIA, summary.blockerPhase)
        assertEquals(DiagnosticReason.OFFLINE, summary.blockerReason)
        assertEquals(DiagnosticAction.CONNECT, summary.nextAction)
    }

    @Test
    fun `later success resolves the same operation blocker`() {
        val report =
            SyncDiagnosticReport(
                reportId = "a",
                events =
                    listOf(
                        SyncDiagnosticEvent(
                            DiagnosticPhase.UPLOAD,
                            DiagnosticOutcome.FAILED,
                            DiagnosticReason.SERVER_UNAVAILABLE,
                            operationId = "op",
                        ),
                        SyncDiagnosticEvent(DiagnosticPhase.UPLOAD, DiagnosticOutcome.SUCCEEDED, operationId = "op"),
                    ),
            )

        val summary = summarizeDiagnostics(report)

        assertNull(summary.blockerPhase)
        assertEquals(DiagnosticAction.NONE, summary.nextAction)
        assertEquals(DiagnosticPhase.UPLOAD, summary.lastSuccessfulPhase)
    }

    @Test
    fun `interrupted attempt suggests retry without assigning a failure cause`() {
        val report =
            SyncDiagnosticReport(
                reportId = "a",
                events =
                    listOf(
                        SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.INTERRUPTED, attemptId = "interrupted"),
                    ),
            )

        val summary = summarizeDiagnostics(report)

        assertEquals(DiagnosticReason.NONE, summary.blockerReason)
        assertEquals(DiagnosticAction.RETRY, summary.nextAction)
    }
}
