package app.logdate.feature.editor.ui.editor

import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.text.toUiText
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel.LoadRequest
import app.logdate.feature.editor.ui.editor.delegate.AudioBlockFinalizer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.editor_error_delete_draft_failed
import logdate.client.feature.editor.generated.resources.editor_error_delete_drafts_failed
import logdate.client.feature.editor.generated.resources.editor_error_load_draft_failed
import kotlin.uuid.Uuid

/**
 * Sets the expanded block ID.
 */

internal fun EntryEditorViewModel.setExpandedBlockIdImpl(blockId: Uuid?) {
    mutableEditorState.update { it.copy(expandedBlockId = blockId) }
}

/**
 * Registers the audio finalizer consulted for [blockId] at save time.
 *
 * Call once per block, from that block's own `AudioBlockEditor`, with a finalizer backed by
 * the live, block-scoped [AudioViewModel][app.logdate.feature.editor.ui.audio.AudioViewModel]
 * instance that composable owns via its own keyed `koinViewModel()` call -- not one Koin
 * resolves during this ViewModel's own construction, which would be disconnected from any
 * in-progress recording. See [audioBlockFinalizers] for why this indirection exists at all.
 */
internal fun EntryEditorViewModel.bindAudioBlockFinalizerImpl(
    blockId: Uuid,
    finalizer: AudioBlockFinalizer,
) {
    audioBlockFinalizers[blockId] = finalizer
}

/**
 * The finalizer to consult for [blockId]: its own bound finalizer if
 * [bindAudioBlockFinalizer] has been called for it, otherwise [defaultAudioBlockFinalizer]
 * for a block whose `AudioBlockEditor` hasn't composed yet.
 */
internal fun EntryEditorViewModel.audioBlockFinalizerFor(blockId: Uuid): AudioBlockFinalizer =
    audioBlockFinalizers[blockId] ?: defaultAudioBlockFinalizer

/**
 * Removes the lone empty block so the editor can return to the initial content-type picker.
 *
 * @return true if the editor was reset to the empty picker, false otherwise
 */
internal fun EntryEditorViewModel.clearSingleEmptyBlockImpl(): Boolean {
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
internal fun EntryEditorViewModel.dismissExpandedBlockImpl(): Boolean {
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
internal fun EntryEditorViewModel.dismissExpandedBlockOrClearSingleEmptyImpl(): Boolean {
    if (clearSingleEmptyBlock()) {
        return true
    }

    return dismissExpandedBlock()
}

/**
 * Loads a draft into the editor.
 */
internal fun EntryEditorViewModel.resumeFirstEntryDraftImpl(openPreferredIfEmpty: Boolean = false) {
    viewModelScope.launch {
        val drafts = editorState.first { !it.isLoading }.availableDrafts
        val latest =
            drafts
                .withIndex()
                .maxWithOrNull(
                    compareBy<IndexedValue<EntryDraft>> { it.value.updatedAt }.thenBy { it.index },
                )?.value
        if (latest != null) {
            loadDraft(latest.id)
        } else if (openPreferredIfEmpty) {
            initializePreferredEntryMode()
        }
    }
}

internal fun EntryEditorViewModel.loadDraftImpl(draftId: Uuid) {
    val request = LoadRequest.Draft(draftId)
    if (activeLoadRequest == request) return
    if (mutableEditorState.value.isEditingLocked || mutableEditorState.value.shouldExit) return
    activeLoadRequest = request
    val generation = ++loadGeneration
    val requestedContentRevision = mutableEditorState.value.contentRevision
    viewModelScope.launch {
        draftPersistenceMutex.withLock {
            draftManager.loadDraft(draftId).fold(
                onSuccess = { loaded ->
                    val recoveredBlocks = recoverPendingAudio(loaded.blocks)
                    if (generation != loadGeneration || mutableEditorState.value.contentRevision != requestedContentRevision) {
                        return@withLock
                    }
                    mutateEditorContent { currentState ->
                        if (generation != loadGeneration || currentState.contentRevision != requestedContentRevision) {
                            currentState
                        } else {
                            currentState.copy(
                                blocks = recoveredBlocks,
                                entryEditBase = loaded.editBase,
                                selectedJournalIds = loaded.selectedJournalIds,
                                visitContext = loaded.visitContext,
                                markId = loaded.markId,
                                hasJournalSelectionChanges = false,
                                draftState = DraftState.Active(loaded.draftId),
                                isModified = true,
                                errorMessage = null,
                            )
                        }
                    }
                },
                onFailure = { e ->
                    if (generation != loadGeneration) return@withLock
                    if (activeLoadRequest == request) activeLoadRequest = null
                    Napier.e("Failed to load draft: ${e.message}", e)
                    mutableEditorState.update {
                        it.copy(errorMessage = Res.string.editor_error_load_draft_failed.toUiText(e.message.toString()))
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
internal suspend fun EntryEditorViewModel.recoverPendingAudio(blocks: List<EntryBlockUiState>): List<EntryBlockUiState> {
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
internal fun EntryEditorViewModel.deleteDraftImpl(draftId: Uuid) {
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
                        it.copy(errorMessage = Res.string.editor_error_delete_draft_failed.toUiText(e.message.toString()))
                    }
                },
            )
        }
    }
}

/**
 * Deletes all drafts atomically.
 */
internal fun EntryEditorViewModel.deleteAllDraftsImpl() {
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
                        it.copy(errorMessage = Res.string.editor_error_delete_drafts_failed.toUiText(e.message.toString()))
                    }
                },
            )
        }
    }
}
