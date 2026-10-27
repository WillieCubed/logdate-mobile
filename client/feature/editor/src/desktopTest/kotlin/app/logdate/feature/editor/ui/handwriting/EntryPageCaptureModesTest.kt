package app.logdate.feature.editor.ui.handwriting

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class EntryPageCaptureModesTest(
    private val direction: LayoutDirection,
) {
    @Test
    fun barSwipesReuseTheSameTextAndInkBlocks() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            val text = TextBlockUiState(content = "Keep this thought intact.")
            val ink = HandwritingBlockUiState(page = entryPageStudyInk())
            var blocks: List<EntryBlockUiState> = emptyList()
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    EntryPageStudy(initialBlocks = listOf(text, ink), onBlocksChanged = { blocks = it })
                }
            }
            onNodeWithTag("memory_block_${text.id}").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("entry_capture_tools").performTouchInput {
                if (direction == LayoutDirection.Ltr) swipeLeft() else swipeRight()
            }
            waitForIdle()
            onNodeWithTag("handwriting_canvas").assertExists()
            onNodeWithTag("entry_capture_tools").performTouchInput {
                if (direction == LayoutDirection.Ltr) swipeRight() else swipeLeft()
            }
            waitForIdle()
            onNodeWithTag("editor_text_input").assertExists()
            assertEquals(listOf(text.id, ink.id), blocks.map { it.id })
            assertEquals(text.content, blocks.filterIsInstance<TextBlockUiState>().single().content)
            assertEquals(ink.page, blocks.filterIsInstance<HandwritingBlockUiState>().single().page)
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun directions() = listOf(arrayOf(LayoutDirection.Ltr), arrayOf(LayoutDirection.Rtl))
    }
}
