package app.logdate.feature.editor.ui.camera

import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for [CameraBlockUiState].
 *
 * Tests cover:
 * - Default state initialization
 * - State with captured photo
 * - State with captured video
 * - Content detection for saving
 * - Media type handling
 */
class CameraBlockUiStateTest {

    @Test
    fun `default state has expected values`() {
        val state = CameraBlockUiState()

        assertNotNull(state.id)
        assertNotNull(state.timestamp)
        assertNull(state.location)
        assertNull(state.uri)
        assertEquals("", state.caption)
        assertEquals(CapturedMediaType.PHOTO, state.mediaType)
        assertEquals(0L, state.durationMs)
    }

    @Test
    fun `state with photo has correct media type`() {
        val state = CameraBlockUiState(
            uri = "content://media/photo.jpg",
            caption = "My photo",
            mediaType = CapturedMediaType.PHOTO
        )

        assertEquals("content://media/photo.jpg", state.uri)
        assertEquals("My photo", state.caption)
        assertEquals(CapturedMediaType.PHOTO, state.mediaType)
        assertEquals(0L, state.durationMs)
    }

    @Test
    fun `state with video has correct media type and duration`() {
        val state = CameraBlockUiState(
            uri = "content://media/video.mp4",
            caption = "My video",
            mediaType = CapturedMediaType.VIDEO,
            durationMs = 15000L
        )

        assertEquals("content://media/video.mp4", state.uri)
        assertEquals("My video", state.caption)
        assertEquals(CapturedMediaType.VIDEO, state.mediaType)
        assertEquals(15000L, state.durationMs)
    }

    @Test
    fun `formattedDuration returns correct format for seconds only`() {
        val state = CameraBlockUiState(
            mediaType = CapturedMediaType.VIDEO,
            durationMs = 45000L
        )
        assertEquals("00:45", state.formattedDuration)
    }

    @Test
    fun `formattedDuration returns correct format for minutes and seconds`() {
        val state = CameraBlockUiState(
            mediaType = CapturedMediaType.VIDEO,
            durationMs = 125000L
        )
        assertEquals("02:05", state.formattedDuration)
    }

    @Test
    fun `hasContent returns false when uri is null`() {
        val state = CameraBlockUiState(uri = null)

        assertFalse(state.hasContent())
    }

    @Test
    fun `hasContent returns true when uri is present`() {
        val state = CameraBlockUiState(uri = "content://media/photo.jpg")

        assertTrue(state.hasContent())
    }

    @Test
    fun `copy preserves id when not explicitly changed`() {
        val originalId = Uuid.random()
        val state = CameraBlockUiState(id = originalId)
        val copied = state.copy(caption = "Updated caption")

        assertEquals(originalId, copied.id)
        assertEquals("Updated caption", copied.caption)
    }

    @Test
    fun `copy allows changing media type`() {
        val state = CameraBlockUiState(
            uri = "content://media/video.mp4",
            mediaType = CapturedMediaType.VIDEO
        )
        val copied = state.copy(mediaType = CapturedMediaType.PHOTO)

        assertEquals(CapturedMediaType.PHOTO, copied.mediaType)
    }

    @Test
    fun `two states with identical properties are equal`() {
        val id = Uuid.random()
        val timestamp = Clock.System.now()
        val state1 = CameraBlockUiState(id = id, timestamp = timestamp, caption = "Photo 1")
        val state2 = CameraBlockUiState(id = id, timestamp = timestamp, caption = "Photo 1")

        assertEquals(state1, state2)
    }

    @Test
    fun `two states with different ids are not equal`() {
        val timestamp = Clock.System.now()
        val state1 = CameraBlockUiState(timestamp = timestamp, caption = "Photo 1")
        val state2 = CameraBlockUiState(timestamp = timestamp, caption = "Photo 1")

        // Different auto-generated IDs
        assertFalse(state1 == state2)
    }
}
