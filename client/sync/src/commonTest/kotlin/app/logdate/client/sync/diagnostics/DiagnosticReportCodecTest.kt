package app.logdate.client.sync.diagnostics

import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiagnosticReportCodecTest {
    @Test
    fun `build and frame context rejects private strings and unbounded metadata`() {
        val base = SyncDiagnosticEvent(DiagnosticPhase.APPLY, DiagnosticOutcome.FAILED)
        val invalid =
            listOf(
                base.copy(schemaVersion = 2),
                base.copy(
                    context =
                        app.logdate.shared.model.diagnostics
                            .DiagnosticContext(serverBuild = "private-token-path-marker"),
                ),
                base.copy(
                    context =
                        app.logdate.shared.model.diagnostics
                            .DiagnosticContext(appBuild = -1),
                ),
                base.copy(
                    context =
                        app.logdate.shared.model.diagnostics
                            .DiagnosticContext(osVersion = List(50) { 1 }),
                ),
                base.copy(
                    context =
                        app.logdate.shared.model.diagnostics
                            .DiagnosticContext(appVersion = listOf(-1)),
                ),
                base.copy(
                    frames =
                        List(9) {
                            app.logdate.shared.model.diagnostics.DiagnosticFrame(
                                app.logdate.shared.model.diagnostics.DiagnosticComponent.SYNC,
                                10,
                            )
                        },
                ),
                base.copy(
                    frames =
                        listOf(
                            app.logdate.shared.model.diagnostics.DiagnosticFrame(
                                app.logdate.shared.model.diagnostics.DiagnosticComponent.SYNC,
                                -1,
                            ),
                        ),
                ),
            )
        invalid.forEach { event ->
            assertFailsWith<IllegalArgumentException> { DiagnosticReportCodec.validateEvent(event) }
        }
    }

    private val id = "00000000-0000-4000-8000-000000000001"

    @Test
    fun `valid report round trips without adding private fields`() {
        val report =
            SyncDiagnosticReport(id, events = listOf(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.FAILED, requestId = id)))
        assertEquals(report, DiagnosticReportCodec.decode(DiagnosticReportCodec.encode(report)))
    }

    @Test
    fun `untrusted fields strings identifiers and unbounded counters are rejected`() {
        for (payload in listOf(
            """{"reportId":"$id","journal":"secret"}""",
            """{"reportId":"$id","schemaVersion":2}""",
            """{"reportId":"private-account"}""",
            """{"reportId":"$id","events":[{"phase":"FETCH","outcome":"FAILED","recordAlias":"private-path"}]}""",
            """{"reportId":"$id","events":[{"phase":"FETCH","outcome":"FAILED","durationMs":-1}]}""",
            " ".repeat(262145),
        )) {
            assertFailsWith<IllegalArgumentException> { DiagnosticReportCodec.decode(payload) }
        }
    }

    @Test
    fun `correlation identifiers reject time and hardware derived UUIDs`() {
        val timeBased = "00000000-0000-1000-8000-000000000001"
        assertFailsWith<IllegalArgumentException> {
            DiagnosticReportCodec.encode(SyncDiagnosticReport(timeBased))
        }
        assertFailsWith<IllegalArgumentException> {
            DiagnosticReportCodec.encode(
                SyncDiagnosticReport(
                    id,
                    events =
                        listOf(
                            SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.STARTED, requestId = timeBased),
                        ),
                ),
            )
        }
    }

    @Test
    fun `export validates locally constructed reports too`() {
        assertFailsWith<IllegalArgumentException> { DiagnosticReportCodec.encode(SyncDiagnosticReport("private-account")) }
    }
}
