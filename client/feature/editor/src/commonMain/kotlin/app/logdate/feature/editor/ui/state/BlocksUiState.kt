package app.logdate.feature.editor.ui.state

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.EditorMode
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * A composite state holder that combines specialized state objects for different editor features.
 * This approach follows the separation of concerns principle by grouping related state and callbacks.
 */
@Stable
class BlocksUiState(
    val blocks: List<EntryBlockUiState> = listOf(),
    val textState: EditorTextState,
    val audioState: EditorRecorderState,
    val journalState: EditorJournalState,
    val navigationState: EditorNavigationState,
    val onUpdateBlock: (EntryBlockUiState) -> Unit,
    val onCreateBlock: (BlockType) -> EntryBlockUiState,
) {
    val hasContent get() = blocks.isNotEmpty() && blocks.any { it.hasContent() }
    val pagerState get() = navigationState.pagerState

    /**
     * Request an empty block to be created if needed.
     * This is used when the UI detects there are no blocks to edit.
     */
    fun requestEmptyBlock() {
        if (blocks.isEmpty()) {
            // Use the provided textState functionality to create a block
            Napier.d("Creating empty block upon request")
            val emptyBlock = TextBlockUiState(location = null)
            onCreateBlock(BlockType.TEXT)
        }
    }
    
    /**
     * Returns whether the given mode is currently available.
     * For now, only TEXT mode is available regardless of content state.
     */
    fun isModeAvailable(mode: EditorMode): Boolean {
        return mode == EditorMode.TEXT
    }
    
    /**
     * Returns a list of editor modes that are currently available.
     * For now, always return just TEXT mode.
     */
    val availableModes: List<EditorMode> = listOf(EditorMode.TEXT)
}

/**
 * Creates and remembers an EditorUiState instance based on the provided state and callbacks.
 * This factory function wires up all the specialized state objects using dependency injection.
 * 
 * @param pagerState The pager state for horizontal navigation
 * @param editorState The core editor state containing all editing data
 * @param onAudioRecordingStarted Callback when audio recording starts
 * @param onAudioRecordingStopped Callback when audio recording stops
 * @param onUpdateBlock Callback to update a block's content
 * @param onFocusBlock Callback when a block receives focus
 * @param onCreateBlock Callback to create a new block
 * @param onUpdateJournalSelection Callback to update selected journals
 * @return A composite EditorUiState combining all specialized states
 */
@Composable
fun rememberBlocksUiState(
    editorState: EditorState,
    pagerState: PagerState,
    onAudioRecordingStarted: () -> Unit,
    onAudioRecordingStopped: () -> Unit,
    onUpdateBlock: (EntryBlockUiState) -> Unit,
    onFocusBlock: (Uuid) -> Unit,
    onCreateBlock: (BlockType) -> EntryBlockUiState,
    onUpdateJournalSelection: (List<Uuid>) -> Unit
): BlocksUiState {
    val coroutineScope = rememberCoroutineScope()
    val hasContent by remember(editorState.blocks) {
        derivedStateOf {
            editorState.blocks.isNotEmpty()
        }
    }
    
    return remember(editorState, pagerState, hasContent) {
        // Create specialized state objects
        val textState = EditorTextState(
            onTextChanged = { block, newText ->
                // For text blocks, we need to create a copy with the updated content
                // Create a copy with the updated text
                val updatedBlock = block.copy(content = newText)

                // Log the update for debugging
                Napier.d("Text changed: ${block.id}, oldContent: '${block.content}', newContent: '${updatedBlock.content}'")

                // Update the block using the provided callback
                onUpdateBlock(updatedBlock)
            },
            onBlockFocused = onFocusBlock,
        )
        
        val audioState = EditorRecorderState(
            onAudioRecordingStarted = onAudioRecordingStarted,
            onAudioRecordingStopped = onAudioRecordingStopped,
            onAudioRecordingSaved = { /* Called after audio is successfully saved */ },
            onCreateAudioBlock = { uri ->
                // Create a new audio block
                val audioBlock = onCreateBlock(BlockType.AUDIO)
                // Update it with the URI if it's the correct type
                if (audioBlock is AudioBlockUiState) {
                    onUpdateBlock(audioBlock.copy(uri = uri))
                }
            }
        )
        
        val journalState = EditorJournalState(
            availableJournals = editorState.availableJournals,
            selectedJournalIds = editorState.selectedJournalIds,
            onJournalSelectionChanged = onUpdateJournalSelection
        )
        
        val navigationState = EditorNavigationState(
            pagerState = pagerState,
            hasContent = hasContent,
            coroutineScope = coroutineScope
        )
        
        // Combine all specialized states into the composite EditorUiState
        BlocksUiState(
            blocks = editorState.blocks,
            textState = textState,
            audioState = audioState,
            journalState = journalState,
            navigationState = navigationState,
            onCreateBlock = { blockType ->
                Napier.i("BlocksUiState: Creating block of type $blockType")
                onCreateBlock(blockType)
            },
            onUpdateBlock = {
                // Log the block update for debugging
                Napier.d("Updating block: ${it.id}")
                onUpdateBlock(it)
            }
        )
    }
}