package app.logdate.server.sync

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

@Serializable
data class SyncOperationMetricsSnapshot(
    val name: String,
    val successCount: Long,
    val errorCount: Long,
    val totalDurationMs: Long,
    val totalBytes: Long,
)

@Serializable
data class SyncMetricsSnapshot(
    val generatedAt: Long,
    val conflictCount: Long,
    val operations: List<SyncOperationMetricsSnapshot>,
)

class SyncMetricsRegistry {
    private val operations = ConcurrentHashMap<String, SyncOperationMetricsAccumulator>()
    private val conflictCount = LongAdder()

    fun recordOperation(
        name: String,
        durationMs: Long,
        success: Boolean,
        bytes: Long = 0L,
    ) {
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
        )
    }
}

private class SyncOperationMetricsAccumulator {
    private val successCount = LongAdder()
    private val errorCount = LongAdder()
    private val totalDurationMs = LongAdder()
    private val totalBytes = LongAdder()

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
        totalDurationMs.add(durationMs)
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
        )
}
