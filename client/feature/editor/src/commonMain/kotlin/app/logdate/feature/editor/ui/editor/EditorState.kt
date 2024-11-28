package app.logdate.feature.editor.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.uuid.Uuid

// Editor state management
@Stable
class EditorState(
    initialBlocks: List<EntryBlockData> = emptyList(),
    initialExpandedBlockId: Uuid? = null
) {
    var blocks by mutableStateOf(initialBlocks)
        private set

    var expandedBlockId by mutableStateOf(initialExpandedBlockId)
        private set

    var isAddingNewBlock by mutableStateOf(false)
        private set

    fun addBlock(block: EntryBlockData) {
        blocks = blocks + block
    }

    fun updateBlock(updatedBlock: EntryBlockData) {
        blocks = blocks.map { if (it.id == updatedBlock.id) updatedBlock else it }
    }

    fun removeBlock(blockId: Uuid) {
        blocks = blocks.filterNot { it.id == blockId }
        if (expandedBlockId == blockId) {
            expandedBlockId = null
        }
    }

    fun expandBlock(blockId: Uuid) {
        expandedBlockId = blockId
    }

    fun startAddingBlock() {
        isAddingNewBlock = true
    }

    fun finishAddingBlock() {
        isAddingNewBlock = false
    }
}

@Composable
fun rememberEditorState(
    initialBlocks: List<EntryBlockData> = emptyList(),
    initialExpandedBlockId: Uuid? = null,
): EditorState {
    return remember {
        EditorState(
            initialBlocks = initialBlocks,
            initialExpandedBlockId = initialExpandedBlockId
        )
    }
}

