package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EntryCapturePositionTest {
    @Test
    fun longPageKeepsCaptureInsideViewportWhileShortPageFollowsItsAnchor() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val overview = EntryPageOverviewState(false)
            val block = TextBlockUiState(content = "A thought")
            var captures = 0
            setContent {
                LogDateTheme(dynamicColor = false) {
                    EntryPageChrome(
                        EditorState(blocks = listOf(block)),
                        overview,
                        EntryStartMode.TEXT,
                        {},
                        {},
                        { captures++ },
                        header = {},
                    ) {}
                }
            }
            for ((anchor, expected) in listOf(280f to 280f, 1000f to 720f, 700f to 700f)) {
                runOnIdle { overview.captureBounds = Rect(0f, anchor, 400f, anchor + 80f) }
                val bounds = onNodeWithTag("entry_creation_actions").getUnclippedBoundsInRoot()
                assertEquals(expected, bounds.top.value, 1f, "Capture must follow the page until its safe bottom limit")
                assertTrue(bounds.bottom.value <= 800f, "Long page moved capture outside the viewport: $bounds")
            }
            onNodeWithText("Add to entry").performTouchInput { click() }
            assertEquals(1, captures, "Bounded capture controls must still receive physical pointer input")
        }
}
