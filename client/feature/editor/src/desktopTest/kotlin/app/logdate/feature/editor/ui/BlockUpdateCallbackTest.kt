package app.logdate.feature.editor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BlockUpdateCallbackTest {
    @Test
    fun `old callback retains its rendered base after editor recomposes`() =
        runSkikoComposeUiTest {
            val original = AudioBlockUiState(captureState = AudioCaptureState.Ready("file:///audio", 1000L), caption = "Original")
            val changed = original.copy(caption = "Edited caption")
            val state = mutableStateOf(EditorState(blocks = listOf(original)))
            val delivered = mutableListOf<Pair<EntryBlockUiState, EntryBlockUiState?>>()
            var latest: (EntryBlockUiState) -> Unit = {}
            setContent {
                val editorState by state
                latest = blockUpdateCallback(editorState.blocks) { updated, base -> delivered += updated to base }
            }
            lateinit var oldCallback: (EntryBlockUiState) -> Unit
            runOnIdle {
                oldCallback = latest
                state.value = state.value.copy(blocks = listOf(changed))
            }
            waitForIdle()
            val transcriptUpdate = original.copy(transcription = "Final transcript")
            runOnIdle {
                oldCallback(transcriptUpdate)
                latest(changed.copy(transcription = "Final transcript"))
            }
            assertEquals(listOf(original, changed), delivered.map { it.second })
            assertEquals(transcriptUpdate, delivered.first().first)
        }
}
