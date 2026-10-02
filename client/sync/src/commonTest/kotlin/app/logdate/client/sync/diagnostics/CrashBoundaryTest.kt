package app.logdate.client.sync.diagnostics

import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CrashBoundaryTest {
    @Test
    fun `crash sink receives only safe category and no diagnostic events or nested exception content`() =
        runTest {
            val captured = mutableListOf<String>()
            val sink = PrivateCrashAntilog(captured::add)
            val storage =
                object : DiagnosticStorage {
                    override suspend fun read(): String? = null

                    override suspend fun write(value: String) = Unit

                    override suspend fun clear() = Unit
                }
            Napier.base(sink)
            try {
                val recorder = SyncDiagnosticRecorder(DiagnosticHistory(storage, { 1L }), backgroundScope)
                recorder.record(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.FAILED))
                assertEquals(emptyList(), captured)
                Napier.e(
                    "private-journal",
                    IllegalStateException("private-path", IllegalArgumentException("private-token")),
                    "private-host",
                )
                assertEquals(listOf("APP_ERROR"), captured)
                assertFalse(captured.joinToString().contains("private-"))
            } finally {
                Napier.takeLogarithm(sink)
            }
        }
}
