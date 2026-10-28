package app.logdate.feature.editor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.state.BlocksUiState
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FocusedTextViewportTest {
    @Test
    fun landscapeKeyboardViewportLeavesRoomForWritingBetweenChrome() =
        runDesktopComposeUiTest(width = 800, height = 192) {
            val block = TextBlockUiState(content = "The light was too good to miss.")
            setContent {
                MaterialTheme {
                    MainEditorContent(
                        BlocksUiState(
                            blocks = listOf(block),
                            expandedBlockId = block.id,
                            availableJournals = emptyList(),
                            selectedJournalIds = emptyList(),
                            onBlockFocused = {},
                            onJournalSelectionChanged = {},
                            onUpdateBlock = {},
                            onCreateBlock = { _, _ -> block },
                            onDeleteBlock = {},
                        ),
                        shouldReturnToPickerOnBack = false,
                        onDismissExpanded = {},
                        expandAnySelectedBlock = true,
                        floatingChrome = true,
                    )
                }
            }
            val field = onNodeWithTag("editor_text_input").getUnclippedBoundsInRoot()
            assertTrue(field.bottom - field.top >= 48.dp, "Writing is squeezed out of the keyboard viewport: $field")
            assertTrue(field.top >= 64.dp && field.bottom <= 192.dp, "Meaningful writing must reclaim the hidden creation area: $field")
        }
}
