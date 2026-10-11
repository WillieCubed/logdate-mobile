package app.logdate.wear.v2.playback

import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class MomentPlaybackEngineTest {
    @Test
    fun `the manager's status names the note and its position`() {
        val noteId = Uuid.random()
        val status =
            AudioPlaybackStatus(
                isPlaying = true,
                duration = 42.seconds,
                metadata = AudioPlaybackMetadata(noteId = noteId),
                isSuppressedForUnsuitableOutput = true,
                positionMs = 5_000L,
            )

        assertEquals(
            EngineStatus(noteId, positionMs = 5_000L, durationMs = 42_000L, suppressedForUnsuitableOutput = true),
            status.toEngineStatus(),
        )
    }

    @Test
    fun `an idle manager names no note`() {
        assertEquals(EngineStatus.Idle, AudioPlaybackStatus().toEngineStatus())
    }
}
