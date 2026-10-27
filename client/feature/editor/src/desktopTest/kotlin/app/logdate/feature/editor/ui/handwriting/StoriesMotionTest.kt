package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class StoriesMotionTest {
    @Test
    fun `record the native canvas focus and return`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            setContent { LogDateTheme(dynamicColor = false) { StoriesEntryPrototype(initialPage = sampleInk()) } }
            val directory = File("build/reports/stories-motion").apply { mkdirs() }
            var index = 0

            fun frame() {
                File(directory, "frame-${(index++).toString().padStart(3, '0')}.png")
                    .writeBytes(
                        Image.makeFromBitmap(onNodeWithTag("stories_editor").captureToImage().asSkiaBitmap()).encodeToData()!!.bytes,
                    )
            }

            fun advanceFrames(count: Int) {
                repeat(count) {
                    mainClock.advanceTimeBy(16)
                    frame()
                }
            }
            mainClock.autoAdvance = false
            advanceFrames(24)
            onNodeWithTag("handwriting_preview", useUnmergedTree = true).performClick()
            advanceFrames(42)
            advanceFrames(48)
            onNodeWithContentDescription("Return to entry").performClick()
            advanceFrames(42)
            advanceFrames(24)
            onNodeWithTag("handwriting_preview", useUnmergedTree = true).assertExists()
        }
}
