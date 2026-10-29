package app.logdate.feature.editor.ui.text

import androidx.compose.runtime.compositionLocalOf
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import app.logdate.feature.editor.ui.editor.commitTextInput
import kotlin.uuid.Uuid

/** Commits against the latest document; rendered echoes may arrive after newer local edits. */
internal class EditorTextInputSession(
    val renderedRevision: Long,
    val runtimeId: Uuid? = null,
    val commit: (Uuid, Uuid?, String) -> Long?,
)

internal val LocalEditorTextInputSession = compositionLocalOf<EditorTextInputSession?> { null }

internal fun editorTextInputSession(
    viewModel: EntryEditorViewModel,
    renderedRevision: Long,
): EditorTextInputSession = EditorTextInputSession(renderedRevision, viewModel.textInputRuntimeId, viewModel::commitTextInput)
