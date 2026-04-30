package app.logdate.feature.editor.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.editor.ObserveEditorDataUseCase
import app.logdate.client.domain.editor.SaveEntryUseCase
import app.logdate.feature.editor.ui.editor.delegate.ContentLoader
import app.logdate.feature.editor.ui.editor.delegate.DraftManager
import app.logdate.feature.editor.ui.mapper.toDomainBlock
import app.logdate.feature.editor.ui.mapper.toJournalNote
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * ViewModel for the note editor screen.
 *
 * This attempts to load the entry for the current date. If entries exist, it will add them to the
 * state for editing. If no entries exist, it will create a new entry.
 */
class EntryEditorViewModel(
    observeEditorData: ObserveEditorDataUseCase,
    private val saveEntryUseCase: SaveEntryUseCase,
    private val draftManager: DraftManager,
    private val contentLoader: ContentLoader,
) : ViewModel() {
    // Internal mutable state that can be modified by UI
    private val mutableEditorState =
        MutableStateFlow(
            EditorState(
                isLoading = true,
                disableEmptyBlockCreation = false,
            ),
        )

    // Track the auto-save job so it can be cancelled before a manual save
    private var autoSaveJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val deleted = draftManager.cleanupExpired()
                if (deleted > 0) {
                    Napier.d("Cleaned up $deleted expired draft(s)")
                }
            } catch (e: Exception) {
                Napier.e("Failed to clean up expired drafts: ${e.message}", e)
            }
        }

        viewModelScope.launch {
            val defaults = contentLoader.loadDefaultJournals()
            if (defaults.isNotEmpty()) {
                mutableEditorState.update { state ->
                    if (state.selectedJournalIds.isEmpty()) {
                        state.copy(selectedJournalIds = defaults)
                    } else {
                        state
                    }
                }
            }
        }
    }

    // Combine mutable state with external data into a single editor state
    val editorState: StateFlow<EditorState> =
        combine(
            mutableEditorState,
            observeEditorData(),
        ) { currentState, data ->
            val todayBlockIds =
                data.todayNotes
                    .map { it.toDomainBlock().id }
                    .toSet()

            val blocks = currentState.blocks
            val readOnlyMap = blocks.associate { it.id to (it.id in todayBlockIds) }

            val selectedJournalIds =
                if (currentState.selectedJournalIds.isEmpty() && data.journals.isNotEmpty()) {
                    listOf(data.journals.first().id)
                } else {
                    currentState.selectedJournalIds
                }

            currentState.copy(
                blocks = blocks,
                readOnlyBlocks = readOnlyMap,
                availableJournals = data.journals,
                selectedJournalIds = selectedJournalIds,
                availableDrafts = data.allDrafts,
                isLoadingDrafts = false,
                isLoading = false,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            EditorState(
                isLoading = true,
                disableEmptyBlockCreation = false,
            ),
        )

    /**
     * Sets the journals that this entry is associated with.
     */
    fun setSelectedJournals(journalIds: List<Uuid>) {
        mutableEditorState.update { it.copy(selectedJournalIds = journalIds) }
    }

    /**
     * Adds a new block to the editor.
     */
    fun createNewBlock(
        type: BlockType,
        id: Uuid = Uuid.random(),
    ): EntryBlockUiState {
        val location = null
        val timestamp = Clock.System.now()

        val newBlock =
            when (type) {
                BlockType.TEXT ->
                    TextBlockUiState(
                        id = id,
                        timestamp = timestamp,
                        location = location,
                    )

                BlockType.IMAGE ->
                    ImageBlockUiState(
                        id = id,
                        timestamp = timestamp,
                        location = location,
                    )

                BlockType.VIDEO ->
                    VideoBlockUiState(
                        id = id,
                        timestamp = timestamp,
                        location = location,
                    )

                BlockType.AUDIO ->
                    AudioBlockUiState(
                        id = id,
                        timestamp = timestamp,
                        location = location,
                    )

                BlockType.CAMERA ->
                    CameraBlockUiState(
                        id = id,
                        timestamp = timestamp,
                        location = location,
                    )
            }

        mutableEditorState.update { currentState ->
            currentState.copy(
                blocks = currentState.blocks + newBlock,
                expandedBlockId = if (type != BlockType.TEXT) newBlock.id else currentState.expandedBlockId,
                isModified = true,
            )
        }
        return newBlock
    }

    /**
     * Updates an existing block in the editor.
     */
    fun updateBlock(updatedBlock: EntryBlockUiState) {
        mutableEditorState.update { currentState ->
            if (currentState.isReadOnly(updatedBlock.id)) {
                currentState
            } else {
                val existingBlock = currentState.blocks.find { it.id == updatedBlock.id }
                val hasContentChanged = existingBlock != updatedBlock

                currentState.copy(
                    blocks =
                        currentState.blocks.map {
                            if (it.id == updatedBlock.id) updatedBlock else it
                        },
                    isModified = hasContentChanged || currentState.isModified,
                )
            }
        }
    }

    /**
     * Appends a new text block populated with [text] to the current entry.
     *
     * Unlike [setInitialTextContent], this works regardless of whether the editor
     * already has content, making it suitable for drag-and-drop text drops.
     */
    fun appendTextBlock(text: String) {
        if (text.isBlank()) return
        val newBlock = TextBlockUiState(content = text)
        mutableEditorState.update { state ->
            state.copy(
                blocks = state.blocks + newBlock,
                isModified = true,
            )
        }
    }

    /**
     * Removes a block from the entry.
     * Also clears the expanded block ID if the deleted block was currently expanded.
     */
    fun removeBlock(blockId: Uuid) {
        mutableEditorState.update { currentState ->
            val shouldClearExpanded = currentState.expandedBlockId == blockId
            val filteredBlocks = currentState.blocks.filterNot { it.id == blockId }

            currentState.copy(
                blocks = filteredBlocks,
                expandedBlockId = if (shouldClearExpanded) null else currentState.expandedBlockId,
                isModified = true,
            )
        }
    }

    /**
     * Autosaves the current entry state as a draft.
     */
    fun autoSaveEntry(state: EditorState) {
        val currentState = mutableEditorState.value
        if (state.isSaving || state.shouldExit || currentState.isSaving || currentState.shouldExit) return

        autoSaveJob =
            viewModelScope.launch {
                try {
                    draftManager.autoSave(state)?.let { draftId ->
                        mutableEditorState.update {
                            it.copy(draftState = DraftState.Active(draftId))
                        }
                    }
                } catch (e: Exception) {
                    Napier.e("Failed to auto-save draft: ${e.message}", e)
                    throw e
                }
            }
    }

    /**
     * Cancels any in-flight auto-save to prevent race conditions with manual save.
     */
    private fun cancelPendingAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    /**
     * Saves the current entry.
     */
    fun saveEntry(state: EditorState) {
        cancelPendingAutoSave()
        mutableEditorState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val latestState = editorState.value
            val notes =
                latestState.blocks.mapNotNull { block ->
                    if (latestState.isReadOnly(block.id)) return@mapNotNull null
                    block.toJournalNote()
                }

            if (notes.isEmpty()) {
                mutableEditorState.update { it.copy(shouldExit = true, isSaving = false) }
                return@launch
            }

            try {
                val activeDraftId = (latestState.draftState as? DraftState.Active)?.id
                saveEntryUseCase(notes, latestState.selectedJournalIds, activeDraftId)
                mutableEditorState.update {
                    it.copy(
                        draftState = DraftState.None,
                        isModified = false,
                        errorMessage = null,
                        shouldExit = true,
                        isSaving = false,
                    )
                }
            } catch (e: Exception) {
                Napier.e("Failed to save entry: ${e.message}", e)
                mutableEditorState.update {
                    it.copy(errorMessage = "Failed to save: ${e.message}", isSaving = false)
                }
            }
        }
    }

    /**
     * Sets the expanded block ID.
     */
    fun setExpandedBlockId(blockId: Uuid?) {
        mutableEditorState.update { it.copy(expandedBlockId = blockId) }
    }

    /**
     * Removes the lone empty block so the editor can return to the initial content-type picker.
     *
     * @return true if the editor was reset to the empty picker, false otherwise
     */
    fun clearSingleEmptyBlock(): Boolean {
        val currentState = mutableEditorState.value
        if (!currentState.shouldReturnToPickerOnBack()) {
            return false
        }
        mutableEditorState.update {
            it.copy(
                blocks = emptyList(),
                expandedBlockId = null,
                isModified = false,
            )
        }
        return true
    }

    /**
     * Dismisses the currently expanded block by collapsing it.
     * This is typically called when the user presses back while a block is focused.
     *
     * @return true if a block was dismissed, false if no block was expanded
     */
    fun dismissExpandedBlock(): Boolean {
        val currentExpandedId = mutableEditorState.value.expandedBlockId
        return if (currentExpandedId != null) {
            setExpandedBlockId(null)
            true
        } else {
            false
        }
    }

    /**
     * Handles back from an expanded block, preferring to restore the empty picker when
     * the editor only contains a single untouched block.
     */
    fun dismissExpandedBlockOrClearSingleEmpty(): Boolean {
        if (clearSingleEmptyBlock()) {
            return true
        }

        return dismissExpandedBlock()
    }

    /**
     * Loads a draft into the editor.
     */
    fun loadDraft(draftId: Uuid) {
        viewModelScope.launch {
            draftManager.loadDraft(draftId).fold(
                onSuccess = { loaded ->
                    mutableEditorState.update { currentState ->
                        currentState.copy(
                            blocks = loaded.blocks,
                            draftState = DraftState.Active(loaded.draftId),
                            isModified = true,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { e ->
                    Napier.e("Failed to load draft: ${e.message}", e)
                    mutableEditorState.update {
                        it.copy(errorMessage = "Failed to load draft: ${e.message}")
                    }
                },
            )
        }
    }

    /**
     * Deletes a draft.
     */
    fun deleteDraft(draftId: Uuid) {
        viewModelScope.launch {
            draftManager.deleteDraft(draftId).fold(
                onSuccess = {
                    mutableEditorState.update {
                        val newDraftState =
                            when (val current = it.draftState) {
                                is DraftState.Active -> if (current.id == draftId) DraftState.None else current
                                DraftState.None -> DraftState.None
                            }
                        it.copy(draftState = newDraftState)
                    }
                },
                onFailure = { e ->
                    Napier.e("Failed to delete draft: ${e.message}", e)
                    mutableEditorState.update {
                        it.copy(errorMessage = "Failed to delete draft: ${e.message}")
                    }
                },
            )
        }
    }

    /**
     * Deletes all drafts atomically.
     */
    fun deleteAllDrafts() {
        viewModelScope.launch {
            draftManager.deleteAllDrafts().fold(
                onSuccess = {
                    mutableEditorState.update { it.copy(draftState = DraftState.None) }
                },
                onFailure = { e ->
                    Napier.e("Failed to delete all drafts: ${e.message}", e)
                    mutableEditorState.update {
                        it.copy(errorMessage = "Failed to delete drafts: ${e.message}")
                    }
                },
            )
        }
    }

    /**
     * Sets the initial text content for a new note.
     * This creates a text block with the given content if there are no blocks yet.
     *
     * @param content The initial text content.
     */
    fun setInitialTextContent(content: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            try {
                val currentState = mutableEditorState.value

                if (currentState.blocks.isEmpty()) {
                    val textBlock = createNewBlock(BlockType.TEXT) as TextBlockUiState
                    updateBlock(textBlock.copy(content = content))
                }
            } catch (e: Exception) {
                Napier.e("Failed to set initial text content: ${e.message}", e)
            }
        }
    }

    /**
     * Sets initial attachments for the note.
     * This creates blocks for each attachment URI in the list.
     *
     * @param attachmentUris List of URI strings pointing to attachments.
     */
    fun setInitialAttachments(attachmentUris: List<String>) {
        if (attachmentUris.isEmpty()) return

        viewModelScope.launch {
            try {
                attachmentUris.forEach { uri ->
                    val blockType =
                        when {
                            uri.contains(".jpg", ignoreCase = true) ||
                                uri.contains(".jpeg", ignoreCase = true) ||
                                uri.contains(".png", ignoreCase = true) ||
                                uri.contains("image/", ignoreCase = true) -> BlockType.IMAGE

                            uri.contains(".mp4", ignoreCase = true) ||
                                uri.contains(".mov", ignoreCase = true) ||
                                uri.contains("video/", ignoreCase = true) -> BlockType.VIDEO

                            uri.contains(".mp3", ignoreCase = true) ||
                                uri.contains(".wav", ignoreCase = true) ||
                                uri.contains("audio/", ignoreCase = true) -> BlockType.AUDIO

                            else -> BlockType.IMAGE
                        }

                    when (blockType) {
                        BlockType.IMAGE -> {
                            val block = createNewBlock(BlockType.IMAGE) as ImageBlockUiState
                            updateBlock(block.copy(uri = uri))
                        }
                        BlockType.VIDEO -> {
                            val block = createNewBlock(BlockType.VIDEO) as VideoBlockUiState
                            updateBlock(block.copy(uri = uri))
                        }
                        BlockType.AUDIO -> {
                            val block = createNewBlock(BlockType.AUDIO) as AudioBlockUiState
                            updateBlock(
                                block.copy(
                                    captureState = AudioCaptureState.Ready(uri = uri, durationMs = 0L),
                                ),
                            )
                        }
                        else -> {
                            Napier.w("Unhandled attachment type for URI: $uri")
                        }
                    }
                }
            } catch (e: Exception) {
                Napier.e("Failed to add initial attachments: ${e.message}", e)
            }
        }
    }

    /**
     * Loads an existing entry for editing.
     *
     * Fetches the entry by ID and populates the editor state with its content. If provided,
     * journalId is used to set the selected journal context.
     *
     * @param entryId The unique identifier of the entry to load and display for editing
     * @param journalId Optional journal ID to set as the selected journal context when loading the entry
     */
    fun loadExistingEntry(
        entryId: Uuid,
        journalId: Uuid? = null,
    ) {
        viewModelScope.launch {
            mutableEditorState.update { it.copy(isLoading = true) }

            contentLoader.loadEntry(entryId).fold(
                onSuccess = { block ->
                    mutableEditorState.update { currentState ->
                        currentState.copy(
                            blocks = listOf(block),
                            isLoading = false,
                            isModified = false,
                            errorMessage = null,
                        )
                    }
                    if (journalId != null) {
                        setSelectedJournals(listOf(journalId))
                    }
                },
                onFailure = { e ->
                    Napier.e("Failed to load existing entry: $entryId", e)
                    mutableEditorState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load entry: ${e.message}",
                        )
                    }
                },
            )
        }
    }
}
