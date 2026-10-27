@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.handwriting.EntryPageChrome
import app.logdate.feature.editor.ui.handwriting.EntryPageOverviewState
import app.logdate.feature.editor.ui.handwriting.StoryCaptureMode
import app.logdate.feature.editor.ui.layout.EditorFocus
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout

@Composable
internal fun EntryEditorFrame(
    state: EditorState,
    page: EntryPageOverviewState,
    defaultMode: EntryStartMode,
    blockFocusEnabled: Boolean,
    focusProgress: Float,
    onReturn: () -> Unit,
    onExit: () -> Unit,
    onCapture: (StoryCaptureMode) -> Unit,
    topBarContent: @Composable () -> Unit,
    editorContent: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit,
    modifier: Modifier,
    isImmersiveBlockActive: Boolean,
    immersiveExitProgress: Float,
    bottomContentTopPadding: Dp,
    isAudioRecordingActive: Boolean,
    focusState: EditorFocus?,
    hasJournalSelection: Boolean,
    entryLocked: Boolean,
    animateContextControls: Boolean,
    drawingBackdrop: Color,
) {
    if (blockFocusEnabled) {
        EntryPageChrome(
            state,
            page,
            defaultMode,
            onReturn,
            onExit,
            onCapture,
            modifier = modifier,
            header = topBarContent,
            focusProgress = focusProgress,
            content = editorContent,
        )
    } else {
        ImmersiveEditorLayout(
            topBarContent,
            editorContent,
            bottomContent,
            modifier,
            isImmersiveBlockActive = isImmersiveBlockActive,
            immersiveExitProgress = immersiveExitProgress,
            bottomContentTopPadding = bottomContentTopPadding,
            isAudioRecordingActive = isAudioRecordingActive,
            focusState = focusState,
            hasJournalSelection = hasJournalSelection,
            entryLocked = entryLocked,
            animateContextControls = animateContextControls,
            drawingBackdrop = drawingBackdrop,
        )
    }
}
