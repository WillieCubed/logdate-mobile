package app.logdate.feature.editor.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class BlockUpdateCallbackTest {
    @Test
    fun `old callback retains its rendered base and generation after editor recomposes`() =
        runSkikoComposeUiTest {
            val original = AudioBlockUiState(captureState = AudioCaptureState.Ready("file:///audio", 1000L), caption = "Original")
            val changed = original.copy(caption = "Edited caption")
            val oldToken = Uuid.random()
            val newToken = Uuid.random()
            val state = MutableStateFlow(EditorState(blocks = listOf(original), creationInputTokens = mapOf(original.id to oldToken)))
            val delivered = mutableListOf<Triple<EntryBlockUiState, EntryBlockUiState?, Uuid?>>()
            var latest: (EntryBlockUiState) -> Unit = {}
            setContent {
                val editorState by state.collectAsState()
                latest =
                    blockUpdateCallback(editorState.blocks, editorState.creationInputTokens) { updated, base, token ->
                        delivered += Triple(updated, base, token)
                    }
            }
            lateinit var oldCallback: (EntryBlockUiState) -> Unit
            runOnIdle {
                oldCallback = latest
                state.value = state.value.copy(blocks = listOf(changed), creationInputTokens = mapOf(original.id to newToken))
            }
            waitForIdle()
            val transcriptUpdate = original.copy(transcription = "Final transcript")
            runOnIdle {
                oldCallback(transcriptUpdate)
                latest(changed.copy(transcription = "Final transcript"))
            }
            assertEquals(listOf(original, changed), delivered.map { it.second })
            assertEquals(listOf(oldToken, newToken), delivered.map { it.third })
            assertEquals(transcriptUpdate, delivered.first().first)
        }

    @Test
    fun `initial input callback keeps its absent token after a replacement emits`() =
        runSkikoComposeUiTest {
            val original = AudioBlockUiState()
            val newToken = Uuid.random()
            val state = MutableStateFlow(EditorState(blocks = listOf(original)))
            val delivered = mutableListOf<Uuid?>()
            var latest: (EntryBlockUiState) -> Unit = {}
            setContent {
                val editorState by state.collectAsState()
                latest = blockUpdateCallback(editorState.blocks, editorState.creationInputTokens) { _, _, token -> delivered += token }
            }
            lateinit var oldCallback: (EntryBlockUiState) -> Unit
            runOnIdle {
                oldCallback = latest
                state.value = state.value.copy(creationInputTokens = mapOf(original.id to newToken))
            }
            waitForIdle()
            runOnIdle {
                oldCallback(original)
                latest(original)
            }
            assertEquals(listOf(null, newToken), delivered)
        }
}
