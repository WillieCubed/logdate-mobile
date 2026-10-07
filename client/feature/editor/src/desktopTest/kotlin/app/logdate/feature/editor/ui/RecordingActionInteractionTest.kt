package app.logdate.feature.editor.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.logdate.feature.editor.ui.audio.ActiveRecordingDisplay
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class RecordingActionInteractionTest {
    @Test
    fun `focus and press provide visible feedback without activating Finish early`() =
        runSkikoComposeUiTest(size = Size(390f, 300f)) {
            var finishes = 0
            mainClock.autoAdvance = false
            setContent {
                LogDateTheme(dynamicColor = false) {
                    ActiveRecordingDisplay(
                        listOf(.2f, .4f),
                        42.seconds,
                        {},
                        {},
                        { finishes++ },
                        transcriptionText = "A memory worth keeping.",
                    )
                }
            }
            mainClock.advanceTimeBy(300)
            waitForIdle()
            val finish = onNodeWithText("Finish")
            val idle = finish.captureToImage().toPixelMap()

            fun save(name: String) {
                val directory = File(System.getProperty("logdate.review.screenshots", "build/reports/editor-screenshots"))
                directory.mkdirs()
                File(directory, name).writeBytes(
                    requireNotNull(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()).bytes,
                )
            }
            save("recording-actions-idle.png")
            finish.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            mainClock.advanceTimeBy(300)
            waitForIdle()
            finish.assertIsFocused()
            val focused = finish.captureToImage().toPixelMap()
            assertNotEquals(idle[idle.width / 2, 4], focused[focused.width / 2, 4], "Focus must change the visible state layer")
            save("recording-actions-focused.png")
            assertEquals(0, finishes)
            finish.performTouchInput { down(center) }
            mainClock.advanceTimeBy(300)
            waitForIdle()
            val pressed = finish.captureToImage().toPixelMap()
            assertNotEquals(focused[2, 2], pressed[2, 2], "Press must visibly tighten the rounded corners")
            save("recording-actions-pressed.png")
            assertEquals(0, finishes)
            finish.performTouchInput { up() }
            mainClock.advanceTimeBy(300)
            waitForIdle()
            assertEquals(1, finishes)
        }
}
