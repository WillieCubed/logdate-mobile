package app.logdate.feature.editor.ui

import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import app.logdate.feature.editor.ui.editor.EditorAudioCaptureStartGate
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.handwriting.EntryPageOverviewState
import app.logdate.feature.editor.ui.handwriting.EntryPageScrollAnchor

internal fun editorReturnToEntry(
    currentState: () -> EditorState,
    page: EntryPageOverviewState,
    captureGate: EditorAudioCaptureStartGate,
    focusManager: FocusManager,
    keyboard: SoftwareKeyboardController?,
    dismiss: () -> Boolean,
): () -> Boolean =
    {
        var accepted = false
        captureGate.runWhenIdle {
            val id = currentState().expandedBlockId
            if (dismiss()) {
                id?.let { page.focusedAnchor = EntryPageScrollAnchor(it, page.blockPositions[it] ?: 88f, page.viewportWidth) }
                focusManager.clearFocus()
                keyboard?.hide()
                accepted = true
            }
        }
        accepted
    }

internal fun EditorState.canSaveFocusedEntry(focus: app.logdate.feature.editor.ui.layout.EditorFocus): Boolean =
    focus !is app.logdate.feature.editor.ui.layout.EditorFocus.Recording &&
        !isLoading &&
        !isEditingLocked &&
        !isSaving &&
        !shouldExit &&
        audioCaptureStartReservations.isEmpty()
