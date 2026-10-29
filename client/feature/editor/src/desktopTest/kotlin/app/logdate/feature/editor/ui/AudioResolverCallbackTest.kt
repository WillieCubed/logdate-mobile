package app.logdate.feature.editor.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.delegate.PendingAudioResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class AudioResolverCallbackTest {
    @Test
    fun `current audio input can bind its resolver before and after a generation is assigned`() {
        val block = AudioBlockUiState()
        val token = Uuid.random()
        var state = EditorState(blocks = listOf(block))
        val resolver = TestResolver()
        val bindings = mutableListOf<Pair<Uuid, PendingAudioResolver>>()

        audioResolverCallback(emptyMap(), { state }) { id, bound -> bindings += id to bound }(block.id, resolver)
        state = state.copy(creationInputTokens = mapOf(block.id to token))
        audioResolverCallback(state.creationInputTokens, { state }) { id, bound -> bindings += id to bound }(block.id, resolver)

        assertEquals(listOf<Pair<Uuid, PendingAudioResolver>>(block.id to resolver, block.id to resolver), bindings)
    }

    @Test
    fun `old resolver callback cannot bind after mode changes away and back through recomposition`() =
        runSkikoComposeUiTest {
            val block = AudioBlockUiState()
            val oldToken = Uuid.random()
            val newToken = Uuid.random()
            val state = MutableStateFlow(EditorState(blocks = listOf(block), creationInputTokens = mapOf(block.id to oldToken)))
            val oldResolver = TestResolver()
            val newResolver = TestResolver()
            val bindings = mutableListOf<Pair<Uuid, PendingAudioResolver>>()
            var latest: (Uuid, PendingAudioResolver) -> Unit = { _, _ -> }
            setContent {
                val editorState by state.collectAsState()
                latest =
                    audioResolverCallback(editorState.creationInputTokens, { state.value }) { id, resolver -> bindings += id to resolver }
            }
            lateinit var oldCallback: (Uuid, PendingAudioResolver) -> Unit
            runOnIdle {
                oldCallback = latest
                // Matching generation alone is insufficient once the memory is another input type.
                state.value = state.value.copy(blocks = listOf(TextBlockUiState(id = block.id)))
            }
            waitForIdle()
            runOnIdle {
                oldCallback(block.id, oldResolver)
                assertEquals(emptyList(), bindings)
                state.value = state.value.copy(blocks = listOf(block), creationInputTokens = mapOf(block.id to newToken))
            }
            waitForIdle()
            runOnIdle {
                oldCallback(block.id, oldResolver)
                assertEquals(emptyList(), bindings)
                latest(block.id, newResolver)
            }
            assertEquals(listOf<Pair<Uuid, PendingAudioResolver>>(block.id to newResolver), bindings)
        }

    @Test
    fun `removed audio memory cannot rebind even when its generation map entry remains`() {
        val block = AudioBlockUiState()
        val token = Uuid.random()
        var state = EditorState(blocks = listOf(block), creationInputTokens = mapOf(block.id to token))
        val bindings = mutableListOf<Pair<Uuid, PendingAudioResolver>>()
        val callback = audioResolverCallback(state.creationInputTokens, { state }) { id, resolver -> bindings += id to resolver }

        state = state.copy(blocks = emptyList())
        callback(block.id, TestResolver())

        assertEquals(emptyList(), bindings)
    }

    private class TestResolver : PendingAudioResolver {
        override suspend fun resolvePending(blockId: Uuid): AudioCaptureState? = null
    }
}
