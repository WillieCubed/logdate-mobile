package app.logdate.feature.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.TextBlockData

/**
 * Represents the UI state for a text block editor.
 * This class handles all the visual and interaction states that affect
 * how the editor is displayed and behaves.
 */
@Stable
class TextBlockEditorUiState(
    initialContent: String,
    private val onContentChanged: (String) -> Unit
) {
    // The current text content and selection state
    var textFieldValue by mutableStateOf(TextFieldValue(initialContent))
        private set

    // Editor focus state
    var isFocused by mutableStateOf(false)
        private set

    // Tracks whether the user is currently dragging to select text
    var isSelectingText by mutableStateOf(false)
        private set

    // Visual feedback states
    var isExpanded by mutableStateOf(false)
        private set

    var characterCount by mutableStateOf(initialContent.length)
        private set

    // Error handling state
    var error by mutableStateOf<String?>(null)
        private set

    // Placeholder visibility state
    val showPlaceholder: Boolean
        get() = textFieldValue.text.isEmpty() && !isFocused

    /**
     * Updates the text content while maintaining selection and managing side effects
     */
    fun onTextChanged(newValue: TextFieldValue) {
        // Update character count
        characterCount = newValue.text.length

        // Validate content length
        error = when {
            characterCount > MAX_CHARACTERS -> "Text is too long"
            else -> null
        }

        // Update the text field value
        textFieldValue = newValue

        // Notify listeners of content change
        onContentChanged(newValue.text)
    }

    /**
     * Handles focus changes and updates UI accordingly
     */
    fun onFocusChanged(focused: Boolean) {
        isFocused = focused
        if (focused) {
            isExpanded = true
        }
    }

    /**
     * Expands or collapses the editor
     */
    fun toggleExpanded() {
        isExpanded = !isExpanded
        // If collapsing, also clear selection
        if (!isExpanded) {
            clearSelection()
        }
    }

    /**
     * Clears the current text selection
     */
    fun clearSelection() {
        textFieldValue = textFieldValue.copy(
            selection = TextRange(textFieldValue.text.length)
        )
        isSelectingText = false
    }

    /**
     * Selects all text in the editor
     */
    fun selectAll() {
        textFieldValue = textFieldValue.copy(
            selection = TextRange(0, textFieldValue.text.length)
        )
        isSelectingText = true
    }

    /**
     * Updates text selection state
     */
    fun onSelectionChanged(isSelecting: Boolean) {
        isSelectingText = isSelecting
    }

    companion object {
        private const val MAX_CHARACTERS = 5000
    }
}

/**
 * Remembers and provides a TextBlockEditorUiState instance.
 * This composable ensures the UI state survives recomposition and
 * handles initialization and cleanup properly.
 */
@Composable
fun rememberTextBlockEditorState(
    block: TextBlockData,
    editorState: EditorState
): TextBlockEditorUiState {
    // Remember UI state for this specific block
    return remember(block.id) {
        TextBlockEditorUiState(
            initialContent = block.content,
            onContentChanged = { newContent ->
                editorState.updateBlock(
                    block.copy(content = newContent)
                )
            }
        )
    }
}