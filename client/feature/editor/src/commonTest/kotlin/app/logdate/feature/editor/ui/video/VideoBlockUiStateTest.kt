package app.logdate.feature.editor.ui.video

import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for [VideoBlockUiState].
 *
 * Tests cover:
 * - Default state initialization
 * - State with video content
 * - Duration formatting
 * - Content detection for saving
 */
class VideoBlockUiStateTest {

    @Test
    fun `default state has expected values`() {
        val state = VideoBlockUiState()

        assertNotNull(state.id)
        assertNotNull(state.timestamp)
        assertNull(state.location)
        assertNull(state.uri)
        assertEquals("", state.caption)
        assertEquals(0L, state.durationMs)
    }

    @Test
    fun `state with video content has correct values`() {
        val state = VideoBlockUiState(
            uri = "content://media/video.mp4",
            caption = "My vacation video",
            durationMs = 120000L
        )

        assertEquals("content://media/video.mp4", state.uri)
        assertEquals("My vacation video", state.caption)
        assertEquals(120000L, state.durationMs)
    }

    @Test
    fun `hasContent returns false when uri is null`() {
        val state = VideoBlockUiState(uri = null)

        assertFalse(state.hasContent())
    }

    @Test
    fun `hasContent returns true when uri is present`() {
        val state = VideoBlockUiState(uri = "content://media/video.mp4")

        assertTrue(state.hasContent())
    }

    @Test
    fun `copy preserves id when not explicitly changed`() {
        val originalId = Uuid.random()
        val state = VideoBlockUiState(id = originalId)
        val copied = state.copy(caption = "Updated caption")

        assertEquals(originalId, copied.id)
        assertEquals("Updated caption", copied.caption)
    }

    @Test
    fun `copy allows updating duration`() {
        val state = VideoBlockUiState(durationMs = 5000L)
        val copied = state.copy(durationMs = 10000L)

        assertEquals(10000L, copied.durationMs)
    }

    @Test
    fun `formattedDuration returns correct format for seconds only`() {
        val state = VideoBlockUiState(durationMs = 45000L)
        assertEquals("00:45", state.formattedDuration)
    }

    @Test
    fun `formattedDuration returns correct format for minutes and seconds`() {
        val state = VideoBlockUiState(durationMs = 125000L)
        assertEquals("02:05", state.formattedDuration)
    }

    @Test
    fun `formattedDuration returns correct format for zero duration`() {
        val state = VideoBlockUiState(durationMs = 0L)
        assertEquals("00:00", state.formattedDuration)
    }

    @Test
    fun `formattedDuration returns correct format for long videos`() {
        val state = VideoBlockUiState(durationMs = 3661000L) // 1 hour, 1 minute, 1 second
        assertEquals("61:01", state.formattedDuration)
    }

    @Test
    fun `two states with identical properties are equal`() {
        val id = Uuid.random()
        val timestamp = Clock.System.now()
        val state1 = VideoBlockUiState(id = id, timestamp = timestamp, caption = "Video 1")
        val state2 = VideoBlockUiState(id = id, timestamp = timestamp, caption = "Video 1")

        assertEquals(state1, state2)
    }

    @Test
    fun `two states with different ids are not equal`() {
        val timestamp = Clock.System.now()
        val state1 = VideoBlockUiState(timestamp = timestamp, caption = "Video 1")
        val state2 = VideoBlockUiState(timestamp = timestamp, caption = "Video 1")

        // Different auto-generated IDs
        assertFalse(state1 == state2)
    }

    @Test
    fun `video with empty uri and non-empty caption still has no content`() {
        val state = VideoBlockUiState(
            uri = null,
            caption = "This is a description without a video"
        )

        assertFalse(state.hasContent())
    }
}
