package app.logdate.client.sync.v2.continuity

import app.logdate.client.sync.continuity.HandoffFailure
import app.logdate.client.sync.continuity.PlaybackHandoffResult
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LivePlaybackCommand
import app.logdate.client.sync.datalayer.v2.PlaybackCommandType
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PhonePlaybackHandoffHandlerTest {
    private val id = Uuid.random()

    @Test
    fun `verified command carries position and speed and waits for playback acceptance`() =
        runTest {
            val handler =
                PhonePlaybackHandoffHandler({ it == "watch" }) { note, position, speed ->
                    assertEquals(id, note)
                    assertEquals(1_234L, position)
                    assertEquals(1.5f, speed)
                    null
                }
            assertTrue(result(handler).accepted)
        }

    @Test
    fun `unlinked source cannot start playback`() =
        runTest {
            val handler = PhonePlaybackHandoffHandler({ false }) { _, _, _ -> error("must not play") }
            assertEquals(HandoffFailure.PHONE_NEEDS_SETUP, result(handler).failure)
        }

    @Test
    fun `missing local audio returns meaningful failure`() =
        runTest {
            val handler = PhonePlaybackHandoffHandler({ true }) { _, _, _ -> HandoffFailure.AUDIO_NOT_ON_PHONE }
            assertFalse(result(handler).accepted)
            assertEquals(HandoffFailure.AUDIO_NOT_ON_PHONE, result(handler).failure)
        }

    @Test
    fun `a link replaced while playback starts is rejected and stops the new playback`() =
        runTest {
            var audience = "first-link"
            var stopped = false
            val handler =
                PhonePlaybackHandoffHandler(
                    accepts = { true },
                    audience = { audience },
                    stopRejectedPlayback = { stopped = true },
                    play = { _, _, _ ->
                        audience = "replacement-link"
                        null
                    },
                )
            assertEquals(HandoffFailure.PHONE_NEEDS_SETUP, result(handler).failure)
            assertTrue(stopped)
        }

    private suspend fun result(handler: PhonePlaybackHandoffHandler): PlaybackHandoffResult {
        val command =
            LivePlaybackCommand(
                sessionId = "session",
                noteId = id.toString(),
                command = PlaybackCommandType.CONTINUE_ON_PHONE,
                positionMs = 1_234,
                speed = 1.5f,
            )
        val bytes = handler.answer("watch", WearJson.encodeToBytes(LivePlaybackCommand.serializer(), command))
        return (WearJson.decode(bytes, WearProtocol.LIVE_SCHEMA, PlaybackHandoffResult.serializer()) as Decoded.Supported).value
    }
}
