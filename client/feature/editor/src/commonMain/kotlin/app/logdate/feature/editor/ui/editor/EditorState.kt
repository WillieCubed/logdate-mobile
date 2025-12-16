package app.logdate.feature.editor.ui.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.shared.model.Journal
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * Immutable state class for the editor.
 * This is the single source of truth for all editor state.
 * 
 * Note: This class is marked as @Stable rather than being a data class
 * to optimize Compose recompositions. Since EditorState maintains its identity
 * while its properties change, @Stable tells Compose it can skip equality checks
 * and trust that the object's hashCode/equals implementation is consistent.
 *
 * TODO: Consolidate with BlocksUiState to avoid duplication.
 */
@Stable
class EditorState(
    val blocks: List<EntryBlockUiState> = emptyList(),
    val expandedBlockId: Uuid? = null,
    val readOnlyBlocks: Map<Uuid, Boolean> = emptyMap(),
    val availableJournals: List<Journal> = emptyList(),
    val selectedJournalIds: List<Uuid> = emptyList(),
    val draftId: Uuid? = null,
    val isDraft: Boolean = false,
    val availableDrafts: List<EntryDraft> = emptyList(),
    val isLoadingDrafts: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val shouldExit: Boolean = false,
    val disableEmptyBlockCreation: Boolean = false,
    val isModified: Boolean = false,
) {
    /**
     * Checks if a block is read-only
     */
    fun isReadOnly(blockId: Uuid): Boolean {
        return readOnlyBlocks[blockId] == true
    }

    /**
     * Returns true if there are no blocks in this state.
     */
    fun isEmpty(): Boolean = blocks.isEmpty()

    /**
     * Returns true if any of the blocks in this state have content.
     */
    fun hasContent(): Boolean = blocks.any { it.hasContent() }

    /**
     * Returns true if the editor state has an error.
     */
    fun hasError(): Boolean = errorMessage != null
    
    /**
     * Returns true if the editor has unsaved changes.
     * Content is considered dirty if:
     * 1. It has content that hasn't been saved
     * 2. The modified flag is explicitly set to true
     */
    val isDirty: Boolean
        get() = hasContent() && isModified
        
    /**
     * Returns true if the editor can be safely exited without saving.
     * Safe to exit if:
     * 1. There's no content (empty editor)
     * 2. Content exists but has been saved (not dirty)
     */
    val canExitWithoutSaving: Boolean
        get() = !hasContent() || !isDirty
        
    /**
     * Creates a copy of the editor state with the specified properties changed.
     * This allows us to maintain the same copy-and-modify pattern as a data class.
     */
    fun copy(
        blocks: List<EntryBlockUiState> = this.blocks,
        expandedBlockId: Uuid? = this.expandedBlockId,
        readOnlyBlocks: Map<Uuid, Boolean> = this.readOnlyBlocks,
        availableJournals: List<Journal> = this.availableJournals,
        selectedJournalIds: List<Uuid> = this.selectedJournalIds,
        draftId: Uuid? = this.draftId,
        isDraft: Boolean = this.isDraft,
        availableDrafts: List<EntryDraft> = this.availableDrafts,
        isLoadingDrafts: Boolean = this.isLoadingDrafts,
        isLoading: Boolean = this.isLoading,
        errorMessage: String? = this.errorMessage,
        shouldExit: Boolean = this.shouldExit,
        disableEmptyBlockCreation: Boolean = this.disableEmptyBlockCreation,
        isModified: Boolean = this.isModified
    ): EditorState {
        return EditorState(
            blocks = blocks,
            expandedBlockId = expandedBlockId,
            readOnlyBlocks = readOnlyBlocks,
            availableJournals = availableJournals,
            selectedJournalIds = selectedJournalIds,
            draftId = draftId,
            isDraft = isDraft,
            availableDrafts = availableDrafts,
            isLoadingDrafts = isLoadingDrafts,
            isLoading = isLoading,
            errorMessage = errorMessage,
            shouldExit = shouldExit,
            disableEmptyBlockCreation = disableEmptyBlockCreation,
            isModified = isModified
        )
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EditorState) return false

        if (blocks != other.blocks) return false
        if (expandedBlockId != other.expandedBlockId) return false
        if (readOnlyBlocks != other.readOnlyBlocks) return false
        if (availableJournals != other.availableJournals) return false
        if (selectedJournalIds != other.selectedJournalIds) return false
        if (draftId != other.draftId) return false
        if (isDraft != other.isDraft) return false
        if (availableDrafts != other.availableDrafts) return false
        if (isLoadingDrafts != other.isLoadingDrafts) return false
        if (isLoading != other.isLoading) return false
        if (errorMessage != other.errorMessage) return false
        if (shouldExit != other.shouldExit) return false
        if (disableEmptyBlockCreation != other.disableEmptyBlockCreation) return false
        if (isModified != other.isModified) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blocks.hashCode()
        result = 31 * result + (expandedBlockId?.hashCode() ?: 0)
        result = 31 * result + readOnlyBlocks.hashCode()
        result = 31 * result + availableJournals.hashCode()
        result = 31 * result + selectedJournalIds.hashCode()
        result = 31 * result + (draftId?.hashCode() ?: 0)
        result = 31 * result + isDraft.hashCode()
        result = 31 * result + availableDrafts.hashCode()
        result = 31 * result + isLoadingDrafts.hashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + shouldExit.hashCode()
        result = 31 * result + disableEmptyBlockCreation.hashCode()
        result = 31 * result + isModified.hashCode()
        return result
    }
    
    override fun toString(): String {
        return "EditorState(blocks=${blocks.size}, expandedBlockId=$expandedBlockId, " +
               "selectedJournalIds=${selectedJournalIds.size}, draftId=$draftId, " +
               "isDraft=$isDraft, isModified=$isModified)"
    }
}

/**
 * UI version of EditorState that supports Compose state handling.
 * This is used in the UI layer to track mutable state that needs to be
 * synchronized with the ViewModel's EditorState.
 *
 */
@Stable
@Deprecated("Use package app.logdate.feature.editor.ui.state.EditorState instead for immutable state management")
class EditorUiState(
    initialBlocks: List<EntryBlockUiState> = emptyList(),
    initialExpandedBlockId: Uuid? = null,
    initialReadOnlyBlocks: Map<Uuid, Boolean> = emptyMap(),
    val draftId: Uuid? = null,
    val isDraft: Boolean = false,
    val disableEmptyBlockCreation: Boolean = false,
) {
    // Blocks managed by this editor
    var blocks by mutableStateOf(
        if (!disableEmptyBlockCreation && (initialBlocks.isEmpty() || initialBlocks.lastOrNull()
                ?.hasContent() == true)
        ) {
            // Only add an empty block at the end if not disabled
            initialBlocks + TextBlockUiState(location = null)
        } else {
            initialBlocks
        }
    )
        private set

    // Tracks which blocks are read-only
    var readOnlyBlocks by mutableStateOf(initialReadOnlyBlocks)
        private set

    var expandedBlockId by mutableStateOf(initialExpandedBlockId)
        private set

    var isAddingNewBlock by mutableStateOf(false)
        private set

    init {
        // Only add an empty block if not disabled and we have no blocks
        if (!disableEmptyBlockCreation && blocks.isEmpty()) {
            blocks = listOf(TextBlockUiState(location = null))
        }
    }

    /**
     * Checks if a block is read-only
     */
    fun isReadOnly(blockId: Uuid): Boolean {
        return readOnlyBlocks[blockId] == true
    }

    /**
     * Adds a block to the editor
     */
    fun addBlock(block: EntryBlockUiState, isReadOnly: Boolean = false) {
        // Add the new block to the list
        blocks = blocks + block

        // Set read-only state for the block
        if (isReadOnly) {
            readOnlyBlocks = readOnlyBlocks + (block.id to true)
        }

        // Add empty block at the end if enabled and needed
        if (!disableEmptyBlockCreation) {
            ensureEmptyBlockAtEnd()
        }
    }

    /**
     * Updates a block if it's not read-only
     */
    fun updateBlock(updatedBlock: EntryBlockUiState) {
        // Only update if the block is not read-only
        if (!isReadOnly(updatedBlock.id)) {
            // Update the block in the list
            blocks = blocks.map { if (it.id == updatedBlock.id) updatedBlock else it }

            // Add empty block at the end if enabled and needed
            if (!disableEmptyBlockCreation) {
                ensureEmptyBlockAtEnd()
            }
        }
    }

    /**
     * Removes a block if it's not read-only
     */
    fun removeBlock(blockId: Uuid) {
        // Only remove if the block is not read-only
        if (!isReadOnly(blockId)) {
            blocks = blocks.filterNot { it.id == blockId }
            if (expandedBlockId == blockId) {
                expandedBlockId = null
            }

            // Also remove from read-only map
            readOnlyBlocks = readOnlyBlocks - blockId

            // Add empty block at the end if enabled and needed
            if (!disableEmptyBlockCreation) {
                ensureEmptyBlockAtEnd()
            }
        }
    }

    /**
     * Ensures there's always an empty text block at the end of the blocks list.
     * If the last block already exists and is empty, it doesn't add another one.
     * This is only used when disableEmptyBlockCreation is false.
     */
    private fun ensureEmptyBlockAtEnd() {
        // Skip if block creation is disabled
        if (disableEmptyBlockCreation) return
        
        // Check if we need to add an empty block
        val lastBlock = blocks.lastOrNull()
        val shouldAddEmptyBlock = blocks.isEmpty() || (lastBlock != null && lastBlock.hasContent())
        
        // Only add a new block if truly needed
        if (shouldAddEmptyBlock) {
            Napier.d("Adding empty block at end of list")
            
            // Store current list size for logging
            val beforeCount = blocks.size
            
            // Create a new empty text block to add
            blocks = blocks + TextBlockUiState(location = null)
            
            // Log the change
            val afterCount = blocks.size
            Napier.d("Block count changed: $beforeCount -> $afterCount")
        }
    }

    fun expandBlock(blockId: Uuid) {
        expandedBlockId = blockId
    }

    fun collapseBlock(blockId: Uuid) {
        expandedBlockId = null
    }

    fun startAddingBlock() {
        isAddingNewBlock = true
    }

    fun finishAddingBlock() {
        isAddingNewBlock = false
    }

    /**
     * Returns true if there are no blocks in this state.
     */
    fun isEmpty() = blocks.isEmpty()

    /**
     * Returns true if any of the blocks in this state have content.
     */
    fun hasContent() = blocks.any { it.hasContent() }
    
    /**
     * Returns true if the editor has unsaved changes.
     * For UI state, we consider dirty if there's content that could be saved.
     */
    val isDirty: Boolean
        get() = hasContent()
        
    /**
     * Returns true if the editor can be safely exited without saving.
     * For UI state, safe to exit if there's no content.
     */
    val canExitWithoutSaving: Boolean
        get() = !hasContent()

    /**
     * Updates this UI state from the immutable editor state.
     * This synchronizes the UI state with the ViewModel state.
     */
    fun updateFromEditorState(editorState: EditorState) {
        // Only update if we have different content
        if (blocks != editorState.blocks) {
            blocks = editorState.blocks
        }
        if (readOnlyBlocks != editorState.readOnlyBlocks) {
            readOnlyBlocks = editorState.readOnlyBlocks
        }
        if (expandedBlockId != editorState.expandedBlockId) {
            expandedBlockId = editorState.expandedBlockId
        }
    }

    /**
     * Creates an immutable EditorState snapshot from this UI state.
     * Used to send UI state updates back to the ViewModel.
     */
    fun toEditorState(
        availableJournals: List<Journal> = emptyList(),
        selectedJournalIds: List<Uuid> = emptyList(),
        isLoading: Boolean = false,
        errorMessage: String? = null,
        shouldExit: Boolean = false,
        isModified: Boolean = hasContent(),
    ): EditorState {
        return EditorState(
            blocks = blocks,
            expandedBlockId = expandedBlockId,
            readOnlyBlocks = readOnlyBlocks,
            availableJournals = availableJournals,
            selectedJournalIds = selectedJournalIds,
            draftId = draftId,
            isDraft = isDraft,
            isLoading = isLoading,
            errorMessage = errorMessage,
            shouldExit = shouldExit,
            disableEmptyBlockCreation = disableEmptyBlockCreation,
            isModified = isModified
        )
    }
}