package app.logdate.feature.editor.ui.editor.mediator

import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

/**
 * Mediator interface that handles communication between different components
 * of the editor system. This promotes loose coupling between components.
 */
interface EditorMediator {
    /** Current state of the editor actions */
    val editorActions: StateFlow<EditorActions>
    
    /** Notifies that a block was selected */
    fun onBlockSelected(blockId: Uuid)
    
    /** Notifies that a new block type was requested */
    fun onNewBlockRequested(blockType: BlockType)
    
    /** Notifies that block content was updated */
    fun onBlockUpdated(block: EntryBlockUiState)
    
    /** Notifies that a save was requested */
    fun onSaveRequested()
    
    /** Notifies that a draft should be loaded */
    fun onLoadDraftRequested(draftId: Uuid)
}

/**
 * Implementation of the EditorMediator interface that maintains its own state
 * and facilitates communication between components.
 *
 * TODO: Figure out how to get rid of this
 */
class EditorMediatorImpl : EditorMediator {
    private val _editorActions = MutableStateFlow(EditorActions())
    override val editorActions = _editorActions.asStateFlow()
    
    override fun onBlockSelected(blockId: Uuid) {
        _editorActions.update { it.copy(selectedBlockId = blockId) }
    }
    
    override fun onNewBlockRequested(blockType: BlockType) {
        _editorActions.update { 
            it.copy(
                newBlockRequest = NewBlockRequest(blockType),
                // Reset any previous errors
                error = null
            )
        }
    }
    
    override fun onBlockUpdated(block: EntryBlockUiState) {
        _editorActions.update { 
            it.copy(
                updatedBlock = block,
                // Reset any previous errors
                error = null
            )
        }
    }
    
    override fun onSaveRequested() {
        _editorActions.update { it.copy(saveRequested = true) }
    }
    
    override fun onLoadDraftRequested(draftId: Uuid) {
        _editorActions.update { 
            it.copy(
                draftToLoad = draftId,
                // Reset any previous errors
                error = null
            )
        }
    }
    
    /**
     * Call this after handling an action to reset its state,
     * preventing multiple reactions to the same action.
     */
    fun resetAction(action: EditorActionType) {
        _editorActions.update {
            when (action) {
                EditorActionType.BLOCK_SELECTED -> it.copy(selectedBlockId = null)
                EditorActionType.NEW_BLOCK -> it.copy(newBlockRequest = null)
                EditorActionType.BLOCK_UPDATED -> it.copy(updatedBlock = null)
                EditorActionType.SAVE -> it.copy(saveRequested = false)
                EditorActionType.LOAD_DRAFT -> it.copy(draftToLoad = null)
            }
        }
    }
    
    /**
     * Report an error that occurred during processing.
     */
    fun reportError(message: String) {
        _editorActions.update { it.copy(error = message) }
    }
}

/**
 * Represents the current state of editor actions.
 */
data class EditorActions(
    val selectedBlockId: Uuid? = null,
    val newBlockRequest: NewBlockRequest? = null,
    val updatedBlock: EntryBlockUiState? = null,
    val saveRequested: Boolean = false,
    val draftToLoad: Uuid? = null,
    val error: String? = null
)

/**
 * Request to create a new block of a specific type.
 */
data class NewBlockRequest(
    val blockType: BlockType
)

/**
 * Types of actions that can be performed in the editor.
 */
enum class EditorActionType {
    BLOCK_SELECTED,
    NEW_BLOCK,
    BLOCK_UPDATED,
    SAVE,
    LOAD_DRAFT
}