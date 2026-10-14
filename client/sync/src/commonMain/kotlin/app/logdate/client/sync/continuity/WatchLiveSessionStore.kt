package app.logdate.client.sync.continuity

import app.logdate.client.sync.datalayer.v2.LiveRecordingState
import app.logdate.client.sync.datalayer.v2.RecordingPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ephemeral state only: stale remote recordings never survive a process restart. */
class WatchLiveSessionStore(
    private val nowMs: () -> Long,
) {
    data class Session(
        val nodeId: String,
        val state: LiveRecordingState,
        val receivedAt: Long,
        val elapsedMs: Long = state.elapsedMs,
    )

    private val latest = mutableMapOf<String, Long>()
    private val mutableSession = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = mutableSession.asStateFlow()

    fun receive(
        nodeId: String,
        state: LiveRecordingState,
    ) {
        if (state.sessionId.isBlank() || state.elapsedMs < 0 || state.sequence < 0) return
        val key = "$nodeId/${state.sessionId}"
        if (state.sequence <= (latest[key] ?: -1L)) return
        latest[key] = state.sequence
        if (latest.size > 64) latest.remove(latest.keys.first())
        if (state.phase == RecordingPhase.SAVED || state.phase == RecordingPhase.DISCARDED) {
            if (mutableSession.value?.let { it.nodeId == nodeId && it.state.sessionId == state.sessionId } ==
                true
            ) {
                mutableSession.value = null
            }
            return
        }
        mutableSession.value = Session(nodeId, state, nowMs())
    }

    fun current(): Session? {
        val value = mutableSession.value ?: return null
        val age = (nowMs() - value.receivedAt).coerceAtLeast(0)
        if (age >= 45_000) {
            mutableSession.value = null
            return null
        }
        return value.copy(elapsedMs = value.state.elapsedMs + if (value.state.phase == RecordingPhase.RECORDING) age else 0)
    }

    fun clear() {
        mutableSession.value = null
    }
}
