package app.logdate.ui.workspace

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkspaceEntryPullInterruptionTest {
    @Test
    fun renewedPullContinuesFromThePaintedReturnPositionWithinOnePixel() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            var visits = 0
            setContent {
                LogDateTheme {
                    WorkspaceEntryPull(onCreate = { visits++ }) {
                        LazyColumn(Modifier.fillMaxSize().testTag("timeline")) {
                            item { Text("Memory", Modifier.height(64.dp)) }
                        }
                    }
                }
            }
            waitForIdle()
            val timeline = onNodeWithTag("timeline")

            fun paintedTop(): Float = timeline.fetchSemanticsNode().boundsInRoot.top
            val restingTop = paintedTop()
            mainClock.autoAdvance = false
            try {
                timeline.performTouchInput {
                    down(Offset(center.x, 180f))
                    moveBy(Offset(0f, 110f), 250)
                }
                repeat(3) { mainClock.advanceTimeByFrame() }
                assertTrue(paintedTop() > restingTop + 20f, "Fixture must have a visible, uncommitted pull: $restingTop -> ${paintedTop()}")
                timeline.performTouchInput { up() }
                mainClock.advanceTimeBy(32)
                val beforeTakeover = paintedTop()
                assertTrue(beforeTakeover > restingTop + 8f, "The return must still be visibly in flight")

                // The clock is held between this snapshot and the same down/move frame.
                // 32 px crosses touch slop, but cannot explain a backwards jump to zero.
                timeline.performTouchInput {
                    down(Offset(center.x, 180f))
                    moveBy(Offset(0f, 32f), 16)
                }
                repeat(3) { mainClock.advanceTimeByFrame() }
                val afterTakeover = paintedTop()
                assertTrue(
                    afterTakeover >= beforeTakeover - 1f,
                    "New drag must start at the painted position: $beforeTakeover -> $afterTakeover",
                )
                assertTrue(
                    afterTakeover <= beforeTakeover + 32f * 0.6f + 1f,
                    "First movement must remain bounded by the existing pull resistance",
                )
                assertEquals(0, visits)
                timeline.performTouchInput { cancel() }
            } finally {
                mainClock.autoAdvance = true
            }
            waitForIdle()
            assertEquals(0, visits)
        }
}
