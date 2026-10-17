package app.logdate.feature.editor.ui.editor

import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.editor_error_recording_not_finalized
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorRecordingFocusTest {
    @Test
    fun `recording remains active when another block is selected`() {
        val text = TextBlockUiState(content = "An existing memory")
        val audio = AudioBlockUiState(captureState = AudioCaptureState.Recording())
        assertTrue(EditorState(blocks = listOf(text, audio), expandedBlockId = text.id).isRecordingAudio)
    }

    @Test
    fun `finalizing audio keeps the entry in recording focus`() {
        assertTrue(EditorState(blocks = listOf(AudioBlockUiState(captureState = AudioCaptureState.Stopping()))).isRecordingAudio)
    }

    @Test
    fun `ready failed and empty audio do not lock the recording context`() {
        listOf(
            AudioCaptureState.Empty,
            AudioCaptureState.Failed(Res.string.editor_error_recording_not_finalized),
            AudioCaptureState.Ready("file:///recording.m4a", 5000),
        ).forEach { state ->
            assertFalse(EditorState(blocks = listOf(AudioBlockUiState(captureState = state))).isRecordingAudio, state.toString())
        }
    }
}
