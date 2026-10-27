package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.editor.ui.BlockFocusProgressKey
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import org.jetbrains.skia.Image
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.math.abs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class EntryPageInterruptedMotionTest(
    private val handwriting: Boolean,
) {
    @Test
    fun surfaceCanReverseTwiceWithoutJumping() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            val ink =
                if (handwriting) {
                    HandwritingBlockUiState(
                        page = entryPageStudyInk(),
                    )
                } else {
                    TextBlockUiState(content = "Took the long way home.\n\nThe light was too good to miss.")
                }
            setContent { EntryPageStudy(initialBlocks = listOf(ink)) }
            waitForIdle()
            val output =
                File(
                    System.getenv("DISK_JANITOR_ARTIFACT_ROOT") ?: "build/reports",
                    "interrupted-focus/${if (handwriting) "ink" else "text"}",
                ).apply {
                    mkdirs()
                }
            mainClock.autoAdvance = false
            var frame = 0
            var previousProgress = 0f
            File(output, "trace.txt").writeText("")
            var previous = onRoot().captureToImage()

            fun frames(count: Int) {
                repeat(count) {
                    mainClock.advanceTimeBy(16)
                    val current = onRoot().captureToImage()
                    val before = previous.toPixelMap()
                    val after = current.toPixelMap()
                    var changed = 0
                    // Exclude floating controls: inspect the paper and surrounding workspace.
                    for (y in 90 until 710 step 2) {
                        for (x in 0 until 360 step 2) {
                            val a = before[x, y]
                            val b = after[x, y]
                            if (abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > 0.06f) changed++
                        }
                    }
                    val progress =
                        onAllNodes(SemanticsMatcher.keyIsDefined(BlockFocusProgressKey)).fetchSemanticsNodes().map {
                            it.config[BlockFocusProgressKey]
                        }
                    assertTrue(
                        abs(progress.single() - previousProgress) < 0.15f,
                        "Shape progress jumped at $frame: $previousProgress -> $progress",
                    )
                    assertTrue(changed < 18000, "Surface jumped at $frame: $changed sampled pixels")
                    previousProgress = progress.single()
                    File(output, "trace.txt").appendText("$frame $progress changed=$changed\n")
                    File(output, "frame-${(frame++).toString().padStart(4, '0')}.png").writeBytes(
                        Image.makeFromBitmap(current.asSkiaBitmap()).encodeToData()!!.bytes,
                    )
                    previous = current
                }
            }
            frames(8)
            onNodeWithTag("memory_block_${ink.id}").performSemanticsAction(SemanticsActions.OnClick) { it() }
            frames(10)
            onNodeWithContentDescription("Return to entry").performSemanticsAction(SemanticsActions.OnClick) { it() }
            frames(6)
            onNodeWithTag("memory_block_${ink.id}").performSemanticsAction(SemanticsActions.OnClick) { it() }
            frames(60)
            assertTrue(previousProgress == 1f, "Focused paper must keep its square full-screen shape")
            onNodeWithContentDescription("Return to entry").performSemanticsAction(SemanticsActions.OnClick) { it() }
            frames(60)
            onNodeWithTag("entry_mode_overview").assertExists()
            mainClock.autoAdvance = true
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "handwriting={0}")
        fun variants() = listOf(arrayOf(true), arrayOf(false))
    }
}
