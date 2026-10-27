package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class HandwritingStrokeBatchTest {
    @Test
    fun `completed strokes accumulate before the next composition and undo individually`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            val block = HandwritingBlockUiState()
            var latest = block.page
            setContent {
                LogDateTheme(dynamicColor = false) {
                    EntryStartPrototype(
                        initialBlocks = listOf(block),
                        initialFocusedBlockId = block.id,
                        focusAnyBlock = true,
                        onBlocksChanged = { latest = (it.single() as HandwritingBlockUiState).page },
                    )
                }
            }
            onNodeWithContentDescription("Drawing options").performClick()
            onNodeWithText("Draw with touch").performClick()
            onNodeWithTag("handwriting_canvas").performTouchInput {
                repeat(6) { index ->
                    down(Offset(40f + index * 30f, 200f))
                    moveBy(Offset(10f, 30f))
                    up()
                    advanceEventTime(120)
                }
            }
            waitForIdle()
            assertEquals(6, latest.strokes.size)
            mainClock.autoAdvance = false
            onNodeWithTag("handwriting_canvas").performTouchInput {
                down(Offset(40f, 300f))
                moveBy(Offset(10f, 30f))
                up()
            }
            mainClock.advanceTimeBy(80)
            onNodeWithTag("handwriting_canvas").assertIsEnabled()
            mainClock.autoAdvance = true
            onNodeWithContentDescription("Undo").performClick()
            waitForIdle()
            assertEquals(6, latest.strokes.size)
            onNodeWithContentDescription("Redo").performClick()
            waitForIdle()
            assertEquals(7, latest.strokes.size)
        }
}
