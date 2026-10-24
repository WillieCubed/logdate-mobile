package app.logdate.feature.core.restore

import kotlin.coroutines.CoroutineContext

/** Callers serialize selection transitions with the launcher's completion lock. */
internal class ArchivePickerRequestGate {
    private var generation = 0L
    private var outstanding: Long? = null
    private var queued: Long? = null

    fun invalidate() {
        generation++
        queued = null
    }

    fun beginSelection(): Long? {
        invalidate()
        val request = generation
        if (outstanding != null) {
            queued = request
            return null
        }
        outstanding = request
        return request
    }

    fun consumeResult(): ArchivePickerResult {
        val request = outstanding ?: return ArchivePickerResult.Ignored
        outstanding = null
        val next = queued
        queued = null
        if (next != null && isCurrent(next)) {
            outstanding = next
            return ArchivePickerResult.Launch(next)
        }
        return if (isCurrent(request)) ArchivePickerResult.Selected(request) else ArchivePickerResult.Ignored
    }

    fun launchFailed(request: Long) {
        if (outstanding == request) outstanding = null
    }

    fun isCurrent(request: Long): Boolean = generation == request
}

internal sealed interface ArchivePickerResult {
    data object Ignored : ArchivePickerResult

    data class Launch(
        val request: Long,
    ) : ArchivePickerResult

    data class Selected(
        val request: Long,
    ) : ArchivePickerResult
}

internal fun commitArchiveSelectionRequest(
    operationContext: CoroutineContext,
    request: Long,
    gate: ArchivePickerRequestGate,
    commit: (() -> Unit) -> Unit,
    action: () -> Unit,
) {
    commitArchiveResumeRequest(operationContext, commit) {
        if (gate.isCurrent(request)) action()
    }
}
