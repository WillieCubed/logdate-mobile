package app.logdate.feature.editor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.blocks.MemoryBlockSurface
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import app.logdate.feature.editor.ui.state.BlocksUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MemoryBlockSemanticsTest {
    @Test
    fun `every memory type offers the same edit and remove actions`() {
        listOf(TextBlockUiState(), ImageBlockUiState(), AudioBlockUiState(), VideoBlockUiState()).forEach { block ->
            runSkikoComposeUiTest(size = Size(500f, 500f)) {
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
        runSkikoComposeUiTest(size = Size(500f, 700f)) {
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
        runSkikoComposeUiTest(size = Size(700f, 900f)) {
            val first = TextBlockUiState(content = "First memory")
            val second = TextBlockUiState(content = "Second memory")
            val blocks = mutableStateOf(listOf(first, second))
            val selected = mutableStateOf(first.id)
            lateinit var focusManager: FocusManager
            setContent {
                focusManager = LocalFocusManager.current
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
            mainClock.autoAdvance = false
            mainClock.advanceTimeBy(2000)
            runOnIdle { focusManager.clearFocus(force = true) }
            onNodeWithTag("editor_block_list").performScrollToIndex(3)
            mainClock.advanceTimeBy(1000)
            onNodeWithTag("add_to_entry").assertIsDisplayed()
        }

    @Test
    fun `explicit text edit does not reclaim focus after the item is recycled`() =
        runSkikoComposeUiTest(size = Size(390f, 480f)) {
            val blocks = List(4) { TextBlockUiState(content = "Existing memory ${it + 1}") }
            val first = blocks.first()
            val selected = mutableStateOf(first.id)
            lateinit var focusManager: FocusManager
            mainClock.autoAdvance = false
            setContent {
                focusManager = LocalFocusManager.current
                MaterialTheme {
                    MainEditorContent(
                        uiState =
                            BlocksUiState(
                                blocks = blocks,
                                expandedBlockId = selected.value,
                                availableJournals = emptyList(),
                                selectedJournalIds = emptyList(),
                                onBlockFocused = { selected.value = it },
                                onJournalSelectionChanged = {},
                                onUpdateBlock = {},
                                onCreateBlock = { _, _ -> first },
                                onDeleteBlock = {},
                            ),
                        shouldReturnToPickerOnBack = false,
                        onDismissExpanded = {},
                    )
                }
            }
            mainClock.advanceTimeBy(1200)
            waitForIdle()
            onNodeWithTag("block_menu_${first.id}").performClick()
            mainClock.advanceTimeBy(300)
            onNodeWithText("Edit").performClick()
            mainClock.advanceTimeBy(1200)
            waitForIdle()

            val firstField = hasTestTag("editor_text_input") and hasAnyAncestor(hasTestTag("memory_block_${first.id}"))

            fun firstFieldHasFocus(): Boolean {
                val fields = onAllNodes(firstField, useUnmergedTree = true).fetchSemanticsNodes()
                assertEquals(1, fields.size, "The first memory's text field must be composed")
                return fields.single().config[SemanticsProperties.Focused]
            }
            assertTrue(firstFieldHasFocus(), "Explicit Edit must focus the text field")
            runOnIdle { focusManager.clearFocus(force = true) }
            mainClock.advanceTimeBy(300)
            onNodeWithTag("editor_block_list").performScrollToIndex(3)
            mainClock.advanceTimeBy(1200)
            waitForIdle()
            assertEquals(
                0,
                onAllNodesWithTag("memory_block_${first.id}", useUnmergedTree = true).fetchSemanticsNodes().size,
                "Scrolling away must recycle the previously edited memory",
            )
            onNodeWithTag("editor_block_list").performScrollToIndex(0)
            mainClock.advanceTimeBy(1200)
            waitForIdle()
            assertFalse(firstFieldHasFocus(), "Returning to a recycled memory must not replay the consumed Edit request")
        }

    @Test
    fun `focusing a text memory opens a writing surface in the entry`() =
        runSkikoComposeUiTest(size = Size(700f, 900f)) {
            val first = TextBlockUiState(content = "First memory")
            val second = TextBlockUiState(content = "Second memory")
            val selected = mutableStateOf(first.id)
            setContent {
                MaterialTheme {
                    MainEditorContent(
                        uiState =
                            BlocksUiState(
                                blocks = listOf(first, second),
                                expandedBlockId = selected.value,
                                availableJournals = emptyList(),
                                selectedJournalIds = emptyList(),
                                onBlockFocused = { selected.value = it },
                                onJournalSelectionChanged = {},
                                onUpdateBlock = {},
                                onCreateBlock = { _, _ -> first },
                                onDeleteBlock = {},
                            ),
                        shouldReturnToPickerOnBack = false,
                        onDismissExpanded = {},
                    )
                }
            }
            waitForIdle()
            val firstBounds = onNodeWithTag("memory_block_${first.id}").getUnclippedBoundsInRoot()
            val secondBounds = onNodeWithTag("memory_block_${second.id}").getUnclippedBoundsInRoot()
            val firstHeight = firstBounds.bottom - firstBounds.top
            val secondHeight = secondBounds.bottom - secondBounds.top
            assertTrue(firstHeight > secondHeight + 120.dp, "Focused text memory should expand for writing")
            onNodeWithText("Second memory").performClick()
            waitForIdle()
            val focusedSecondBounds = onNodeWithTag("memory_block_${second.id}").getUnclippedBoundsInRoot()
            val focusedSecondHeight = focusedSecondBounds.bottom - focusedSecondBounds.top
            assertTrue(focusedSecondHeight > secondHeight + 120.dp, "Newly focused text memory should expand")
            onAllNodesWithTag("editor_text_input")[1].assertIsFocused()
        }
}
