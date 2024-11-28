package app.logdate.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

/**
 * Entry block for text content.
 *
 * @param state The UI state for this text block
 * @param isExpanded Whether the block is currently expanded
 * @param onExpandClick Callback when the expand button is clicked
 * @param onCollapseClick Callback when the collapse button is clicked
 * @param modifier Optional modifier for the block
 * @param enabled Whether the block is enabled for editing
 */
@Composable
internal fun TextBlockContent(
    state: TextBlockEditorUiState,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onCollapseClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(modifier) {
        BasicTextField(
            value = state.textFieldValue,
            onValueChange = { state.onTextChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (isExpanded) 240.dp else 80.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .onFocusChanged { state.onFocusChanged(it.isFocused) },
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            // Optional: Add decorationBox to show placeholder when empty
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (state.showPlaceholder) {
                        Text(
                            text = "Write something...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )

        IconButton(
            onClick = if (isExpanded) onCollapseClick else onExpandClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = if (isExpanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = if (isExpanded) {
                    "Collapse note"
                } else {
                    "Expand note"
                }
            )
        }
    }
}