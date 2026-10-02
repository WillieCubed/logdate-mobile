package app.logdate.client.sync.diagnostics

import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import io.github.aakira.napier.LogLevel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LocalLogBoundaryTest {
    @Test
    fun `untrusted messages tags and nested causes never reach platform log sinks`() {
        val marker = "PRIVATE_JOURNAL_CREDENTIAL_PATH_SENTINEL"
        val captured = mutableListOf<String>()
        val sink = PrivateLocalAntilog { _, value -> captured += value }
        sink.log(LogLevel.ERROR, marker, IllegalStateException(marker, RuntimeException(marker)), marker)
        sink.log(LogLevel.WARNING, SyncDiagnosticRecorder.TAG, null, "{\"message\":\"$marker\"}")
        assertFalse(captured.joinToString().contains(marker))
        assertEquals(listOf("APP_ERROR", "APP_WARNING"), captured)
    }

    @Test
    fun `validated typed diagnostics survive the local boundary`() {
        val event = SyncDiagnosticEvent(DiagnosticPhase.MEDIA, DiagnosticOutcome.FAILED)
        val encoded = Json.encodeToString(event)
        val captured = mutableListOf<String>()
        val sink = PrivateLocalAntilog { _, value -> captured += value }
        sink.log(LogLevel.INFO, SyncDiagnosticRecorder.TAG, null, encoded)
        assertEquals(listOf(encoded), captured)
    }
}
