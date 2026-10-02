package app.logdate.server.sync

import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

@Serializable
data class SyncDurationBucket(
    val upperBoundMs: Long,
    val count: Long,
)

@Serializable
data class SyncDiagnosticMetric(
    val phase: DiagnosticPhase,
    val outcome: DiagnosticOutcome,
    val reason: DiagnosticReason,
    val count: Long,
)

@Serializable
data class SyncOperationMetricsSnapshot(
    val name: String,
    val successCount: Long,
    val errorCount: Long,
    val totalDurationMs: Long,
    val totalBytes: Long,
    val durationBuckets: List<SyncDurationBucket> = emptyList(),
)

@Serializable
data class SyncMetricsSnapshot(
    val generatedAt: Long,
    val conflictCount: Long,
    val operations: List<SyncOperationMetricsSnapshot>,
    val diagnostics: List<SyncDiagnosticMetric> = emptyList(),
    val diagnosticDrops: Long = 0,
)

class SyncMetricsRegistry {
    private val operations = ConcurrentHashMap<String, SyncOperationMetricsAccumulator>()
    private val conflictCount = LongAdder()
    private val diagnostics = ConcurrentHashMap<Triple<DiagnosticPhase, DiagnosticOutcome, DiagnosticReason>, LongAdder>()
    private val diagnosticDrops = LongAdder()

    fun recordDiagnostic(event: SyncDiagnosticEvent) {
        if (runCatching { DiagnosticReportCodec.validateEvent(event) }.isFailure) {
            diagnosticDrops.increment()
            return
        }
        val key = Triple(event.phase, event.outcome, event.reason)
        diagnostics.computeIfAbsent(key) { LongAdder() }.increment()
    }

    fun recordOperation(
        name: String,
        durationMs: Long,
        success: Boolean,
        bytes: Long = 0L,
    ) {
        if (name !in KNOWN_OPERATIONS) return
        val accumulator = operations.computeIfAbsent(name) { SyncOperationMetricsAccumulator() }
        accumulator.record(durationMs, success, bytes)
    }

    fun recordConflict() {
        conflictCount.increment()
    }

    fun snapshot(): SyncMetricsSnapshot {
        val snapshots =
            operations.entries
                .sortedBy { it.key }
                .map { (name, accumulator) -> accumulator.snapshot(name) }
        return SyncMetricsSnapshot(
            generatedAt = System.currentTimeMillis(),
            conflictCount = conflictCount.sum(),
            operations = snapshots,
            diagnostics =
                diagnostics.entries
                    .map { (key, value) ->
                        SyncDiagnosticMetric(key.first, key.second, key.third, value.sum())
                    }.sortedWith(compareBy({ it.phase.ordinal }, { it.outcome.ordinal }, { it.reason.ordinal })),
            diagnosticDrops = diagnosticDrops.sum(),
        )
    }

    private companion object {
        val KNOWN_OPERATIONS =
            setOf(
                "sync.status",
                "sync.metrics",
                "sync.metrics.prometheus",
                "sync.content.upload",
                "sync.content.changes",
                "sync.content.update",
                "sync.content.delete",
                "sync.journal.upload",
                "sync.journal.changes",
                "sync.journal.update",
                "sync.journal.delete",
                "sync.association.upload",
                "sync.association.changes",
                "sync.association.delete",
                "sync.media.upload",
                "sync.media.download",
                "sync.media.delete",
                "sync.backup.upload",
                "sync.backup.list",
                "sync.backup.download",
                "sync.backup.delete",
                "sync.maintenance.purge",
                "sync.backups.purge",
                "maintenance.tombstone_purge",
            )
    }
}

private class SyncOperationMetricsAccumulator {
    private val successCount = LongAdder()
    private val errorCount = LongAdder()
    private val totalDurationMs = LongAdder()
    private val totalBytes = LongAdder()
    private val durationBuckets = DURATION_BOUNDS.map { LongAdder() }

    fun record(
        durationMs: Long,
        success: Boolean,
        bytes: Long,
    ) {
        if (success) {
            successCount.increment()
        } else {
            errorCount.increment()
        }
        val elapsed = durationMs.coerceAtLeast(0)
        totalDurationMs.add(elapsed)
        DURATION_BOUNDS.forEachIndexed { index, bound ->
            if (elapsed <= bound) durationBuckets[index].increment()
        }
        if (bytes > 0L) {
            totalBytes.add(bytes)
        }
    }

    fun snapshot(name: String): SyncOperationMetricsSnapshot =
        SyncOperationMetricsSnapshot(
            name = name,
            successCount = successCount.sum(),
            errorCount = errorCount.sum(),
            totalDurationMs = totalDurationMs.sum(),
            totalBytes = totalBytes.sum(),
            durationBuckets = DURATION_BOUNDS.mapIndexed { index, bound -> SyncDurationBucket(bound, durationBuckets[index].sum()) },
        )

    private companion object {
        val DURATION_BOUNDS = listOf(100L, 500L, 1_000L, 5_000L, 30_000L, Long.MAX_VALUE)
    }
}
