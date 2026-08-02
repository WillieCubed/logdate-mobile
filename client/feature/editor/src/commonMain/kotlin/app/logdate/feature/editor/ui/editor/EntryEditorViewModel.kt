package app.logdate.feature.editor.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.editor.ObserveEditorDataUseCase
import app.logdate.client.domain.editor.SaveEntryUseCase
import app.logdate.feature.editor.ui.editor.delegate.AudioBlockFinalizer
import app.logdate.feature.editor.ui.editor.delegate.ContentLoader
import app.logdate.feature.editor.ui.editor.delegate.DraftManager
import app.logdate.feature.editor.ui.editor.delegate.PendingAudioRecoverer
import app.logdate.feature.editor.ui.mapper.toDomainBlock
import app.logdate.feature.editor.ui.mapper.toJournalNote
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    private val audioBlockFinalizer: AudioBlockFinalizer = AudioBlockFinalizer.NoOp,
    private val pendingAudioRecoverer: PendingAudioRecoverer? = null,
) : ViewModel() {
    // Internal mutable state that can be modified by UI
    private val mutableEditorState =
        MutableStateFlow(
            EditorState(
                isLoading = true,
                disableEmptyBlockCreation = false,
            ),
        )

    private val draftPersistenceMutex = Mutex()

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
                mutateEditorContent { state ->
                    if (state.selectedJournalIds.isEmpty() && !state.hasJournalSelectionChanges) {
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
                if (
                    currentState.selectedJournalIds.isEmpty() &&
                    !currentState.hasJournalSelectionChanges &&
                    currentState.draftState == DraftState.None &&
                    data.journals.isNotEmpty()
                ) {
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

    /** Atomically rejects durable editor mutations once an exclusive save or exit begins. */
    private fun mutateEditorContent(transform: (EditorState) -> EditorState): Boolean {
        while (true) {
            val currentState = mutableEditorState.value
            if (currentState.isEditingLocked || currentState.shouldExit) return false
            val updatedState = transform(currentState)
            if (updatedState == currentState) return false
            if (
                mutableEditorState.compareAndSet(
                    currentState,
                    updatedState.copy(contentRevision = currentState.contentRevision + 1),
                )
            ) {
                return true
            }
        }
    }

    /**
     * Sets the journals that this entry is associated with.
     */
    fun setSelectedJournals(journalIds: List<Uuid>) {
        val internalState = mutableEditorState.value
        val effectiveSelection = authoritativeSelectedJournalIds(internalState, editorState.value)
        mutateEditorContent { currentState ->
            if (effectiveSelection == journalIds) {
                if (currentState.selectedJournalIds == journalIds) {
                    currentState
                } else {
                    currentState.copy(selectedJournalIds = journalIds)
                }
            } else {
                currentState.copy(
                    selectedJournalIds = journalIds,
                    hasJournalSelectionChanges = true,
                    isModified = true,
                )
            }
        }
    }

    /** Applies navigation-provided journal context without treating it as a user edit. */
    fun initializeSelectedJournals(journalIds: List<Uuid>) {
        mutateEditorContent { currentState ->
            if (currentState.selectedJournalIds == journalIds) {
                currentState
            } else {
                currentState.copy(selectedJournalIds = journalIds)
            }
        }
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

        mutateEditorContent { currentState ->
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
        mutateEditorContent { currentState ->
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
        mutateEditorContent { state ->
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
        mutateEditorContent { currentState ->
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

        viewModelScope.launch {
            try {
                persistDraft(state)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Napier.e("Failed to auto-save draft: ${e.message}", e)
                mutableEditorState.update {
                    it.copy(errorMessage = "Failed to save draft: ${e.message}")
                }
            }
        }
    }

    /**
     * Suspends until the local draft repository finishes persisting [state].
     */
    suspend fun persistDraft(state: EditorState): Uuid? =
        persistDraft(
            state = state,
            allowWhileExplicitlySaving = false,
        )

    /**
     * Persists an explicit exit draft and reports whether it is safe for the UI to navigate.
     */
    suspend fun saveAsDraft(state: EditorState): Boolean {
        if (tryBeginExclusiveOperation(state, lockEditing = false) == null) return false

        return try {
            val draftId =
                persistDraft(
                    state = state,
                    allowWhileExplicitlySaving = true,
                ) ?: error("Draft has no content to save")
            mutableEditorState.update {
                it.copy(
                    draftState = DraftState.Active(draftId),
                    errorMessage = null,
                )
            }
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Napier.e("Failed to save exit draft: ${e.message}", e)
            mutableEditorState.update {
                it.copy(
                    errorMessage = "Failed to save draft: ${e.message}",
                )
            }
            false
        } finally {
            mutableEditorState.update {
                it.copy(
                    isSaving = false,
                    isEditingLocked = false,
                )
            }
        }
    }

    /** Deletes every durable draft owned by this editor before allowing navigation. */
    suspend fun discardAndExit(): Boolean {
        if (tryBeginExclusiveOperation(mutableEditorState.value, lockEditing = true) == null) {
            return false
        }

        return try {
            draftPersistenceMutex.withLock {
                val activeDraftId = (mutableEditorState.value.draftState as? DraftState.Active)?.id
                draftManager.draftIdsForCleanup(activeDraftId).forEach { draftId ->
                    draftManager.deleteDraft(draftId).getOrThrow()
                }
                mutableEditorState.update {
                    it.copy(
                        draftState = DraftState.None,
                        hasJournalSelectionChanges = false,
                        isModified = false,
                        errorMessage = null,
                        shouldExit = true,
                        exitReason = EditorExitReason.DISCARDED,
                    )
                }
            }
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Napier.e("Failed to discard draft: ${error.message}", error)
            mutableEditorState.update {
                it.copy(errorMessage = "Failed to discard draft: ${error.message}")
            }
            false
        } finally {
            mutableEditorState.update { currentState ->
                currentState.copy(
                    isSaving = false,
                    isEditingLocked = currentState.shouldExit,
                )
            }
        }
    }

    private suspend fun persistDraft(
        state: EditorState,
        allowWhileExplicitlySaving: Boolean,
    ): Uuid? =
        draftPersistenceMutex.withLock {
            val currentState = mutableEditorState.value
            if (state.shouldExit || currentState.shouldExit) return@withLock null
            if (!allowWhileExplicitlySaving && (state.isSaving || currentState.isSaving)) {
                return@withLock null
            }
            if (!allowWhileExplicitlySaving && state.isModified && !currentState.isModified) {
                return@withLock null
            }
            if (!allowWhileExplicitlySaving && state.contentRevision != currentState.contentRevision) {
                return@withLock null
            }

            if (allowWhileExplicitlySaving) {
                return@withLock persistLatestDraftUntilStable(state)
            }

            // A queued snapshot may predate the first durable save. Always reuse the currently
            // active draft identity so serialized saves cannot create sibling drafts.
            val stateWithCurrentDraft = state.copy(draftState = currentState.draftState)
            persistDraftSnapshot(stateWithCurrentDraft)
        }

    /**
     * Explicit exit saves fail closed until the durable snapshot catches up with every editor
     * mutation that arrived while an earlier write was suspended.
     */
    private suspend fun persistLatestDraftUntilStable(initialState: EditorState): Uuid? {
        var snapshot = currentDraftPersistenceState(initialState)
        while (true) {
            val draftId = persistDraftSnapshot(snapshot) ?: return null
            val latest =
                currentDraftPersistenceState(snapshot).copy(
                    draftState = DraftState.Active(draftId),
                )
            if (getEditorDraftFingerprint(latest) == getEditorDraftFingerprint(snapshot)) {
                return draftId
            }
            snapshot = latest
        }
    }

    private suspend fun persistDraftSnapshot(state: EditorState): Uuid? =
        draftManager.autoSave(state)?.also { draftId ->
            mutableEditorState.update {
                it.copy(
                    draftState = DraftState.Active(draftId),
                    hasJournalSelectionChanges =
                        if (it.selectedJournalIds == state.selectedJournalIds) {
                            false
                        } else {
                            it.hasJournalSelectionChanges
                        },
                )
            }
        }

    private fun currentDraftPersistenceState(template: EditorState): EditorState {
        val current = mutableEditorState.value
        val combined = editorState.value
        val selectedJournalIds = authoritativeSelectedJournalIds(current, combined)
        return template.copy(
            blocks = current.blocks,
            readOnlyBlocks = combined.readOnlyBlocks,
            selectedJournalIds = selectedJournalIds,
            hasJournalSelectionChanges = current.hasJournalSelectionChanges,
            draftState = current.draftState,
            shouldExit = current.shouldExit,
            isModified = current.isModified,
            isSaving = current.isSaving,
            contentRevision = current.contentRevision,
        )
    }

    private fun authoritativeSelectedJournalIds(
        current: EditorState,
        combined: EditorState,
    ): List<Uuid> =
        if (
            current.hasJournalSelectionChanges ||
            current.draftState is DraftState.Active ||
            current.selectedJournalIds.isNotEmpty()
        ) {
            current.selectedJournalIds
        } else {
            combined.selectedJournalIds
        }

    /**
     * Saves the current entry.
     *
     * Pending audio blocks are finalized first so a recording whose URI hasn't yet
     * been propagated into the block from the recording side is absorbed into the
     * save instead of being silently dropped by the mapper.
     */
    fun saveEntry(state: EditorState) {
        val admittedContentRevision = tryBeginEntrySave(state) ?: return

        viewModelScope.launch {
            try {
                saveEntryAfterAdmission(admittedContentRevision)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Napier.e("Failed to save entry: ${error.message}", error)
                mutableEditorState.update {
                    it.copy(errorMessage = "Failed to save: ${error.message}")
                }
            } finally {
                mutableEditorState.update { currentState ->
                    if (currentState.shouldExit) {
                        currentState.copy(isSaving = false)
                    } else {
                        currentState.copy(
                            isSaving = false,
                            isEditingLocked = false,
                        )
                    }
                }
            }
        }
    }

    private suspend fun saveEntryAfterAdmission(admittedContentRevision: Long) {
        val resolvedAudio = finalizePendingAudio() ?: return

        // Wait for any local draft write already in progress. Reading the latest state and
        // deleting its active draft under the same lock prevents a completed autosave from
        // recreating a draft after publish.
        draftPersistenceMutex.withLock {
            val latestEditableState = mutableEditorState.value
            if (latestEditableState.shouldExit) return@withLock
            val publishSnapshot = currentDraftPersistenceState(latestEditableState)
            if (publishSnapshot.contentRevision != admittedContentRevision) {
                failPublishInvariant()
                return@withLock
            }
            val publishFingerprint = getEditorDraftFingerprint(publishSnapshot)
            val notes =
                publishSnapshot.blocks.mapNotNull { block ->
                    if (publishSnapshot.isReadOnly(block.id)) return@mapNotNull null
                    val effective =
                        if (block is AudioBlockUiState && block.id in resolvedAudio) {
                            block.copy(captureState = resolvedAudio.getValue(block.id))
                        } else {
                            block
                        }
                    effective.toJournalNote()
                }

            if (notes.isEmpty()) {
                val activeDraftId = (publishSnapshot.draftState as? DraftState.Active)?.id
                val cleanupDraftId = draftManager.draftIdForCleanup(activeDraftId)
                val deletionFailure = cleanupDraftId?.let { draftManager.deleteDraft(it).exceptionOrNull() }
                if (deletionFailure != null) {
                    if (deletionFailure is CancellationException) throw deletionFailure
                    Napier.e("Failed to delete cleared draft: ${deletionFailure.message}", deletionFailure)
                    applyResolvedAudio(resolvedAudio)
                    mutableEditorState.update {
                        it.copy(errorMessage = "Failed to clear draft: ${deletionFailure.message}")
                    }
                    return@withLock
                }
                if (publishSnapshotChanged(admittedContentRevision, publishFingerprint, publishSnapshot)) {
                    applyResolvedAudio(resolvedAudio)
                    failPublishInvariant()
                    return@withLock
                }
                applyResolvedAudio(resolvedAudio)
                mutableEditorState.update {
                    it.copy(
                        draftState = DraftState.None,
                        hasJournalSelectionChanges = false,
                        isModified = false,
                        shouldExit = true,
                        exitReason = EditorExitReason.ENTRY_SAVED,
                    )
                }
                return@withLock
            }

            val activeDraftId = (publishSnapshot.draftState as? DraftState.Active)?.id
            val cleanupDraftId = draftManager.draftIdForCleanup(activeDraftId)
            try {
                saveEntryUseCase(notes, publishSnapshot.selectedJournalIds, cleanupDraftId)
                draftManager.acknowledgeDraftDeletion(cleanupDraftId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                applyResolvedAudio(resolvedAudio)
                throw error
            }
            if (publishSnapshotChanged(admittedContentRevision, publishFingerprint, publishSnapshot)) {
                applyResolvedAudio(resolvedAudio)
                failPublishInvariant()
                return@withLock
            }
            applyResolvedAudio(resolvedAudio)
            mutableEditorState.update {
                it.copy(
                    draftState = DraftState.None,
                    hasJournalSelectionChanges = false,
                    isModified = false,
                    errorMessage = null,
                    shouldExit = true,
                    exitReason = EditorExitReason.ENTRY_SAVED,
                )
            }
        }
    }

    private fun publishSnapshotChanged(
        admittedContentRevision: Long,
        admittedFingerprint: String,
        template: EditorState,
    ): Boolean {
        val currentSnapshot = currentDraftPersistenceState(template)
        return currentSnapshot.contentRevision != admittedContentRevision ||
            getEditorDraftFingerprint(currentSnapshot) != admittedFingerprint
    }

    private fun failPublishInvariant() {
        mutableEditorState.update {
            it.copy(
                errorMessage = "The editor changed while saving. Review your entry before closing.",
                isSaving = false,
                isEditingLocked = false,
            )
        }
    }

    private fun tryBeginEntrySave(requestedState: EditorState): Long? = tryBeginExclusiveOperation(requestedState, lockEditing = true)

    private fun tryBeginExclusiveOperation(
        requestedState: EditorState,
        lockEditing: Boolean,
    ): Long? {
        if (requestedState.shouldExit) return null
        while (true) {
            val currentState = mutableEditorState.value
            if (currentState.shouldExit || currentState.isSaving || currentState.isEditingLocked) return null
            if (
                mutableEditorState.compareAndSet(
                    currentState,
                    currentState.copy(
                        isSaving = true,
                        isEditingLocked = lockEditing,
                        errorMessage = null,
                    ),
                )
            ) {
                return currentState.contentRevision
            }
        }
    }

    /**
     * Drives any in-flight audio recordings to a finalized state.
     *
     * Returns a map of blockId → resolved [AudioCaptureState] when every finalize
     * succeeds (possibly empty if no pending audio existed). Returns null when
     * any finalize times out or returns [AudioCaptureState.Failed] — in that case
     * the editor's [EditorState.errorMessage] is set and [EditorState.isSaving]
     * is cleared so the caller can abort the save without further work.
     *
     * The returned map is applied to [mutableEditorState] only after the save
     * completes, to avoid relying on [editorState] (a combined flow) re-emitting
     * synchronously within this coroutine.
     */
    private suspend fun finalizePendingAudio(): Map<Uuid, AudioCaptureState>? {
        val initialState = editorState.value
        val pendingBlocks =
            initialState.blocks.filterIsInstance<AudioBlockUiState>().filter { block ->
                !initialState.isReadOnly(block.id) && !block.isPersistable()
            }
        if (pendingBlocks.isEmpty()) return emptyMap()

        val finalized: List<Pair<Uuid, AudioCaptureState?>> =
            coroutineScope {
                pendingBlocks
                    .map { block ->
                        async {
                            block.id to
                                withTimeoutOrNull(AUDIO_FINALIZE_TIMEOUT_MS) {
                                    audioBlockFinalizer.finalize(block.id, block.captureState)
                                }
                        }
                    }.awaitAll()
            }

        val resolved = mutableMapOf<Uuid, AudioCaptureState>()
        for ((blockId, state) in finalized) {
            when (state) {
                null -> {
                    Napier.w("Audio finalization timed out for block $blockId")
                    mutableEditorState.update {
                        it.copy(
                            errorMessage = "Recording is still finalizing. Try again in a moment.",
                            isSaving = false,
                            isEditingLocked = false,
                        )
                    }
                    return null
                }
                is AudioCaptureState.Failed -> {
                    Napier.w("Audio finalization failed for block $blockId: ${state.reason}")
                    mutableEditorState.update {
                        it.copy(
                            errorMessage = state.reason,
                            isSaving = false,
                            isEditingLocked = false,
                        )
                    }
                    return null
                }
                else -> resolved[blockId] = state
            }
        }
        return resolved
    }

    /**
     * Pushes the resolved capture states from [finalizePendingAudio] back into
     * [mutableEditorState] so the UI reflects finalized recordings even on the
     * non-exit paths (e.g., `notes.isEmpty()`, save failure).
     */
    private fun applyResolvedAudio(resolved: Map<Uuid, AudioCaptureState>) {
        if (resolved.isEmpty()) return
        mutableEditorState.update { state ->
            state.copy(
                blocks =
                    state.blocks.map { existing ->
                        if (existing is AudioBlockUiState && existing.id in resolved) {
                            existing.copy(captureState = resolved.getValue(existing.id))
                        } else {
                            existing
                        }
                    },
            )
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
        return mutateEditorContent {
            if (it.shouldReturnToPickerOnBack()) {
                it.copy(
                    blocks = emptyList(),
                    expandedBlockId = null,
                    isModified = it.hasJournalSelectionChanges,
                )
            } else {
                it
            }
        }
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
        val requestedContentRevision = mutableEditorState.value.contentRevision
        viewModelScope.launch {
            draftPersistenceMutex.withLock {
                draftManager.loadDraft(draftId).fold(
                    onSuccess = { loaded ->
                        val recoveredBlocks = recoverPendingAudio(loaded.blocks)
                        if (mutableEditorState.value.contentRevision != requestedContentRevision) {
                            return@withLock
                        }
                        mutateEditorContent { currentState ->
                            if (currentState.contentRevision != requestedContentRevision) {
                                currentState
                            } else {
                                currentState.copy(
                                    blocks = recoveredBlocks,
                                    selectedJournalIds = loaded.selectedJournalIds,
                                    hasJournalSelectionChanges = false,
                                    draftState = DraftState.Active(loaded.draftId),
                                    isModified = true,
                                    errorMessage = null,
                                )
                            }
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
    }

    /**
     * Resolves any [AudioCaptureState.Stopping] blocks restored from a draft into
     * either [AudioCaptureState.Ready] (when the file on disk is parseable) or
     * [AudioCaptureState.Failed] (when the path is missing or the file is unusable).
     *
     * No-op when [pendingAudioRecoverer] is not wired — blocks remain Stopping,
     * which the editor's UI surfaces as a recovery affordance.
     */
    private suspend fun recoverPendingAudio(blocks: List<EntryBlockUiState>): List<EntryBlockUiState> {
        val recoverer = pendingAudioRecoverer ?: return blocks
        return blocks.map { block ->
            val capture = (block as? AudioBlockUiState)?.captureState
            if (capture is AudioCaptureState.Stopping) {
                block.copy(captureState = recoverer.recover(capture))
            } else {
                block
            }
        }
    }

    /**
     * Deletes a draft.
     */
    fun deleteDraft(draftId: Uuid) {
        val requestedContentRevision = mutableEditorState.value.contentRevision
        viewModelScope.launch {
            draftPersistenceMutex.withLock {
                draftManager.deleteDraft(draftId).fold(
                    onSuccess = {
                        mutableEditorState.update {
                            val deletedActiveDraft =
                                (it.draftState as? DraftState.Active)?.id == draftId
                            val contentUnchanged = it.contentRevision == requestedContentRevision
                            val newDraftState =
                                when (val current = it.draftState) {
                                    is DraftState.Active -> if (current.id == draftId) DraftState.None else current
                                    DraftState.None -> DraftState.None
                                }
                            it.copy(
                                draftState = newDraftState,
                                hasJournalSelectionChanges =
                                    if (deletedActiveDraft && contentUnchanged) {
                                        false
                                    } else {
                                        it.hasJournalSelectionChanges
                                    },
                                isModified =
                                    if (deletedActiveDraft && contentUnchanged) {
                                        false
                                    } else {
                                        it.isModified
                                    },
                            )
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
    }

    /**
     * Deletes all drafts atomically.
     */
    fun deleteAllDrafts() {
        val requestedContentRevision = mutableEditorState.value.contentRevision
        viewModelScope.launch {
            draftPersistenceMutex.withLock {
                draftManager.deleteAllDrafts().fold(
                    onSuccess = {
                        mutableEditorState.update {
                            it.copy(
                                draftState = DraftState.None,
                                hasJournalSelectionChanges =
                                    if (
                                        it.draftState is DraftState.Active &&
                                        it.contentRevision == requestedContentRevision
                                    ) {
                                        false
                                    } else {
                                        it.hasJournalSelectionChanges
                                    },
                                isModified =
                                    if (
                                        it.draftState is DraftState.Active &&
                                        it.contentRevision == requestedContentRevision
                                    ) {
                                        false
                                    } else {
                                        it.isModified
                                    },
                            )
                        }
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
                            selectedJournalIds = journalId?.let(::listOf) ?: currentState.selectedJournalIds,
                            hasJournalSelectionChanges = false,
                            isLoading = false,
                            isModified = false,
                            errorMessage = null,
                        )
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

    private companion object {
        /**
         * MediaRecorder.stop() typically completes in well under a second; the bound is
         * generous so a slow flush on an aging device doesn't cost the user their entry.
         */
        const val AUDIO_FINALIZE_TIMEOUT_MS: Long = 5_000L
    }
}
