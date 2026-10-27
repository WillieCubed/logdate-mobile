package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EntryFocusAdaptiveTest {
    @Test
    fun `every block type can focus and return to its overview item`() {
        val audio = AudioBlockUiState()
        val blocks =
            listOf(
                TextBlockUiState(content = "One entry, two views."),
                HandwritingBlockUiState(page = sampleInk()),
                ImageBlockUiState(uri = samplePhotoUri()),
                CameraBlockUiState(uri = samplePhotoUri()),
                VideoBlockUiState(),
                audio,
            )
        blocks.forEach { block ->
            runDesktopComposeUiTest(width = 360, height = 800) {
                setContent {
                    LogDateTheme(dynamicColor = false) {
                        EntryStartPrototype(
                            initialBlocks = listOf(block),
                            focusAnyBlock = true,
                            initialFocusedBlockId = block.id,
                            previewPresentations = mapOf(recorderFixture(audio)),
                        )
                    }
                }
                onNodeWithTag("focused_block_${block.id}").assertExists()
                onNodeWithTag("entry_mode_focus").assertIsDisplayed()
                onNodeWithContentDescription("Return to entry").performClick()
                onNodeWithTag("entry_mode_overview").assertIsDisplayed()
                onNodeWithTag("memory_block_${block.id}").assertExists()
                onNodeWithTag("focus_block_${block.id}").assertExists()
            }
        }
    }

    @Test
    fun `book overview header stays in the same safe pane as its entry`() =
        runDesktopComposeUiTest(width = 1000, height = 800) {
            setContent {
                provideFoldableLayoutInfo(foldFixture(FoldablePosture.Book, 1000, 800)) {
                    LogDateTheme(dynamicColor = false) { StoriesEntryPrototype(initialPage = sampleInk()) }
                }
            }
            val header = onNodeWithTag("entry_mode_overview").fetchSemanticsNode().boundsInRoot
            assertTrue(header.right <= 488f, "Header crosses the separating hinge: $header")
        }

    @Test
    fun `record action stays bounded in a wide focus window`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val audio = AudioBlockUiState()
            setContent { LogDateTheme(dynamicColor = false) { recorderFixture(audio).second.focused() } }
            val action = onNode(hasText("Record") and hasClickAction()).fetchSemanticsNode().boundsInRoot
            assertTrue(action.width <= 360f, "Record action stretches across the window: $action")
        }

    @Test
    fun `resizing through phone landscape and tablet keeps the focused ink and strokes`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            var size by mutableStateOf(IntSize(360, 800))
            val ink = HandwritingBlockUiState(page = sampleInk())
            var latest: List<EntryBlockUiState> = emptyList()
            setContent {
                LogDateTheme(dynamicColor = false) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.size(size.width.dp, size.height.dp)) {
                            EntryStartPrototype(
                                initialBlocks = listOf(ink),
                                focusAnyBlock = true,
                                initialFocusedBlockId = ink.id,
                                onBlocksChanged = { latest = it },
                            )
                        }
                    }
                }
            }
            listOf(IntSize(360, 800), IntSize(800, 360), IntSize(1280, 800), IntSize(600, 900)).forEach { next ->
                runOnIdle { size = next }
                onNodeWithTag("focused_block_${ink.id}").assertExists()
                onNodeWithTag("handwriting_canvas").assertExists()
                val drawing = onNodeWithTag("handwriting_drawing_area").fetchSemanticsNode().boundsInRoot
                assertEquals(next.width.toFloat(), drawing.width, 1f)
                assertEquals(listOf(ink), latest)
            }
        }
}
