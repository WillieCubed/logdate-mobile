package app.logdate.feature.editor.ui.editor

import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/**
 * Autosaves the current entry state as a draft.
 */

internal fun EntryEditorViewModel.autoSaveEntryImpl(state: EditorState) {
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

internal fun EntryEditorViewModel.autoSaveLatestEntryImpl() = autoSaveEntry(currentDraftPersistenceState(editorState.value))

/**
 * The id of the draft Handoff names when it offers this entry to another device, or null for an
 * empty new entry. Text that has no draft yet gets the id its first draft will be saved under,
 * and that save starts now, so the id can be handed off at once rather than after autosave.
 */
internal fun EntryEditorViewModel.draftIdForHandoffImpl(): Uuid? {
    val state = mutableEditorState.value
    (state.draftState as? DraftState.Active)?.let { return it.id }
    if (!state.hasContent()) return null
    val draftId = draftManager.reserveDraftId()
    autoSaveLatestEntry()
    return draftId
}

/**
 * Suspends until the local draft repository finishes persisting [state].
 */
internal suspend fun EntryEditorViewModel.persistDraftImpl(state: EditorState): Uuid? =
    persistDraft(
        state = state,
        allowWhileExplicitlySaving = false,
    )

/**
 * Persists an explicit exit draft and reports whether it is safe for the UI to navigate.
 */
internal suspend fun EntryEditorViewModel.saveAsDraftImpl(state: EditorState): Boolean {
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
internal suspend fun EntryEditorViewModel.discardAndExitImpl(): Boolean {
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

internal suspend fun EntryEditorViewModel.persistDraft(
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
internal suspend fun EntryEditorViewModel.persistLatestDraftUntilStable(initialState: EditorState): Uuid? {
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

internal suspend fun EntryEditorViewModel.persistDraftSnapshot(state: EditorState): Uuid? =
    draftManager.autoSave(state)?.also { draftId ->
        mutableEditorState.update {
            it.copy(
                draftState = DraftState.Active(draftId),
                // The journal shown by default is only derived while there is no draft, so the
                // one the draft was saved with becomes the selection.
                selectedJournalIds =
                    if (it.selectedJournalIds.isEmpty() && !it.hasJournalSelectionChanges) {
                        state.selectedJournalIds
                    } else {
                        it.selectedJournalIds
                    },
                hasJournalSelectionChanges =
                    if (it.selectedJournalIds == state.selectedJournalIds) {
                        false
                    } else {
                        it.hasJournalSelectionChanges
                    },
            )
        }
    }

internal fun EntryEditorViewModel.currentDraftPersistenceState(template: EditorState): EditorState {
    val current = mutableEditorState.value
    val combined = editorState.value
    val selectedJournalIds = authoritativeSelectedJournalIds(current, combined)
    return template.copy(
        blocks = current.blocks,
        markId = current.markId,
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

internal fun EntryEditorViewModel.authoritativeSelectedJournalIds(
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
