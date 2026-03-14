package app.logdate.feature.editor.ui.editor

import androidx.compose.runtime.Stable
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.shared.model.Journal
import kotlin.uuid.Uuid

/**
 * Represents whether the editor is working on a draft or starting fresh.
 */
sealed interface DraftState {
    /** No draft is associated with the current editor session. */
    data object None : DraftState

    /** The editor is backed by a persisted draft with the given [id]. */
    data class Active(
        val id: Uuid,
    ) : DraftState
}

/**
 * Immutable state class for the editor.
 * This is the single source of truth for all editor state.
 *
 * Note: This class is marked as @Stable rather than being a data class
 * to optimize Compose recompositions. Since EditorState maintains its identity
 * while its properties change, @Stable tells Compose it can skip equality checks
 * and trust that the object's hashCode/equals implementation is consistent.
 */
@Stable
class EditorState(
    val blocks: List<EntryBlockUiState> = emptyList(),
    val expandedBlockId: Uuid? = null,
    val readOnlyBlocks: Map<Uuid, Boolean> = emptyMap(),
    val availableJournals: List<Journal> = emptyList(),
    val selectedJournalIds: List<Uuid> = emptyList(),
    val draftState: DraftState = DraftState.None,
    val availableDrafts: List<EntryDraft> = emptyList(),
    val isLoadingDrafts: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val shouldExit: Boolean = false,
    val disableEmptyBlockCreation: Boolean = false,
    val isModified: Boolean = false,
    val isSaving: Boolean = false,
) {
    /**
     * Checks if a block is read-only
     */
    fun isReadOnly(blockId: Uuid): Boolean = readOnlyBlocks[blockId] == true

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
     * Returns true when back should restore the empty content-type picker instead of
     * exiting or leaving a single empty block stranded in the editor.
     */
    fun shouldReturnToPickerOnBack(): Boolean {
        val block = blocks.singleOrNull() ?: return false
        return !block.hasContent() && block.supportsPickerReturnTransition()
    }

    /**
     * Returns true when the currently expanded block opts into immersive editor chrome.
     */
    fun isImmersiveBlockActive(): Boolean = blocks.firstOrNull { it.id == expandedBlockId }?.wantsImmersiveLayout == true

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
        draftState: DraftState = this.draftState,
        availableDrafts: List<EntryDraft> = this.availableDrafts,
        isLoadingDrafts: Boolean = this.isLoadingDrafts,
        isLoading: Boolean = this.isLoading,
        errorMessage: String? = this.errorMessage,
        shouldExit: Boolean = this.shouldExit,
        disableEmptyBlockCreation: Boolean = this.disableEmptyBlockCreation,
        isModified: Boolean = this.isModified,
        isSaving: Boolean = this.isSaving,
    ): EditorState =
        EditorState(
            blocks = blocks,
            expandedBlockId = expandedBlockId,
            readOnlyBlocks = readOnlyBlocks,
            availableJournals = availableJournals,
            selectedJournalIds = selectedJournalIds,
            draftState = draftState,
            availableDrafts = availableDrafts,
            isLoadingDrafts = isLoadingDrafts,
            isLoading = isLoading,
            errorMessage = errorMessage,
            shouldExit = shouldExit,
            disableEmptyBlockCreation = disableEmptyBlockCreation,
            isModified = isModified,
            isSaving = isSaving,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EditorState) return false

        if (blocks != other.blocks) return false
        if (expandedBlockId != other.expandedBlockId) return false
        if (readOnlyBlocks != other.readOnlyBlocks) return false
        if (availableJournals != other.availableJournals) return false
        if (selectedJournalIds != other.selectedJournalIds) return false
        if (draftState != other.draftState) return false
        if (availableDrafts != other.availableDrafts) return false
        if (isLoadingDrafts != other.isLoadingDrafts) return false
        if (isLoading != other.isLoading) return false
        if (errorMessage != other.errorMessage) return false
        if (shouldExit != other.shouldExit) return false
        if (disableEmptyBlockCreation != other.disableEmptyBlockCreation) return false
        if (isModified != other.isModified) return false
        if (isSaving != other.isSaving) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blocks.hashCode()
        result = 31 * result + (expandedBlockId?.hashCode() ?: 0)
        result = 31 * result + readOnlyBlocks.hashCode()
        result = 31 * result + availableJournals.hashCode()
        result = 31 * result + selectedJournalIds.hashCode()
        result = 31 * result + draftState.hashCode()
        result = 31 * result + availableDrafts.hashCode()
        result = 31 * result + isLoadingDrafts.hashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + shouldExit.hashCode()
        result = 31 * result + disableEmptyBlockCreation.hashCode()
        result = 31 * result + isModified.hashCode()
        result = 31 * result + isSaving.hashCode()
        return result
    }

    override fun toString(): String =
        "EditorState(blocks=${blocks.size}, expandedBlockId=$expandedBlockId, " +
            "selectedJournalIds=${selectedJournalIds.size}, draftState=$draftState, " +
            "isModified=$isModified)"
}

private fun EntryBlockUiState.supportsPickerReturnTransition(): Boolean =
    when (this) {
        is TextBlockUiState,
        is ImageBlockUiState,
        is AudioBlockUiState,
        is CameraBlockUiState,
        -> true
        is VideoBlockUiState -> false
    }
