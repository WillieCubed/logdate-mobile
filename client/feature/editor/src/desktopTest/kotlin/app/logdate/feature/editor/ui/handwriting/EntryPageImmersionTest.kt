package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class EntryPageImmersionTest {
    @Test
    fun `earlier browsing leaves the draft and returns without replacing it`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            setContent { EntryPageStudy() }
            onNodeWithTag("entry_page_scroll").performTouchInput { swipeDown() }
            waitForIdle()
            onNodeWithTag("entry_day_browser").assertExists()
            onNodeWithText("Took the long way home.\n\nThe light was too good to miss.").assertDoesNotExist()
            onNodeWithContentDescription("Return to writing").performClick()
            waitForIdle()
            onNodeWithTag("entry_page_scroll").assertExists()
            onNodeWithText("Took the long way home.\n\nThe light was too good to miss.").assertExists()
        }

    @Test
    fun `the page reaches the window edges and uses one capture selector across focus`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            setContent { EntryPageStudy() }
            val page = onNodeWithTag("entry_page_scroll").fetchSemanticsNode().boundsInRoot
            assertEquals(0f, page.left)
            assertEquals(360f, page.right)
            assertEquals(0f, page.top)
            assertEquals(800f, page.bottom)
            onAllNodesWithTag("entry_capture_tools").assertCountEquals(1)
            onNodeWithTag("handwriting_preview").performClick()
            waitForIdle()
            onAllNodesWithTag("entry_capture_tools").assertCountEquals(1)
            onNodeWithText("Focus").assertDoesNotExist()
            onNodeWithText("Release to handwrite").assertDoesNotExist()
        }
}
