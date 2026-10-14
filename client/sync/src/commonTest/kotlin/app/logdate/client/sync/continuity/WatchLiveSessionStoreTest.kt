package app.logdate.client.sync.continuity

import app.logdate.client.sync.datalayer.v2.LiveRecordingState
import app.logdate.client.sync.datalayer.v2.RecordingPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchLiveSessionStoreTest {
    @Test
    fun `elapsed advances locally and expires after forty five seconds`() {
        var now = 100L
        val store = WatchLiveSessionStore { now }
        store.receive("watch", state(1, 3_000))
        now += 2_000
        assertEquals(5_000L, store.current()?.elapsedMs)
        now += 43_000
        assertNull(store.current())
    }

    @Test
    fun `paused time does not advance and late state cannot rewind`() {
        var now = 100L
        val store = WatchLiveSessionStore { now }
        store.receive("watch", state(2, 3_000, RecordingPhase.PAUSED))
        store.receive("watch", state(1, 1_000))
        now += 2_000
        assertEquals(3_000L, store.current()?.elapsedMs)
        store.receive("watch", state(3, 3_000, RecordingPhase.SAVED))
        assertNull(store.current())
        store.receive("watch", state(2, 3_000))
        assertNull(store.current())
    }

    private fun state(
        sequence: Long,
        elapsed: Long,
        phase: RecordingPhase = RecordingPhase.RECORDING,
    ) = LiveRecordingState(sessionId = "session", sequence = sequence, phase = phase, elapsedMs = elapsed, sentAt = 1)
}
