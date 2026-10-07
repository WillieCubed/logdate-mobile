package app.logdate.feature.editor.ui.layout

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import kotlin.uuid.Uuid

sealed interface EditorFocus {
    data object Entry : EditorFocus

    data class Editing(
        val blockId: Uuid,
    ) : EditorFocus

    data class Recording(
        val blockId: Uuid? = null,
    ) : EditorFocus

    data class Camera(
        val blockId: Uuid? = null,
    ) : EditorFocus
}

internal fun EditorState.editorFocus(): EditorFocus = editorFocus(blocks, expandedBlockId)

internal fun editorFocus(
    blocks: List<EntryBlockUiState>,
    selectedId: Uuid?,
): EditorFocus {
    blocks
        .filterIsInstance<AudioBlockUiState>()
        .firstOrNull {
            it.captureState is AudioCaptureState.Recording || it.captureState is AudioCaptureState.Stopping
        }?.let { return EditorFocus.Recording(it.id) }
    val selected = blocks.firstOrNull { it.id == selectedId } ?: return EditorFocus.Entry
    return if (selected is CameraBlockUiState && selected.uri == null) EditorFocus.Camera(selected.id) else EditorFocus.Editing(selected.id)
}

@Immutable
internal data class EditorFocusPresentation(
    val focus: EditorFocus = EditorFocus.Entry,
    val entryActionsEnabled: Boolean = true,
    val showJournalContext: Boolean = true,
    val showSecondaryContext: Boolean = false,
    val blockScrollEnabled: Boolean = true,
) {
    val recordingBlockId: Uuid? get() = (focus as? EditorFocus.Recording)?.blockId
    val isRecording: Boolean get() = focus is EditorFocus.Recording
}

internal fun editorFocusPresentation(
    focus: EditorFocus,
    paneWidth: Dp,
    secondaryPaneAvailable: Boolean,
    keyboardVisible: Boolean,
    hasJournalSelection: Boolean = true,
    entryLocked: Boolean = false,
): EditorFocusPresentation {
    val capturing = focus is EditorFocus.Recording || focus is EditorFocus.Camera
    return EditorFocusPresentation(
        focus = focus,
        entryActionsEnabled = !capturing && !entryLocked,
        showJournalContext =
            !keyboardVisible && focus !is EditorFocus.Camera && (!capturing || (paneWidth >= 600.dp && hasJournalSelection)),
        showSecondaryContext = focus is EditorFocus.Recording && secondaryPaneAvailable,
        blockScrollEnabled = !capturing,
    )
}

internal val LocalEditorFocusPresentation = compositionLocalOf { EditorFocusPresentation() }
