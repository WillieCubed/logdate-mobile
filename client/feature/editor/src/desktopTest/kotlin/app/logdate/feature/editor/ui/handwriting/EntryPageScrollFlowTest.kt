package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.datastore.EntryStartMode
import org.jetbrains.skia.Image
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Continuous event-driven capture: no state jumps or separately seeded endpoints. */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class EntryPageScrollFlowTest(
    private val mode: EntryStartMode,
) {
    @Test
    fun scrollCreateReturnAndOpenEarlier() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            setContent { EntryPageStudy(defaultMode = mode) }
            waitForIdle()
            val output = File("build/reports/entry-page-scroll/${mode.name.lowercase()}").apply { mkdirs() }
            var index = 0

            fun frames(count: Int) {
                repeat(count) {
                    mainClock.advanceTimeBy(40)
                    File(output, "frame-${(index++).toString().padStart(4, '0')}.png").writeBytes(
                        Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()!!.bytes,
                    )
                }
            }

            fun range() = onNodeWithTag("entry_page_scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

            fun drag(
                distance: Float,
                steps: Int,
                hold: Int = 0,
                armed: Boolean = false,
            ) {
                onNodeWithTag("entry_page_scroll").performTouchInput {
                    down(Offset(width - 6f, if (distance < 0f) height - 30f else 30f))
                }
                repeat(steps) {
                    onNodeWithTag("entry_page_scroll").performTouchInput { moveBy(Offset(0f, distance / steps), delayMillis = 40) }
                    frames(1)
                }
                frames(hold)
                if (armed) {
                    val progress = onNodeWithTag("study_edge_pull").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
                    assertEquals(1f, progress.current, 0.01f)
                    onNodeWithTag("study_pull_hint").assertDoesNotExist()
                }
                onNodeWithTag("entry_page_scroll").performTouchInput {
                    advanceEventTime(160)
                    up()
                }
            }

            mainClock.autoAdvance = false
            frames(18)
            // Reach the creation controls without crossing the creation detent.
            drag(-(range().maxValue() + 32f), steps = 18)
            frames(14)
            val endPosition = range().value()
            assertTrue(endPosition > 0f)
            assertEquals(range().maxValue(), endPosition, 1f, "The initial scroll must reach the creation area before pulling")
            onNodeWithTag("entry_page_creation").assertExists()
            // A second deliberate pull arms the selected default; release creates once.
            drag(-240f, steps = 16, hold = 12, armed = true)
            frames(28)
            onNodeWithTag("entry_mode_focus").assertExists()
            if (mode == EntryStartMode.HANDWRITING) onNodeWithTag("handwriting_canvas").assertExists()
            onNodeWithContentDescription("Return to entry").performClick()
            frames(24)
            assertEquals(endPosition, range().value(), 1f, "Focus return must retain the page's scroll position")
            frames(12)
            // Scroll back through the same page, then pull beyond its leading edge.
            drag(range().value() + 32f, steps = 18)
            frames(12)
            assertEquals(0f, range().value(), 1f, "The return scroll must reach the top before pulling")
            drag(240f, steps = 16, hold = 12, armed = true)
            frames(24)
            onNodeWithTag("entry_day_browser").assertExists()
            onNodeWithText("Took the long way home.\n\nThe light was too good to miss.").assertDoesNotExist()
            frames(18)
            onNodeWithContentDescription("Return to writing").performClick()
            frames(20)
            mainClock.autoAdvance = true
            File(output, "frames.txt").writeText(index.toString())
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = listOf(arrayOf(EntryStartMode.TEXT), arrayOf(EntryStartMode.HANDWRITING))
    }
}
