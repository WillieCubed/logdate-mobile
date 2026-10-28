@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.logdate.feature.editor.ui.common.NoteEditorToolbar
import app.logdate.feature.editor.ui.content.EditorBottomContent
import app.logdate.feature.editor.ui.editor.AutoSaveStatus
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.handwriting.EntryFocusPrototypeHeader
import app.logdate.feature.editor.ui.handwriting.EntryFocusPrototypeTools
import app.logdate.feature.editor.ui.handwriting.StoryCaptureMode
import app.logdate.feature.editor.ui.handwriting.captureModeForBlock
import app.logdate.feature.editor.ui.layout.EditorFocus
import app.logdate.feature.editor.ui.layout.editorFocus
import kotlin.uuid.Uuid

@Composable
internal fun EntryEditorTopBar(
    state: EditorState,
    blockFocusEnabled: Boolean,
    immersive: Boolean,
    autoSaveStatus: AutoSaveStatus,
    onBack: () -> Unit,
    onReturnToEntry: () -> Unit,
    onShowDrafts: () -> Unit,
    onSave: () -> Unit,
    onCaptureMode: (StoryCaptureMode) -> Unit,
    onJournalSelection: (List<Uuid>) -> Unit,
    onChooseDefaultMode: (() -> Unit)? = null,
    floating: Boolean = false,
) {
    val recording = state.editorFocus() is EditorFocus.Recording
    val enabled = !state.isSaving && !state.shouldExit && state.audioCaptureStartReservations.isEmpty()
    if (blockFocusEnabled) {
        val selectedJournal = state.availableJournals.firstOrNull { it.id in state.selectedJournalIds }
        EntryFocusPrototypeHeader(
            block = state.blocks.firstOrNull { it.id == state.expandedBlockId },
            canvasOnly = immersive,
            recording = state.isRecordingAudio || state.audioCaptureStartReservations.isNotEmpty(),
            onReturnToEntry = onReturnToEntry,
            onExit = onBack,
            journal = selectedJournal,
            journalSelected = selectedJournal != null,
            onJournalSelection = {},
            onCaptureMode = onCaptureMode,
            onSave = onSave,
            saveInProgress = state.isSaving,
            saveEnabled = !recording && !state.isEditingLocked && enabled && state.blocks.isNotEmpty(),
            availableJournals = state.availableJournals,
            onSelectJournal = { onJournalSelection(listOf(it.id)) },
            onShowDrafts = onShowDrafts,
            floating = floating,
            draftCount = state.availableDrafts.size,
            onChooseDefaultMode = onChooseDefaultMode,
        )
    } else {
        NoteEditorToolbar(
            onChooseDefaultMode = onChooseDefaultMode,
            modifier = Modifier.testTag("entry_mode_overview"),
            onBack = onBack,
            onSave = onSave,
            onShowDrafts = onShowDrafts,
            draftCount = state.availableDrafts.size,
            autoSaveStatus = autoSaveStatus,
            actionsVisible = !immersive,
            optionsVisible = !recording,
            saveEnabled = !recording && !state.isEditingLocked && enabled,
            actionsEnabled = enabled,
        )
    }
}

@Composable
internal fun EntryEditorCaptureControls(
    state: EditorState,
    blockFocusEnabled: Boolean,
    journalExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCaptureMode: (StoryCaptureMode) -> Unit,
    onJournalSelection: (List<Uuid>) -> Unit,
) {
    val recording = state.editorFocus() is EditorFocus.Recording
    val enabled = !recording && !state.isSaving && !state.shouldExit && !state.isEditingLocked
    if (blockFocusEnabled) {
        if (state.blocks.isNotEmpty() && !state.isRecordingAudio) {
            EntryFocusPrototypeTools(captureModeForBlock(state.blocks.firstOrNull { it.id == state.expandedBlockId }), onCaptureMode)
        }
    } else {
        EditorBottomContent(
            availableJournals = state.availableJournals,
            selectedJournalIds = state.selectedJournalIds,
            onJournalSelectionChanged = { if (!recording) onJournalSelection(it) },
            enabled = enabled,
            journalSelectorExpanded = journalExpanded,
            onJournalSelectorExpandedChange = { if (enabled) onExpandedChange(it) },
        )
    }
}
