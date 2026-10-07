package app.logdate.feature.editor.ui.blocks

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.state.BlocksUiState
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EntryAddGestureTest {
    @Test
    fun `a short downward pull keeps Add choices open after release`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            val block = TextBlockUiState(content = "A memory")
            setContent {
                MaterialTheme {
                    EntryMemorySequence(
                        uiState =
                            BlocksUiState(
                                blocks = listOf(block),
                                expandedBlockId = null,
                                availableJournals = emptyList(),
                                selectedJournalIds = emptyList(),
                                onBlockFocused = {},
                                onJournalSelectionChanged = {},
                                onUpdateBlock = {},
                                onCreateBlock = { _, _ -> block },
                                onDeleteBlock = {},
                            ),
                        listState = rememberLazyListState(),
                        onAudioResolverReady = { _, _ -> },
                    )
                }
            }
            onNodeWithTag("add_to_entry").performClick()
            waitForIdle()
            onNodeWithTag("add_to_entry").performTouchInput {
                down(center)
                moveBy(Offset(0f, 64.dp.toPx()), delayMillis = 100)
                up()
            }
            waitForIdle()
            onNodeWithTag("add_close").assertIsDisplayed().assertIsEnabled()
            onNodeWithTag("add_memory_AUDIO").assertIsDisplayed()
            saveScreenshot("short-downward-pull.png", Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()))
        }

    @Test
    fun `pulling Add open after adding memories keeps the current footer visible`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            val blocks = mutableStateOf(listOf(TextBlockUiState(content = "First memory")))
            val listState = LazyListState()
            setContent {
                MaterialTheme {
                    EntryMemorySequence(
                        uiState =
                            BlocksUiState(
                                blocks = blocks.value,
                                expandedBlockId = null,
                                availableJournals = emptyList(),
                                selectedJournalIds = emptyList(),
                                onBlockFocused = {},
                                onJournalSelectionChanged = {},
                                onUpdateBlock = {},
                                onCreateBlock = { _, _ -> blocks.value.first() },
                                onDeleteBlock = {},
                            ),
                        listState = listState,
                        onAudioResolverReady = { _, _ -> },
                    )
                }
            }
            waitForIdle()
            runOnIdle {
                blocks.value = blocks.value + List(7) { TextBlockUiState(content = "Added memory $it\n".repeat(8)) }
            }
            waitForIdle()
            onNodeWithTag("editor_block_list").performScrollToIndex(blocks.value.size)
            onNodeWithTag("add_to_entry").assertIsDisplayed()
            onNodeWithTag("editor_block_list").performTouchInput {
                down(Offset(24.dp.toPx(), 420.dp.toPx()))
                moveBy(Offset(0f, -160.dp.toPx()), delayMillis = 200)
                up()
            }
            waitForIdle()
            saveScreenshot("dynamic-memories-pull.png", Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()))
            onNodeWithTag("add_close").assertIsDisplayed()
            onNodeWithTag("add_memory_AUDIO").assertIsDisplayed()
            onNodeWithTag("add_memory_VIDEO").assertIsDisplayed()
            onNodeWithTag("add_memory_CAMERA").assertIsDisplayed()
            val viewport = onRoot().getUnclippedBoundsInRoot()
            listOf("TEXT", "IMAGE", "AUDIO", "VIDEO", "CAMERA").forEach { type ->
                val bounds = onNodeWithTag("add_memory_$type").getUnclippedBoundsInRoot()
                assertTrue(bounds.top >= viewport.top && bounds.bottom <= viewport.bottom, "$type must be fully inside the viewport")
            }
            assertTrue(listState.layoutInfo.visibleItemsInfo.any { it.key == "add_memory_footer" })
        }

    @Test
    fun `scrolling entry content downward is not intercepted by an open Add panel`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            val blocks = List(8) { TextBlockUiState(content = "Memory $it\n".repeat(8)) }
            val listState = LazyListState(firstVisibleItemIndex = 7)
            setContent {
                MaterialTheme {
                    EntryMemorySequence(
                        uiState =
                            BlocksUiState(
                                blocks = blocks,
                                expandedBlockId = null,
                                availableJournals = emptyList(),
                                selectedJournalIds = emptyList(),
                                onBlockFocused = {},
                                onJournalSelectionChanged = {},
                                onUpdateBlock = {},
                                onCreateBlock = { _, _ -> blocks.first() },
                                onDeleteBlock = {},
                            ),
                        listState = listState,
                        onAudioResolverReady = { _, _ -> },
                    )
                }
            }
            onNodeWithTag("add_to_entry").performClick()
            waitForIdle()
            val before = listState.firstVisibleItemIndex * 100000 + listState.firstVisibleItemScrollOffset
            onNodeWithTag("editor_block_list").performTouchInput {
                down(center)
                moveBy(Offset(0f, 100.dp.toPx()), delayMillis = 200)
                up()
            }
            waitForIdle()
            val after = listState.firstVisibleItemIndex * 100000 + listState.firstVisibleItemScrollOffset
            assertTrue(after < before, "Dragging entry content should scroll toward earlier memories")
        }

    private fun saveScreenshot(
        name: String,
        image: Image,
    ) {
        val directory = File(System.getProperty("logdate.review.screenshots", "build/reports/editor-screenshots"))
        directory.mkdirs()
        File(directory, name).writeBytes(requireNotNull(image.encodeToData()).bytes)
    }
}
