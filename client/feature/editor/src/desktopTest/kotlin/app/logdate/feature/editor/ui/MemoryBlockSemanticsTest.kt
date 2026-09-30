package app.logdate.feature.editor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.editor.ui.blocks.MemoryBlockSurface
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import app.logdate.feature.editor.ui.state.BlocksUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MemoryBlockSemanticsTest {
    @Test
    fun `every memory type offers the same edit and remove actions`() {
        listOf(TextBlockUiState(), ImageBlockUiState(), AudioBlockUiState(), VideoBlockUiState()).forEach { block ->
            runDesktopComposeUiTest(width = 500, height = 500) {
                var selected = false
                var removed = false
                setContent {
                    MaterialTheme {
                        MemoryBlockSurface(block, false, { selected = true }, {}, { removed = true }) { Text("Memory content") }
                    }
                }
                listOf("Text", "Photo", "Audio", "Video").forEach {
                    onNodeWithText(it).assertDoesNotExist()
                }
                onNodeWithTag("block_menu_${block.id}").performClick()
                onNodeWithText("Edit").performClick()
                assertTrue(selected)
                onNodeWithTag("block_menu_${block.id}").performClick()
                onNodeWithText("Remove from entry").performClick()
                assertTrue(removed)
            }
        }
    }

    @Test
    fun `first empty text memory is ready to type`() =
        runDesktopComposeUiTest(width = 500, height = 700) {
            val block = TextBlockUiState()
            setContent {
                MaterialTheme {
                    MainEditorContent(
                        uiState =
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
                    )
                }
            }
            onNodeWithTag("editor_text_input").assertIsFocused()
            onNodeWithTag("add_to_entry").assertIsDisplayed()
        }

    @Test
    fun `selecting and adding keeps the entry sequence visible`() =
        runDesktopComposeUiTest(width = 700, height = 900) {
            val first = TextBlockUiState(content = "First memory")
            val second = TextBlockUiState(content = "Second memory")
            val blocks = mutableStateOf(listOf(first, second))
            val selected = mutableStateOf(first.id)
            setContent {
                MaterialTheme {
                    MainEditorContent(
                        uiState =
                            BlocksUiState(
                                blocks = blocks.value,
                                expandedBlockId = selected.value,
                                availableJournals = emptyList(),
                                selectedJournalIds = emptyList(),
                                onBlockFocused = { selected.value = it },
                                onJournalSelectionChanged = {},
                                onUpdateBlock = {},
                                onCreateBlock = { _, id -> TextBlockUiState(id = id).also { blocks.value = blocks.value + it } },
                                onDeleteBlock = {},
                            ),
                        shouldReturnToPickerOnBack = false,
                        onDismissExpanded = {},
                    )
                }
            }
            onNodeWithText("Second memory").assertIsDisplayed().performClick()
            onNodeWithText("First memory").assertIsDisplayed()
            onNodeWithText("Add to entry").performClick()
            onNodeWithTag("add_memory_TEXT").performClick()
            assertEquals(3, blocks.value.size)
            onNodeWithText("Add to entry").assertIsDisplayed()
        }
}
