package app.logdate.feature.editor.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.shared.model.Journal
import kotlin.uuid.Uuid

/**
 * Composite state holder that bridges EditorState (ViewModel) to composables.
 * Holds the block list, journal selection data, and all editor callbacks in one place.
 */
@Stable
class BlocksUiState(
    val blocks: List<EntryBlockUiState> = listOf(),
    val expandedBlockId: Uuid? = null,
    val availableJournals: List<Journal>,
    val selectedJournalIds: List<Uuid>,
    val onBlockFocused: (Uuid) -> Unit,
    val onJournalSelectionChanged: (List<Uuid>) -> Unit,
    val onUpdateBlock: (EntryBlockUiState) -> Unit,
    val onCreateBlock: (BlockType, Uuid) -> EntryBlockUiState,
    val onDeleteBlock: (Uuid) -> Unit,
) {
    val hasContent get() = blocks.any { it.hasContent() }
}

@Composable
fun rememberBlocksUiState(
    editorState: EditorState,
    onUpdateBlock: (EntryBlockUiState) -> Unit,
    onFocusBlock: (Uuid) -> Unit,
    onCreateBlock: (BlockType, Uuid) -> EntryBlockUiState,
    onDeleteBlock: (Uuid) -> Unit,
    onUpdateJournalSelection: (List<Uuid>) -> Unit,
): BlocksUiState {
    val hasContent by remember(editorState.blocks) {
        derivedStateOf { editorState.blocks.isNotEmpty() }
    }

    return remember(editorState, hasContent) {
        BlocksUiState(
            blocks = editorState.blocks,
            expandedBlockId = editorState.expandedBlockId,
            availableJournals = editorState.availableJournals,
            selectedJournalIds = editorState.selectedJournalIds,
            onBlockFocused = onFocusBlock,
            onJournalSelectionChanged = onUpdateJournalSelection,
            onUpdateBlock = onUpdateBlock,
            onCreateBlock = onCreateBlock,
            onDeleteBlock = onDeleteBlock,
        )
    }
}
