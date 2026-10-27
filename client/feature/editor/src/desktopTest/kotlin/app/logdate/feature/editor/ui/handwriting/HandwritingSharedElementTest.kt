package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkPoint
import app.logdate.feature.postcards.model.InkTool
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class HandwritingSharedElementTest {
    @Test
    fun `shared ink stays fully opaque in both directions`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            File("build/reports/handwriting-prototype/shared-trace.txt").apply {
                parentFile.mkdirs()
                writeText("")
            }
            val page =
                HandwritingPage(
                    strokes =
                        listOf(
                            CanvasElement.Ink(
                                Uuid.random(),
                                InkTool.PEN,
                                "#D900D9",
                                16f,
                                listOf(InkPoint(100f, 240f), InkPoint(400f, 240f)),
                            ),
                        ),
                )
            setContent {
                LogDateTheme(
                    dynamicColor = false,
                ) { EntryStartPrototype(initialPage = page, initialText = "An ordinary text block.") }
            }

            fun assertOpaqueInk() {
                val frame = onNodeWithTag("handwriting_editor").captureToImage()
                File("build/reports/handwriting-prototype/shared-mid-frame.png")
                    .apply { parentFile.mkdirs() }
                    .writeBytes(Image.makeFromBitmap(frame.asSkiaBitmap()).encodeToData()!!.bytes)
                val pixels = frame.toPixelMap()
                var darkestGreen = 1f
                for (y in 0 until pixels.height) {
                    for (x in 0 until pixels.width) {
                        val color = pixels[x, y]
                        if (color.red > .7f && color.blue > .7f && color.green < darkestGreen) darkestGreen = color.green
                    }
                }
                assertTrue(darkestGreen < .05f, "Original ink must not fade during the morph: green=$darkestGreen")
            }
            mainClock.autoAdvance = false
            onNodeWithTag("handwriting_preview", useUnmergedTree = true).performClick()
            mainClock.advanceTimeBy(64)
            assertOpaqueInk()
            mainClock.advanceTimeBy(800)
            onNodeWithContentDescription("Back").performClick()
            mainClock.advanceTimeBy(64)
            assertOpaqueInk()
        }

    @Test
    fun `focused paper background reaches all edges on tablet`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                LogDateTheme(darkTheme = true, dynamicColor = false) {
                    EntryStartPrototype(initialPage = HandwritingPage(), initiallyFocused = true)
                }
            }
            val pixels = onNodeWithTag("handwriting_editor").captureToImage().toPixelMap()
            for ((x, y) in listOf(0 to 0, 1279 to 0, 0 to 799, 1279 to 799)) {
                val color = pixels[x, y]
                assertTrue(color.red > .99f && color.green > .99f && color.blue > .99f, "Paper must reach edge ($x, $y): $color")
            }
        }
}
