package app.logdate.feature.editor.ui.content

import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests the visual transition mapping between the empty editor state and media pickers.
 *
 * These tests ensure that editor blocks (text, audio, image, etc.) correctly map to
 * their corresponding UI tile IDs to enable seamless animations when adding content.
 */
class EmptyEditorTransitionStateTest {
    @Test
    fun testMatchingPickerTileIdsReuseSupportedBlockIds() {
        val textBlock = TextBlockUiState()
        val audioBlock = AudioBlockUiState()
        val imageBlock = ImageBlockUiState()
        val cameraBlock = CameraBlockUiState()

        assertEquals(textBlock.id, matchingPickerTileIdsFor(textBlock).textId)
        assertEquals(audioBlock.id, matchingPickerTileIdsFor(audioBlock).audioId)
        assertEquals(imageBlock.id, matchingPickerTileIdsFor(imageBlock).photoId)
        assertEquals(cameraBlock.id, matchingPickerTileIdsFor(cameraBlock).cameraId)
    }

    @Test
    fun testUnsupportedBlockTypesDoNotMapToPickerTiles() {
        val videoBlock = VideoBlockUiState()
        val tileIds = matchingPickerTileIdsFor(videoBlock)

        assertNull(tileIds.textId)
        assertNull(tileIds.audioId)
        assertNull(tileIds.cameraId)
        assertNull(tileIds.photoId)
    }
}
