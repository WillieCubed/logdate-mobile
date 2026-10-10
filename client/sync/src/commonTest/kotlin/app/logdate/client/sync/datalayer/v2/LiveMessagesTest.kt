package app.logdate.client.sync.datalayer.v2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the live recording and playback messages and the gate that drops late ones.
 */
class LiveMessagesTest {
    @Test
    fun `playback state reads back`() {
        val state =
            LivePlaybackState(
                sessionId = "session",
                noteId = "550e8400-e29b-41d4-a716-446655440000",
                sequence = 3,
                phase = PlaybackPhase.PLAYING,
                positionMs = 1_200,
                speed = 1.5f,
                sentAt = 10,
            )
        val bytes = WearJson.encodeToBytes(LivePlaybackState.serializer(), state)

        assertEquals(Decoded.Supported(state), WearJson.decode(bytes, WearProtocol.LIVE_SCHEMA, LivePlaybackState.serializer()))
    }

    @Test
    fun `a recording command reads from its wire form`() {
        val wire = """{"schema":1,"sessionId":"session","command":"STOP"}"""

        assertEquals(
            Decoded.Supported(LiveRecordingCommand(sessionId = "session", command = RecordingCommandType.STOP)),
            WearJson.decode(wire, WearProtocol.LIVE_SCHEMA, LiveRecordingCommand.serializer()),
        )
    }

    @Test
    fun `states arriving out of order are dropped`() {
        val gate = LiveSequenceGate()

        assertTrue(gate.admit("session", 1))
        assertTrue(gate.admit("session", 3))
        assertFalse(gate.admit("session", 2))
        assertFalse(gate.admit("session", 3))
        assertTrue(gate.admit("session", 4))
    }

    @Test
    fun `each session has its own order`() {
        val gate = LiveSequenceGate()
        gate.admit("first", 5)

        assertTrue(gate.admit("second", 1))
    }

    @Test
    fun `an ended session starts its order again`() {
        val gate = LiveSequenceGate()
        gate.admit("session", 5)

        gate.end("session")

        assertTrue(gate.admit("session", 1))
    }
}
