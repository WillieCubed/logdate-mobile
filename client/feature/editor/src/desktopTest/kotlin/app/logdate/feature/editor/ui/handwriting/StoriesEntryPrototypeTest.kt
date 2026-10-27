package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StoriesEntryPrototypeTest {
    @Test
    fun `entry context is visible in overview and handwriting expands back to the same entry`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            setContent { LogDateTheme(dynamicColor = false) { StoriesEntryPrototype(initialPage = sampleInk()) } }
            onNodeWithTag("entry_mode_overview").assertIsDisplayed()
            onNodeWithText("A slower start").assertIsDisplayed()
            onNodeWithText("Done").assertDoesNotExist()
            onNodeWithContentDescription("Save entry").assertIsNotEnabled()
            onNodeWithTag("handwriting_preview", useUnmergedTree = true).performClick()
            onNodeWithTag("entry_mode_focus").assertIsDisplayed()
            onNodeWithTag("handwriting_canvas").assertExists()
            onNodeWithTag("earlier_capture_context").assertDoesNotExist()
            onNodeWithContentDescription("Return to entry").performClick()
            onNodeWithTag("entry_mode_overview").assertIsDisplayed()
            onNodeWithText("A slower start").assertIsDisplayed()
        }

    @Test
    fun `text and handwriting keep their identities and edits across focus and overview`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            val text = TextBlockUiState(content = "A good afternoon.")
            val ink = HandwritingBlockUiState(page = sampleInk())
            var latest: List<EntryBlockUiState> = emptyList()
            setContent {
                LogDateTheme(dynamicColor = false) {
                    EntryStartPrototype(
                        initialBlocks = listOf(text, ink),
                        initialFocusedBlockId = text.id,
                        focusAnyBlock = true,
                        onBlocksChanged = { latest = it },
                    )
                }
            }
            onNodeWithTag("focused_block_${text.id}").assertExists()
            onNodeWithTag("editor_text_input").performTextInput(" Still here.")
            onNodeWithContentDescription("Return to entry").performClick()
            onNodeWithTag("editor_block_list").performScrollToNode(hasTestTag("focus_block_${ink.id}"))
            onNodeWithTag("focus_block_${ink.id}").performClick()
            onNodeWithTag("focused_block_${ink.id}").assertExists()
            onNodeWithContentDescription("Return to entry").performClick()
            onNodeWithTag("editor_block_list").performScrollToNode(hasTestTag("focus_block_${text.id}"))
            onNodeWithTag("focus_block_${text.id}").performClick()
            onNodeWithTag("editor_text_input").assertTextEquals("A good afternoon. Still here.")
            assertEquals(listOf(text.id, ink.id), latest.map { it.id })
            assertEquals(ink.page, latest.filterIsInstance<HandwritingBlockUiState>().single().page)
        }

    @Test
    fun `swiping the focus header switches tools without replacing existing strokes`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            val text = TextBlockUiState(content = "Keep this thought.")
            val ink = HandwritingBlockUiState(page = sampleInk())
            var latest: List<EntryBlockUiState> = emptyList()
            setContent {
                LogDateTheme(dynamicColor = false) {
                    EntryStartPrototype(
                        initialBlocks = listOf(text, ink),
                        initialFocusedBlockId = text.id,
                        focusAnyBlock = true,
                        onBlocksChanged = { latest = it },
                    )
                }
            }
            onNodeWithTag("entry_mode_focus").performTouchInput { swipeLeft() }
            onNodeWithTag("focused_block_${ink.id}").assertExists()
            onNodeWithTag("handwriting_canvas").assertExists()
            assertEquals(listOf(text, ink), latest)
        }
}
