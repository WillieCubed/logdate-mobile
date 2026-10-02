package app.logdate.server.sync

import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SyncMetricsRegistryTest {
    @Test
    fun `diagnostic counters aggregate classifications without retaining identifiers`() {
        val registry = SyncMetricsRegistry()
        val firstId = "00000000-0000-4000-8000-000000000001"
        val secondId = "00000000-0000-4000-8000-000000000002"
        val event =
            SyncDiagnosticEvent(
                DiagnosticPhase.ARCHIVE,
                DiagnosticOutcome.RETRY_SCHEDULED,
                reason = DiagnosticReason.SERVER_UNAVAILABLE,
                requestId = firstId,
                recordAlias = firstId,
            )
        registry.recordDiagnostic(event)
        registry.recordDiagnostic(event.copy(requestId = secondId, recordAlias = secondId))
        registry.recordDiagnostic(event.copy(requestId = "private-path-marker"))

        val snapshot = registry.snapshot()
        assertEquals(2L, snapshot.diagnostics.single().count)
        assertEquals(DiagnosticOutcome.RETRY_SCHEDULED, snapshot.diagnostics.single().outcome)
        assertEquals(1L, snapshot.diagnosticDrops)
        assertFalse(snapshot.toString().contains(firstId))
        assertFalse(snapshot.toString().contains(secondId))
        assertFalse(snapshot.toString().contains("private-path-marker"))
    }

    @Test
    fun `duration histograms have fixed cumulative buckets including overflow`() {
        val registry = SyncMetricsRegistry()
        listOf(0L, 100L, 101L, 60_000L).forEach {
            registry.recordOperation("sync.backup.upload", it, true)
        }

        val buckets =
            registry
                .snapshot()
                .operations
                .single()
                .durationBuckets
        assertEquals(listOf(100L, 500L, 1_000L, 5_000L, 30_000L, Long.MAX_VALUE), buckets.map { it.upperBoundMs })
        assertEquals(listOf(2L, 3L, 3L, 3L, 3L, 4L), buckets.map { it.count })
    }

    @Test
    fun `unrecognized operation labels never enter metrics`() {
        val registry = SyncMetricsRegistry()
        val privateMarker = "https://private-host.invalid/private-record-token"
        repeat(1_000) { registry.recordOperation("$privateMarker/$it", 10, false) }
        registry.recordOperation("sync.backup.upload", 20, true, 100)

        val snapshot = registry.snapshot()
        assertEquals(listOf("sync.backup.upload"), snapshot.operations.map { it.name })
        assertFalse(snapshot.toString().contains(privateMarker))
    }

    @Test
    fun `negative elapsed time cannot decrease duration counters`() {
        val registry = SyncMetricsRegistry()
        registry.recordOperation("sync.content.upload", 10, true, 100)
        registry.recordOperation("sync.content.upload", -50, false, -200)

        val operation = registry.snapshot().operations.single()
        assertEquals(10L, operation.totalDurationMs)
        assertEquals(100L, operation.totalBytes)
        assertEquals(1L, operation.successCount)
        assertEquals(1L, operation.errorCount)
    }
}
