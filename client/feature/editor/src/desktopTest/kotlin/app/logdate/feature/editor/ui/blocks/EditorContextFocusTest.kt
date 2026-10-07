package app.logdate.feature.editor.ui.blocks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.common.NoteEditorToolbar
import app.logdate.feature.editor.ui.content.EditorBottomContent
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.shared.model.Journal
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class EditorContextFocusTest {
    @Test
    fun `phone entry and journal surface share both edges`() = verifySurfaceEdges(Size(390f, 780f))

    @Test
    fun `tablet entry and journal surface share both edges`() = verifySurfaceEdges(Size(1280f, 800f))

    @Test
    fun `medium entry and journal surface share both edges`() = verifySurfaceEdges(Size(700f, 780f))

    private fun verifySurfaceEdges(size: Size) =
        runSkikoComposeUiTest(size = size) {
            val block = TextBlockUiState(content = "A memory already in this entry.")
            val journal = Journal(title = "Personal")
            setContent {
                LogDateTheme(dynamicColor = false) {
                    ImmersiveEditorLayout(
                        topBarContent = { NoteEditorToolbar({}, {}, {}) },
                        bottomContent = {
                            EditorBottomContent(
                                listOf(journal),
                                listOf(journal.id),
                                {},
                                false,
                                {},
                                modifier = Modifier.fillMaxWidth().testTag("journal_surface_bounds"),
                            )
                        },
                        editorContent = {
                            EntryMemorySequence(
                                uiState =
                                    BlocksUiState(
                                        blocks = listOf(block),
                                        availableJournals = listOf(journal),
                                        selectedJournalIds = listOf(journal.id),
                                        onBlockFocused = {},
                                        onJournalSelectionChanged = {},
                                        onUpdateBlock = {},
                                        onCreateBlock = { _, _ -> block },
                                        onDeleteBlock = {},
                                    ),
                                listState = rememberLazyListState(),
                                onAudioResolverReady = { _, _ -> },
                            )
                        },
                    )
                }
            }
            waitForIdle()
            val memory = onNodeWithTag("memory_block_${block.id}").getUnclippedBoundsInRoot()
            val add = onNodeWithTag("add_memory_surface").getUnclippedBoundsInRoot()
            val selector = onNodeWithTag("journal_surface_bounds").getUnclippedBoundsInRoot()
            assertEquals(memory.left, add.left)
            assertEquals(memory.right, add.right)
            assertEquals(memory.left, selector.left, "Journal selector must share the entry's left edge")
            assertEquals(memory.right, selector.right, "Journal selector must share the entry's right edge")
        }

    @Test
    fun `recording hides the real Add surface and restores it collapsed without changing journal selection`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            val recording = mutableStateOf(false)
            val block = TextBlockUiState(content = "A memory already in this entry.")
            val journal = Journal(title = "Personal")
            var selectionChanges = 0
            setContent {
                LogDateTheme(dynamicColor = false) {
                    ImmersiveEditorLayout(
                        isAudioRecordingActive = recording.value,
                        topBarContent = { NoteEditorToolbar({}, {}, {}) },
                        bottomContent = {
                            EditorBottomContent(listOf(journal), listOf(journal.id), { selectionChanges++ }, false, {})
                        },
                        editorContent = {
                            EntryMemorySequence(
                                uiState =
                                    BlocksUiState(
                                        blocks = listOf(block),
                                        availableJournals = listOf(journal),
                                        selectedJournalIds = listOf(journal.id),
                                        onBlockFocused = {},
                                        onJournalSelectionChanged = { selectionChanges++ },
                                        onUpdateBlock = {},
                                        onCreateBlock = { _, _ -> block },
                                        onDeleteBlock = {},
                                    ),
                                listState = rememberLazyListState(),
                                onAudioResolverReady = { _, _ -> },
                            )
                        },
                    )
                }
            }
            onNodeWithTag("add_to_entry").performClick()
            waitForIdle()
            onNodeWithTag("add_close").assertIsDisplayed()
            runOnIdle { recording.value = true }
            waitForIdle()
            onNodeWithTag("add_to_entry").assertDoesNotExist()
            onNodeWithText("Personal").assertDoesNotExist()
            onNodeWithText("A memory already in this entry.").assertIsDisplayed()
            runOnIdle { recording.value = false }
            waitForIdle()
            onNodeWithTag("add_to_entry").assertIsDisplayed()
            onNodeWithText("Personal").assertIsDisplayed()
            onNodeWithTag("add_close").assertIsNotDisplayed()
            assertEquals(0, selectionChanges)
            val memory = onNodeWithTag("memory_block_${block.id}").getUnclippedBoundsInRoot()
            val add = onNodeWithTag("add_memory_surface").getUnclippedBoundsInRoot()
            assertEquals(memory.left, add.left)
            assertEquals(memory.right, add.right)
            assertEquals(16.dp, add.top - memory.bottom)
            val directory = File(System.getProperty("logdate.review.screenshots", "build/reports/editor-screenshots"))
            directory.mkdirs()
            File(directory, "editor-context-restored.png").writeBytes(
                requireNotNull(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()).bytes,
            )
        }
}
