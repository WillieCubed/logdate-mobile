package app.logdate.feature.editor.ui.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.ui.theme.Spacing
import io.github.aakira.napier.Napier

/**
 * Content component for text blocks in the editor.
 *
 * @param block The data object containing the text content
 * @param isExpanded Whether the text block is expanded (full editor) or collapsed
 * @param onTextChanged Callback for when text content changes
 * @param onFocused Callback for when this block receives focus
 * @param readOnly Whether the block is read-only
 * @param modifier Modifier for the component
 */
@Composable
fun TextBlockContent(
    block: TextBlockUiState,
    isExpanded: Boolean = true,
    onTextChanged: (String) -> Unit,
    onFocused: () -> Unit,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    // The container for the text field
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                enabled = !readOnly,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isExpanded) {
                    onFocused()
                }
            }
    ) {
        // The actual text field - use local state with callback
        BasicTextField(
            value = block.content,
            onValueChange = { newValue ->
                // Directly propagate changes to parent
                onTextChanged(newValue)
            },
            modifier = Modifier
                .fillMaxSize()
                .focusable(enabled = !readOnly)
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = true,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            ),
            readOnly = readOnly,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.padding(Spacing.md)) {
                    if (block.content.isEmpty() && !isExpanded) {
                        Text(
                            text = "What's on your mind?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }

    // Request focus when specified
    LaunchedEffect(block.id, isExpanded) {
        if (isExpanded && !readOnly) {
            try {
                focusRequester.requestFocus()
                onFocused()
            } catch (e: Exception) {
                Napier.e("Failed to request focus: ${e.message}", e)
            }
        }
    }
}