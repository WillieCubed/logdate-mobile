package app.logdate.wear.v2.playback

import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class MomentPlaybackEngineTest {
    @Test
    fun `a system pause is preserved independently of actual playback`() {
        val noteId = Uuid.random()
        val status = AudioPlaybackStatus(metadata = AudioPlaybackMetadata(noteId = noteId), playWhenReady = false)
        assertEquals(false, status.toEngineStatus().playWhenReady)
    }

    @Test
    fun `buffering with play requested is not paused`() {
        val status = AudioPlaybackStatus(isPlaying = false, playWhenReady = true)
        assertEquals(true, status.toEngineStatus().playWhenReady)
        assertEquals(false, status.toEngineStatus().isPlaying)
    }

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
            EngineStatus(noteId, positionMs = 5_000L, durationMs = 42_000L, suppressedForUnsuitableOutput = true, isPlaying = true),
            status.toEngineStatus(),
        )
    }

    @Test
    fun `an idle manager names no note`() {
        assertEquals(EngineStatus.Idle, AudioPlaybackStatus().toEngineStatus())
    }
}
