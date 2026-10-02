package app.logdate.client.sync.diagnostics

import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** This storage must be separate from operational queues and excluded from OS backup. */
interface DiagnosticStorage {
    suspend fun read(): String?

    suspend fun write(value: String)

    suspend fun clear()
}

@Serializable
private data class StoredDiagnostic(
    val capturedAt: Long,
    val event: SyncDiagnosticEvent,
)

@Serializable
private data class StoredHistory(
    val events: List<StoredDiagnostic> = emptyList(),
    val dropped: Int = 0,
)

class DiagnosticHistory(
    private val storage: DiagnosticStorage,
    private val nowMillis: () -> Long,
    private val maxBytes: Int = 10 * 1024 * 1024,
) {
    init {
        require(maxBytes >= 128)
    }

    private val mutex = Mutex()
    private val json = Json
    private var history: StoredHistory? = null

    suspend fun append(
        event: SyncDiagnosticEvent,
        verbose: Boolean = false,
    ) {
        mutex.withLock {
            try {
                DiagnosticReportCodec.validateEvent(event)
                val current = load()
                val retained =
                    if (!verbose &&
                        current.events
                            .lastOrNull()
                            ?.event
                            ?.canCoalesceWith(event) == true
                    ) {
                        current.events.dropLast(1) + StoredDiagnostic(nowMillis(), event)
                    } else {
                        current.events + StoredDiagnostic(nowMillis(), event)
                    }
                persistSafely(current.copy(events = retained))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                history = (history ?: StoredHistory()).let { it.copy(dropped = increment(it.dropped)) }
            }
        }
    }

    suspend fun report(): SyncDiagnosticReport =
        mutex.withLock {
            val current = load()
            val retained = current.events.filter { it.capturedAt >= nowMillis() - RETENTION_MS }
            if (retained.size != current.events.size) persistSafely(current.copy(events = retained))
            val aliases = mutableMapOf<String, String>()
            val start = retained.firstOrNull()?.capturedAt ?: nowMillis()
            var report =
                SyncDiagnosticReport(
                    reportId = Uuid.random().toString(),
                    events =
                        retained.takeLast(DiagnosticReportCodec.MAX_EVENTS).map { stored ->
                            stored.event.copy(
                                elapsedMs = (stored.capturedAt - start).coerceAtLeast(0),
                                recordAlias = stored.event.recordAlias?.let { aliases.getOrPut(it) { Uuid.random().toString() } },
                            )
                        },
                    droppedEvents =
                        (
                            current.dropped.toLong() +
                                (retained.size - DiagnosticReportCodec.MAX_EVENTS).coerceAtLeast(
                                    0,
                                )
                        ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                )
            while (runCatching { DiagnosticReportCodec.encode(report) }.isFailure && report.events.isNotEmpty()) {
                report = report.copy(events = report.events.drop(1), droppedEvents = increment(report.droppedEvents))
            }
            report
        }

    suspend fun maintain() =
        mutex.withLock {
            val current = load()
            val retained = current.events.filter { it.capturedAt >= nowMillis() - RETENTION_MS }
            if (retained.size != current.events.size) persistSafely(current.copy(events = retained))
        }

    suspend fun recordDrops(count: Int) {
        if (count <= 0) return
        mutex.withLock {
            val current = load()
            val total = (current.dropped.toLong() + count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            persistSafely(current.copy(dropped = total))
        }
    }

    suspend fun clear() {
        mutex.withLock {
            try {
                storage.clear()
                history = StoredHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw IllegalStateException("DIAGNOSTIC_HISTORY_CLEAR_FAILED")
            }
        }
    }

    private suspend fun load(): StoredHistory {
        history?.let { return it }
        return try {
            val raw = storage.read()
            require(raw == null || raw.encodeToByteArray().size <= maxBytes)
            (raw?.let { json.decodeFromString<StoredHistory>(it) } ?: StoredHistory()).also { stored ->
                require(stored.dropped >= 0)
                stored.events.forEach { DiagnosticReportCodec.validateEvent(it.event) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            StoredHistory(dropped = 1)
        }.let { stored ->
            val retained = stored.events.filter { it.capturedAt >= nowMillis() - RETENTION_MS }
            val lastByAttempt = retained.filter { it.event.attemptId != null }.associateBy { it.event.attemptId }
            val interrupted =
                lastByAttempt.values.filter { it.event.outcome == DiagnosticOutcome.STARTED }.map {
                    StoredDiagnostic(nowMillis(), it.event.copy(outcome = DiagnosticOutcome.INTERRUPTED))
                }
            val recovered = stored.copy(events = retained + interrupted)
            history = recovered
            if (retained.size != stored.events.size || interrupted.isNotEmpty()) persistSafely(recovered)
            requireNotNull(history)
        }
    }

    private fun bounded(value: StoredHistory): StoredHistory {
        val retained = value.copy(events = value.events.filter { it.capturedAt >= nowMillis() - RETENTION_MS })
        if (json.encodeToString(retained).encodeToByteArray().size <= maxBytes) return retained
        var low = 0
        var high = retained.events.size

        fun trimmed(count: Int) =
            retained.copy(
                events = retained.events.drop(count),
                dropped = (retained.dropped.toLong() + count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            )
        while (low < high) {
            val middle = (low + high) / 2
            if (json.encodeToString(trimmed(middle)).encodeToByteArray().size > maxBytes) low = middle + 1 else high = middle
        }
        return trimmed(low)
    }

    private suspend fun persistSafely(value: StoredHistory) {
        val limited = bounded(value)
        history = limited
        try {
            storage.write(json.encodeToString(limited))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            history = limited.copy(dropped = increment(limited.dropped))
        }
    }

    private fun increment(value: Int): Int = if (value == Int.MAX_VALUE) value else value + 1

    private fun SyncDiagnosticEvent.canCoalesceWith(next: SyncDiagnosticEvent): Boolean =
        (attemptId != null || operationId != null || runId != null) &&
            outcome in setOf(DiagnosticOutcome.QUEUED, DiagnosticOutcome.STARTED) &&
            phase == next.phase &&
            outcome == next.outcome &&
            runId == next.runId &&
            operationId == next.operationId &&
            attemptId == next.attemptId &&
            reason == next.reason &&
            action == next.action

    private companion object {
        const val RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
