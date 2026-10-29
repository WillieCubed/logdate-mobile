package app.logdate.ui.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** A leading pull has no navigation side effect until one armed release. */
internal class WorkspaceEntryPullState(
    val threshold: Float,
) {
    var distance by mutableFloatStateOf(0f)
        private set
    var dragging by mutableStateOf(false)
        private set
    private var committed = false
    private var active = true
    private var enabled = true
    private var sourceId: String? = null
    val armed: Boolean get() = distance >= threshold

    fun drag(
        delta: Float,
        displayedDistance: Float = distance,
    ): Float {
        if (!active || !enabled || committed || (distance == 0f && displayedDistance == 0f && delta <= 0f)) return 0f
        if (!dragging) distance = displayedDistance.coerceIn(0f, threshold * 1.4f)
        val before = distance
        distance = (distance + delta * 0.6f).coerceIn(0f, threshold * 1.4f)
        dragging = true
        return (distance - before) / 0.6f
    }

    fun release(): Boolean {
        if (!active || !enabled || committed) return false
        dragging = false
        if (!armed) {
            distance = 0f
            return false
        }
        committed = true
        return true
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        if (!value && !committed) cancel()
    }

    fun cancel() {
        distance = 0f
        dragging = false
        committed = false
    }

    fun abortGesture() {
        if (!committed) cancel()
    }

    fun reset() = cancel()

    fun bindSource(id: String) {
        if (sourceId != null && sourceId != id) reset()
        sourceId = id
    }

    fun dispose() {
        active = false
    }

    companion object {
        fun saver(threshold: Float) =
            listSaver<WorkspaceEntryPullState, Any>(
                save = { listOf(it.distance, it.committed, it.sourceId.orEmpty()) },
                restore = {
                    WorkspaceEntryPullState(threshold).also { state ->
                        state.distance = it[0] as Float
                        state.committed =
                            it[1] as Boolean
                        state.sourceId = (it[2] as String).ifEmpty { null }
                    }
                },
            )
    }
}
