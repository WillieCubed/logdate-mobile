package app.logdate.wear.v2.moments

import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.playback.NOTE_TIME
import app.logdate.wear.v2.playback.PlaybackFailure
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.playback.voiceNote
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MomentRowPlaybackTest {
    private val voice = voiceNote()
    private val text = JournalNote.Text(creationTimestamp = NOTE_TIME, lastUpdated = NOTE_TIME, content = "Lunch")

    @Test
    fun `only voice notes with a handler play from their row`() {
        assertNull(rowPlayback(text, PlaybackState.Idle) {})
        assertNull(rowPlayback(voice, PlaybackState.Idle, onPlayPause = null))
    }

    @Test
    fun `an idle voice note shows play and no progress`() {
        val row = rowPlayback(voice, PlaybackState.Idle) {}

        assertEquals(false, row?.isPlaying)
        assertNull(row?.fraction)
    }

    @Test
    fun `the playing note shows pause and how far it is`() {
        val row = rowPlayback(voice, PlaybackState.Active(voice.uid, positionMs = 21_000L, durationMs = 42_000L, isPaused = false)) {}

        assertEquals(true, row?.isPlaying)
        assertEquals(0.5f, row?.fraction)
    }

    @Test
    fun `a paused note shows play and where it stopped`() {
        val row = rowPlayback(voice, PlaybackState.Active(voice.uid, positionMs = 21_000L, durationMs = 42_000L, isPaused = true)) {}

        assertEquals(false, row?.isPlaying)
        assertEquals(0.5f, row?.fraction)
    }

    @Test
    fun `another note playing leaves this row idle`() {
        val row = rowPlayback(voice, PlaybackState.Active(voiceNote().uid, positionMs = 1_000L, durationMs = 5_000L, isPaused = false)) {}

        assertEquals(false, row?.isPlaying)
        assertNull(row?.fraction)
    }

    @Test
    fun `tapping play hands the row's note to the handler`() {
        val played = mutableListOf<JournalNote.Audio>()

        rowPlayback(voice, PlaybackState.Idle) { played += it }?.onPlayPause?.invoke()

        assertEquals(listOf(voice), played)
    }

    @Test
    fun `a note that failed to play shows why and stays playable`() {
        val row = rowPlayback(voice, PlaybackState.Failed(voice.uid, PlaybackFailure.NOT_ON_WATCH)) {}

        assertEquals(PlaybackFailure.NOT_ON_WATCH, row?.failure)
        assertEquals(false, row?.isPlaying)
        assertNull(row?.fraction)
    }

    @Test
    fun `another note failing leaves this row without a failure`() {
        val row = rowPlayback(voice, PlaybackState.Failed(voiceNote().uid, PlaybackFailure.NO_OUTPUT)) {}

        assertNull(row?.failure)
    }
}
