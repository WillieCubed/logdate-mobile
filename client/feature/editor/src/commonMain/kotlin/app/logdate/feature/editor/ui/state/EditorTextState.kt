package app.logdate.feature.editor.ui.state

import androidx.compose.runtime.Immutable
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import kotlin.uuid.Uuid

/**
 * Represents the state and callbacks for the text editing portion of the editor.
 * Acts as a focused bus between the ViewModel and text editing components.
 *
 * @property content The current text content of the editor
 * @property onTextChanged Callback for when text content changes
 * @property onBlockFocused Callback for when a block receives focus
 */
@Immutable
data class EditorTextState(
    val content: String = "",
    val focused: Boolean = false,
    val onTextChanged: (TextBlockUiState, String) -> Unit,
    val onBlockFocused: (Uuid) -> Unit,
)