package app.logdate.server.auth

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

@Serializable
data class AuthOperationMetricsSnapshot(
    val name: String,
    val successCount: Long,
    val errorCount: Long,
    val totalDurationMs: Long,
)

@Serializable
data class AuthMetricsSnapshot(
    val generatedAt: Long,
    val errorsByCode: Map<String, Long>,
    val rateLimitedByOperation: Map<String, Long>,
    val operations: List<AuthOperationMetricsSnapshot>,
)

class AuthMetricsRegistry {
    private val operations = ConcurrentHashMap<String, AuthOperationMetricsAccumulator>()
    private val errorsByCode = ConcurrentHashMap<String, LongAdder>()
    private val rateLimitedByOperation = ConcurrentHashMap<String, LongAdder>()

    fun recordOperation(
        name: String,
        durationMs: Long,
        success: Boolean,
    ) {
        val accumulator = operations.computeIfAbsent(name) { AuthOperationMetricsAccumulator() }
        accumulator.record(durationMs, success)
    }

    fun recordError(code: String) {
        errorsByCode.computeIfAbsent(code) { LongAdder() }.increment()
    }

    fun recordRateLimit(operation: String) {
        rateLimitedByOperation.computeIfAbsent(operation) { LongAdder() }.increment()
    }

    fun snapshot(): AuthMetricsSnapshot {
        val operationSnapshots =
            operations.entries
                .sortedBy { it.key }
                .map { (name, accumulator) -> accumulator.snapshot(name) }

        return AuthMetricsSnapshot(
            generatedAt = System.currentTimeMillis(),
            errorsByCode = errorsByCode.entries.associate { it.key to it.value.sum() }.toSortedMap(),
            rateLimitedByOperation = rateLimitedByOperation.entries.associate { it.key to it.value.sum() }.toSortedMap(),
            operations = operationSnapshots,
        )
    }
}

private class AuthOperationMetricsAccumulator {
    private val successCount = LongAdder()
    private val errorCount = LongAdder()
    private val totalDurationMs = LongAdder()

    fun record(
        durationMs: Long,
        success: Boolean,
    ) {
        if (success) {
            successCount.increment()
        } else {
            errorCount.increment()
        }
        totalDurationMs.add(durationMs)
    }

    fun snapshot(name: String): AuthOperationMetricsSnapshot =
        AuthOperationMetricsSnapshot(
            name = name,
            successCount = successCount.sum(),
            errorCount = errorCount.sum(),
            totalDurationMs = totalDurationMs.sum(),
        )
}
