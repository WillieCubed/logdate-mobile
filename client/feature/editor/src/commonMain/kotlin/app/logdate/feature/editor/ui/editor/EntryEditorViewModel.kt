package app.logdate.feature.editor.ui.editor

// Restore imports for the functionality we're implementing
// import app.logdate.client.domain.world.ObserveLocationUseCase (commented out)
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.journals.GetCurrentUserJournalsUseCase
import app.logdate.client.domain.journals.GetDefaultSelectedJournalsUseCase
import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.FetchTodayNotesUseCase
import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.notes.drafts.GetAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.editor.delegate.AutoSaveDelegate
import app.logdate.feature.editor.ui.editor.delegate.JournalSelectionDelegate
import app.logdate.feature.editor.ui.editor.mediator.EditorMediator
import app.logdate.feature.editor.ui.mapper.toDomainBlock
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

/**
 * ViewModel for the note editor screen.
 *
 * This attempts to load the entry for the current date. If entries exist, it will add them to the
 * state for editing. If no entries exist, it will create a new entry.
 */

class EntryEditorViewModel(
    fetchTodayNotes: FetchTodayNotesUseCase,
    getCurrentUserJournals: GetCurrentUserJournalsUseCase,
    private val getDefaultSelectedJournals: GetDefaultSelectedJournalsUseCase,
    private val addNoteUseCase: AddNoteUseCase,
    private val journalContentRepository: JournalContentRepository,
    // observeLocation: ObserveLocationUseCase, (commented out)
    private val updateEntryDraft: UpdateEntryDraftUseCase,
    private val createEntryDraft: CreateEntryDraftUseCase,
    private val deleteEntryDraft: DeleteEntryDraftUseCase,
    private val fetchEntryDraft: FetchEntryDraftUseCase,
    fetchMostRecentDraft: FetchMostRecentDraftUseCase,
    getAllDrafts: GetAllDraftsUseCase,
    // private val transcriptionService: TranscriptionService,
    
    // New dependencies for mediator pattern and delegation
    private val mediator: EditorMediator,
    private val autoSaveDelegate: AutoSaveDelegate,
    private val journalSelectionDelegate: JournalSelectionDelegate
) : ViewModel() {
    // Internal mutable state that can be modified by UI
    private val _mutableState = MutableStateFlow(
        EditorState(
            isLoading = true,
            disableEmptyBlockCreation = false,
        )
    )
    
    init {
        // Load default journals using the journal selection delegate
        journalSelectionDelegate.loadDefaultJournals(_mutableState)
        
        // Set up mediator listener for mediator events
        viewModelScope.launch {
            mediator.editorActions.collect { actions ->
                // Handle save requests from other components
                if (actions.saveRequested) {
                    saveEntry(editorState.value)
                    (mediator as? app.logdate.feature.editor.ui.editor.mediator.EditorMediatorImpl)
                        ?.resetAction(app.logdate.feature.editor.ui.editor.mediator.EditorActionType.SAVE)
                }
                
                // Handle draft load requests from other components
                actions.draftToLoad?.let { draftId ->
                    loadDraft(draftId)
                    (mediator as? app.logdate.feature.editor.ui.editor.mediator.EditorMediatorImpl)
                        ?.resetAction(app.logdate.feature.editor.ui.editor.mediator.EditorActionType.LOAD_DRAFT)
                }
            }
        }
    }

    private val todayNotesFlow = fetchTodayNotes()
        .catch { e ->
            Napier.e("Failed to load entries: ${e.message}", e)
            emit(emptyList())
        }

    private val journalsFlow = getCurrentUserJournals()
        .catch { e ->
            Napier.e("Failed to load journals: ${e.message}", e)
            emit(emptyList())
        }
        
    private val recentDraftFlow = fetchMostRecentDraft()
        .catch { e ->
            Napier.e("Failed to load recent draft: ${e.message}", e)
            emit(null)
        }
        
    private val allDraftsFlow = getAllDrafts()
        .catch { e ->
            Napier.e("Failed to load all drafts: ${e.message}", e)
            emit(emptyList())
        }
        
    // Location flow commented out
    // private val locationFlow = observeLocation()
    //    .catch { e ->
    //        Napier.e("Failed to observe location: ${e.message}", e)
    //        // Don't emit null, just don't emit anything on error
    //    }

    // Combine all data sources into a single editor state
    val editorState: StateFlow<EditorState> = combine(
        _mutableState,
        todayNotesFlow,
        journalsFlow,
        recentDraftFlow,
        allDraftsFlow
    ) { currentState, notes, journals, recentDraft, allDrafts ->
        Napier.d("EntryEditorViewModel: Rebuilding state, current blocks: ${currentState.blocks.size}")
        // Convert today's notes to UI blocks
        val todayBlocks = notes.map { it.toDomainBlock() }
        
        // Convert draft notes to UI blocks if we have a draft
        val draftBlocks = recentDraft?.notes?.map { it.toDomainBlock() } ?: emptyList()
        
        // IMPORTANT: Always start with user's current blocks and DO NOT auto-populate content
        // Start with current blocks and ONLY use them - don't auto-populate with drafts or today's notes
        val blocks = currentState.blocks
        
        // Mark today's notes as read-only by tracking their IDs
        val todayBlockIds = todayBlocks.map { it.id }.toSet()
        val readOnlyMap = blocks.associate { it.id to todayBlockIds.contains(it.id) }
        
        // Auto-select first journal if none selected and journals are available
        // This provides a better default experience for users
        val selectedJournalIds = if (currentState.selectedJournalIds.isEmpty() && journals.isNotEmpty()) {
            journals.first().id.let(::listOf)  // Select the first journal
        } else {
            currentState.selectedJournalIds  // Keep existing selection
        }
        
        // Create a new state combining mutable state and data sources
        val newState = currentState.copy(
            blocks = blocks,
            readOnlyBlocks = readOnlyMap,
            availableJournals = journals,
            selectedJournalIds = selectedJournalIds,
            draftId = null, // Don't auto-select a draft
            isDraft = false, // Not a draft by default
            availableDrafts = allDrafts, 
            isLoadingDrafts = false,
            isLoading = false
        )
        Napier.d("EntryEditorViewModel: New state created, blocks: ${newState.blocks.size}, hasContent: ${newState.hasContent()}")
        newState
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        EditorState(
            isLoading = true,
            disableEmptyBlockCreation = false
        )
    )

    /* Original implementation commented out
    // Combine internal state with external data sources into a single editor state
    // This creates a derived state that automatically updates when any of its sources change
    val editorState: StateFlow<EditorState> = combine(
        _mutableState,
        todayNotesFlow,
        journalsFlow,
        recentDraftFlow,
        allDraftsFlow
    ) { currentState, notes, journals, recentDraft, allDrafts ->
        // Convert today's notes to UI blocks
        val todayBlocks = notes.map { noteToDomainBlock(it) }
        
        // Convert draft notes to UI blocks if we have a draft (temporarily empty for debugging)
        val draftBlocks = emptyList<EntryBlockData>() // TODO: Convert recentDraft?.notes when use cases are re-enabled
        
        // Start with an empty blocks list
        var blocks = emptyList<EntryBlockData>()
        
        // If we have user-modified blocks, keep them as is
        if (currentState.blocks.isNotEmpty()) {
            blocks = currentState.blocks
        } 
        // If we have a recent draft and no user modifications, use the draft
        else if (recentDraft != null) {
            blocks = recentDraft.notes.map { noteToDomainBlock(it) }
        } 
        // Otherwise fall back to today's notes
        else if (todayBlocks.isNotEmpty()) {
            blocks = todayBlocks
        }
        
        // Mark today's notes as read-only by tracking their IDs
        val todayBlockIds = todayBlocks.map { it.id }.toSet()
        val readOnlyMap = blocks.associate { it.id to todayBlockIds.contains(it.id) }
        
        // Auto-select first journal if none selected and journals are available
        val selectedJournalIds = if (currentState.selectedJournalIds.isEmpty() && journals.isNotEmpty()) {
            listOf(journals.first().id)
        } else {
            currentState.selectedJournalIds
        }
        
        // Create a new state combining mutable state and data sources
        currentState.copy(
            blocks = blocks,
            readOnlyBlocks = readOnlyMap,
            availableJournals = journals,
            selectedJournalIds = selectedJournalIds,
            draftId = recentDraft?.id,
            isDraft = recentDraft != null,
            availableDrafts = allDrafts, // TODO: Sort by lastModifiedAt when use cases are re-enabled
            isLoadingDrafts = false,
            isLoading = false
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        EditorState(
            isLoading = true,
            disableEmptyBlockCreation = false
        )
    )
    */


    /**
     * Sets the journals that this entry is associated with.
     * Uses the journal selection delegate.
     */
    fun setSelectedJournals(journalIds: List<Uuid>) {
        journalSelectionDelegate.setSelectedJournals(journalIds, _mutableState)
    }

    /**
     * Adds a new block to the editor.
     */
    fun createNewBlock(type: BlockType): EntryBlockUiState {
        Napier.i("EntryEditorViewModel: Creating new block of type $type")
        // Create a new block based on the specified type
        // Using null for location instead of real location data
        val location = null // Location functionality disabled
        val timestamp = Clock.System.now()

        val newBlock = when (type) {
            BlockType.TEXT -> TextBlockUiState(
                timestamp = timestamp,
                location = location,
            )

            BlockType.IMAGE -> ImageBlockUiState(
                timestamp = timestamp,
                location = location,
            )

            BlockType.VIDEO -> VideoBlockUiState(
                timestamp = timestamp,
                location = location,
            )

            BlockType.AUDIO -> AudioBlockUiState(
                timestamp = timestamp,
                location = location,
            )

            BlockType.CAMERA -> CameraBlockUiState(
                timestamp = timestamp,
                location = location,
            )
        }

        // Add the new block to the state
        _mutableState.update { currentState ->
            Napier.i("EntryEditorViewModel: Adding new block to state, current blocks: ${currentState.blocks.size}")
            currentState.copy(
                blocks = currentState.blocks + newBlock,
                isModified = true
            )
        }
        Napier.i("EntryEditorViewModel: Block added, returning: ${newBlock.id}")

        return newBlock
    }

    /**
     * Updates an existing block in the editor.
     */
    fun updateBlock(updatedBlock: EntryBlockUiState) {
        Napier.d("Updating block: ${updatedBlock.id}, content length: ${(updatedBlock as? TextBlockUiState)?.content?.length}")
        
        _mutableState.update { currentState ->
            // Only update if the block is not read-only
            if (currentState.isReadOnly(updatedBlock.id)) {
                Napier.d("Block is read-only, not updating: ${updatedBlock.id}")
                currentState
            } else {
                // Find the existing block to check for actual changes
                val existingBlock = currentState.blocks.find { it.id == updatedBlock.id }
                val hasContentChanged = existingBlock != updatedBlock
                
                // Log the content change for debugging
                if (existingBlock is TextBlockUiState && updatedBlock is TextBlockUiState) {
                    Napier.d("Text changed: '${existingBlock.content}' -> '${updatedBlock.content}'")
                }
                
                // Create a modified copy with the updated block and set isDirty to true
                val updatedState = currentState.copy(
                    blocks = currentState.blocks.map {
                        if (it.id == updatedBlock.id) updatedBlock else it
                    },
                    // Only mark as modified if there was an actual content change
                    isModified = hasContentChanged || currentState.isModified
                )
                
                Napier.d("State updated, block count: ${updatedState.blocks.size}, isModified: ${updatedState.isModified}")
                updatedState
            }
        }
    }

    /**
     * Autosaves the current entry state using the AutoSaveDelegate.
     */
    fun autoSaveEntry(state: EditorState) {
        autoSaveDelegate.autoSaveEntry(state) { newDraftId ->
            // Update draft ID in state
            _mutableState.update { it.copy(draftId = newDraftId, isDraft = true) }
        }
    }

    /**
     * Saves the current entry.
     */
    fun saveEntry(state: EditorState) {
        viewModelScope.launch {
            try {
                // Convert UI blocks to domain notes
                val notes = state.blocks.mapNotNull { block ->
                    if (!block.hasContent()) return@mapNotNull null
                    
                    when (block) {
                        is TextBlockUiState -> JournalNote.Text(
                            uid = block.id,
                            creationTimestamp = block.timestamp,
                            lastUpdated = Clock.System.now(),
                            content = block.content
                        )
                        is ImageBlockUiState -> JournalNote.Image(
                            uid = block.id,
                            creationTimestamp = block.timestamp,
                            lastUpdated = Clock.System.now(),
                            mediaRef = block.uri ?: return@mapNotNull null
                        )
                        is CameraBlockUiState -> JournalNote.Image(
                            uid = block.id,
                            creationTimestamp = block.timestamp,
                            lastUpdated = Clock.System.now(),
                            mediaRef = block.uri ?: return@mapNotNull null
                        )
                        is VideoBlockUiState -> JournalNote.Video(
                            uid = block.id,
                            creationTimestamp = block.timestamp,
                            lastUpdated = Clock.System.now(),
                            mediaRef = block.uri ?: return@mapNotNull null
                        )
                        is AudioBlockUiState -> JournalNote.Audio(
                            uid = block.id,
                            creationTimestamp = block.timestamp,
                            lastUpdated = Clock.System.now(),
                            mediaRef = block.uri ?: return@mapNotNull null
                        )
                        else -> null
                    }
                }
                
                if (notes.isEmpty()) {
                    Napier.d("Skip save: no content")
                    // Signal to UI that we're done
                    _mutableState.update { it.copy(shouldExit = true) }
                    return@launch
                }
                
                // Add each note and link it to selected journals in a single operation
                val currentJournals = state.selectedJournalIds
                
                // Save all notes and associate them with journals in a single call
                addNoteUseCase(
                    notes = notes,
                    journalIds = currentJournals,
                )
                
                // If this was a draft, delete it since we've saved it permanently
                val draftId = state.draftId
                if (draftId != null) {
                    deleteEntryDraft(draftId)
                    Napier.d("Deleted draft after saving: $draftId")
                }
                
                Napier.i("Saved ${notes.size} notes to repository")
                
                // Signal to UI that we're done and should exit
                _mutableState.update { it.copy(shouldExit = true) }
                
            } catch (e: Exception) {
                Napier.e("Failed to save entry: ${e.message}", e)
                _mutableState.update { 
                    it.copy(errorMessage = "Failed to save: ${e.message}")
                }
            }
        }
    }

    /**
     * Sets the expanded block ID.
     */
    fun setExpandedBlockId(blockId: Uuid?) {
        _mutableState.update { it.copy(expandedBlockId = blockId) }
    }

    /**
     * Loads a draft into the editor.
     */
    fun loadDraft(draftId: Uuid) {
        viewModelScope.launch {
            try {
                fetchEntryDraft(draftId)
                    .catch { e ->
                        Napier.e("Failed to load draft: ${e.message}", e)
                        _mutableState.update { 
                            it.copy(errorMessage = "Failed to load draft: ${e.message}")
                        }
                    }
                    .collect { result ->
                        result.fold(
                            onSuccess = { draft ->
                                // Convert draft notes to UI blocks
                                val draftBlocks = draft.notes.map { it.toDomainBlock() }
                                
                                _mutableState.update { currentState ->
                                    currentState.copy(
                                        blocks = draftBlocks,
                                        draftId = draft.id,
                                        isDraft = true,
                                        errorMessage = null
                                    )
                                }
                                Napier.d("Loaded draft: ${draft.id} with ${draft.notes.size} notes")
                            },
                            onFailure = { e ->
                                Napier.e("Failed to load draft: ${e.message}", e)
                                _mutableState.update { 
                                    it.copy(errorMessage = "Failed to load draft: ${e.message}")
                                }
                            }
                        )
                    }
            } catch (e: Exception) {
                Napier.e("Failed to load draft: ${e.message}", e)
                _mutableState.update { 
                    it.copy(errorMessage = "Failed to load draft: ${e.message}")
                }
            }
        }
    }

    /**
     * Deletes a draft.
     */
    fun deleteDraft(draftId: Uuid) {
        viewModelScope.launch {
            try {
                deleteEntryDraft(draftId)
                Napier.d("Deleted draft: $draftId")
            } catch (e: Exception) {
                Napier.e("Failed to delete draft: ${e.message}", e)
                _mutableState.update { 
                    it.copy(errorMessage = "Failed to delete draft: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Deletes all drafts.
     */
    fun deleteAllDrafts() {
        viewModelScope.launch {
            try {
                val currentDrafts = editorState.value.availableDrafts
                currentDrafts.forEach { draft ->
                    deleteEntryDraft(draft.id)
                }
                Napier.d("Deleted all ${currentDrafts.size} drafts")
            } catch (e: Exception) {
                Napier.e("Failed to delete all drafts: ${e.message}", e)
                _mutableState.update { 
                    it.copy(errorMessage = "Failed to delete all drafts: ${e.message}")
                }
            }
        }
    }
    
    // AudioRecordingViewModel is now provided by Koin DI directly

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
                val currentState = _mutableState.value
                
                // Only add initial text if we don't have any blocks yet
                if (currentState.blocks.isEmpty()) {
                    // Create a new text block with the initial content
                    val textBlock = createNewBlock(BlockType.TEXT) as TextBlockUiState
                    updateBlock(textBlock.copy(content = content))
                    
                    Napier.d("Added initial text content block")
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
                // Process each attachment and create appropriate blocks
                attachmentUris.forEach { uri ->
                    // Determine block type based on URI (simplified logic)
                    // In a real implementation, you'd analyze the MIME type or URI pattern
                    val blockType = when {
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
                        
                        else -> BlockType.IMAGE // Default to image for unknown types
                    }
                    
                    // Create appropriate block and update it with the URI
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
                            updateBlock(block.copy(uri = uri))
                        }
                        else -> {
                            // Shouldn't reach here based on our logic above
                            Napier.w("Unhandled attachment type for URI: $uri")
                        }
                    }
                }
                
                Napier.d("Added ${attachmentUris.size} initial attachments")
            } catch (e: Exception) {
                Napier.e("Failed to add initial attachments: ${e.message}", e)
            }
        }
    }
}

/**
 * Maps a JournalNote domain model to an EntryBlockData UI model.
 */
fun JournalNote.toDomainBlock(): EntryBlockUiState {
    return when (this) {
        is JournalNote.Text -> TextBlockUiState(
            id = uid,
            timestamp = creationTimestamp,
            location = null, // We don't have location data from notes
            content = content
        )

        is JournalNote.Image -> ImageBlockUiState(
            id = uid,
            timestamp = creationTimestamp,
            location = null,
            uri = mediaRef
        )

        is JournalNote.Video -> VideoBlockUiState(
            id = uid,
            timestamp = creationTimestamp,
            location = null,
            uri = mediaRef
        )

        is JournalNote.Audio -> AudioBlockUiState(
            id = uid,
            timestamp = creationTimestamp,
            location = null,
            uri = mediaRef
        )

        else -> TextBlockUiState(
            id = uid,
            timestamp = creationTimestamp,
            location = null,
            content = ""
        )
    }
}

