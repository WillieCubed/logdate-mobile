package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkPoint
import app.logdate.feature.postcards.model.InkTool
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class HandwritingPrototypeTest {
    @Test
    fun `paper focus expands through intermediate bounds and returns to its original block`() = checkPaperMotion(360)

    @Test
    fun `paper focus keeps ink aligned with retained tablet entry context`() = checkPaperMotion(1280, withContext = true)

    private fun checkPaperMotion(
        width: Int,
        withContext: Boolean = false,
    ) = runDesktopComposeUiTest(width = width, height = 800) {
        val page =
            HandwritingPage(
                strokes =
                    listOf(
                        CanvasElement.Ink(
                            Uuid.random(),
                            InkTool.PEN,
                            "#D900D9",
                            8f,
                            listOf(InkPoint(100f, 240f), InkPoint(400f, 240f)),
                        ),
                    ),
            )
        setContent { LogDateTheme(dynamicColor = false) { HandwritingEntryPrototype(initialPage = page) } }

        fun inkBands(): Int {
            val paper = onNodeWithTag("handwriting_focus_surface").getBoundsInRoot()
            val pixels = onNodeWithTag("handwriting_editor").captureToImage().toPixelMap()
            var bands = 0
            var previous = false
            for (y in (paper.top.value.toInt() + 8) until minOf(paper.bottom.value.toInt() - 8, pixels.height)) {
                val ink =
                    (paper.left.value.toInt() + 8 until minOf(paper.right.value.toInt() - 8, pixels.width)).any { x ->
                        val color = pixels[x, y]
                        color.red - color.green > .15f && color.blue - color.green > .15f
                    }
                if (ink && !previous) bands++
                previous = ink
            }
            return bands
        }
        if (withContext) {
            onNodeWithTag("handwriting_preview", useUnmergedTree = true).performClick()
            onNodeWithContentDescription("Drawing options").performClick()
            onNodeWithText("Entry context").performClick()
            onNodeWithContentDescription("Back").performClick()
        }
        val source = onNodeWithTag("handwriting_preview", useUnmergedTree = true).getBoundsInRoot()
        mainClock.autoAdvance = false
        onNodeWithTag("handwriting_preview", useUnmergedTree = true).performClick()
        mainClock.advanceTimeBy(32)
        val start = onNodeWithTag("handwriting_focus_surface").getBoundsInRoot()
        assertTrue(start.top.value >= source.top.value - 40f, "Paper must start at the tapped block: $start / $source")
        mainClock.advanceTimeBy(32)
        assertEquals(1, inkBands(), "Opening must not show a second ghost stroke")
        mainClock.advanceTimeBy(144)
        val opening = onNodeWithTag("handwriting_focus_surface").getBoundsInRoot()
        assertTrue(opening.top.value > 1f && opening.top < source.top, "Paper should travel from its block: $opening / $source")
        assertTrue((opening.bottom - opening.top).value in 260f..780f, "Paper should grow continuously: $opening")
        mainClock.advanceTimeBy(800)
        val focused = onNodeWithTag("handwriting_focus_surface").getBoundsInRoot()
        assertTrue(focused.top.value <= 1f && focused.bottom.value >= 799f)
        onNodeWithContentDescription("Back").performClick()
        mainClock.advanceTimeBy(64)
        assertEquals(1, inkBands(), "Returning must not show a second ghost stroke")
        mainClock.advanceTimeBy(144)
        val closing = onNodeWithTag("handwriting_focus_surface").getBoundsInRoot()
        assertTrue(closing.top.value > 1f && closing.top < source.top, "Paper should return continuously: $closing")
        mainClock.advanceTimeBy(800)
        val returned = onNodeWithTag("handwriting_preview", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(source.top.value, returned.top.value, absoluteTolerance = 1f)
        assertEquals(source.left.value, returned.left.value, absoluteTolerance = 1f)
    }

    @Test
    fun `handwriting opens from the paper without extra buttons or prototype copy`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            setContent { LogDateTheme(dynamicColor = false) { HandwritingEntryPrototype() } }
            onNodeWithText("Continue writing").assertDoesNotExist()
            onNodeWithText("Add handwriting").assertDoesNotExist()
            onNodeWithText("Practice entry. Separate from your journal.").assertDoesNotExist()
            onNodeWithTag("handwriting_preview", useUnmergedTree = true).performClick()
            onNodeWithTag("handwriting_canvas").assertExists()
            onNodeWithContentDescription("Back").performClick()
            onNodeWithTag("handwriting_preview", useUnmergedTree = true).assertExists()
        }

    @Test
    fun `reopened page can be panned clear of top controls`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            val page =
                HandwritingPage(
                    strokes =
                        listOf(
                            CanvasElement.Ink(
                                Uuid.random(),
                                InkTool.PEN,
                                "#CC00CC",
                                8f,
                                listOf(InkPoint(620f, 80f), InkPoint(680f, 80f)),
                            ),
                        ),
                )
            setContent {
                LogDateTheme(dynamicColor = false) { HandwritingEntryPrototype(initialPage = page, initiallyFocused = true) }
            }
            onNodeWithTag("handwriting_drawing_area").performTouchInput {
                swipe(Offset(180f, 300f), Offset(180f, 650f))
            }
            waitForIdle()
            val canvas = onNodeWithTag("handwriting_canvas").getBoundsInRoot()
            val history = onNodeWithContentDescription("Undo").getBoundsInRoot()
            assertTrue(canvas.top >= history.bottom, "First ink can hide beneath history: $canvas / $history")
        }

    @Test
    fun `phone drawing tools stay in one compact bottom row`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            setContent {
                LogDateTheme(dynamicColor = false) { HandwritingEntryPrototype(initiallyFocused = true) }
            }
            val palette = onNodeWithTag("handwriting_floating_tools").getBoundsInRoot()
            assertTrue((palette.bottom - palette.top).value <= 56f, "Palette wrapped: $palette")
            assertTrue((palette.right - palette.left).value <= 280f, "Palette too wide: $palette")
            val undo = onNodeWithContentDescription("Undo").getBoundsInRoot()
            assertTrue(undo.bottom.value < 120f, "History should sit at the top edge: $undo")
        }

    @Test
    fun `short narrow windows retain full sized reachable tool targets`() =
        runDesktopComposeUiTest(width = 320, height = 400) {
            setContent {
                LogDateTheme(dynamicColor = false) {
                    HandwritingEntryPrototype(initiallyFocused = true)
                }
            }
            listOf("Pen", "Highlight", "Erase").forEach { selectedTool ->
                onNodeWithContentDescription(selectedTool).performClick()
                waitForIdle()
                listOf("Pen", "Highlight", "Erase", "Undo", "Redo", "Ink and stroke width", "Drawing options").forEach { action ->
                    val bounds = onNodeWithContentDescription(action).getBoundsInRoot()
                    val touch = onNodeWithContentDescription(action).fetchSemanticsNode().touchBoundsInRoot
                    assertTrue(touch.width >= 48f, "$action touch width: $touch")
                    assertTrue(touch.height >= 48f, "$action touch height: $touch")
                    assertTrue(bounds.left.value >= 0f && bounds.right.value <= 320f, "$action outside width: $bounds")
                    assertTrue(bounds.top.value >= 0f && bounds.bottom.value <= 400f, "$action outside height: $bounds")
                }
            }
        }

    @Test
    fun `drawing tools expose selection and contextual ink settings on a phone`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            var page = HandwritingPage()
            setContent {
                LogDateTheme(dynamicColor = false) {
                    HandwritingEntryPrototype(initiallyFocused = true, onPageChanged = { page = it })
                }
            }
            onNodeWithContentDescription("Pen").assertIsSelected()
            onNodeWithContentDescription("Highlight").performClick().assertIsSelected()
            onNodeWithContentDescription("Pen").performClick()
            onNodeWithContentDescription("Ink and stroke width").performClick()
            onNodeWithText("Blue ink").performClick()
            onNodeWithContentDescription("Ink and stroke width").performClick()
            onNodeWithText("Broad pen").performClick()
            onNodeWithContentDescription("Drawing options").performClick()
            onNodeWithText("Draw with touch").performClick()
            onNodeWithTag("handwriting_canvas").performTouchInput {
                down(Offset(100f, 200f))
                moveBy(Offset(30f, 0f))
                up()
            }
            waitForIdle()
            assertEquals("#253F99", page.strokes.single().color)
            assertEquals(6f, page.strokes.single().strokeWidth)
        }

    @Test
    fun `writing survives focus return undo and redo`() =
        runDesktopComposeUiTest(width = 1000, height = 800) {
            var page = HandwritingPage()
            setContent {
                LogDateTheme(dynamicColor = false) {
                    HandwritingEntryPrototype(onPageChanged = { page = it })
                }
            }
            onNodeWithTag("handwriting_preview", useUnmergedTree = true).performClick()
            onNodeWithContentDescription("Drawing options").performClick()
            onNodeWithText("Draw with touch").performClick()
            onNodeWithTag("handwriting_canvas").performTouchInput {
                down(Offset(width / 3f, height / 3f))
                moveBy(Offset(60f, 30f))
                up()
            }
            waitForIdle()
            assertEquals(1, page.strokes.size)
            val written = page
            onNodeWithContentDescription("Back").performClick()
            onNodeWithTag("handwriting_preview", useUnmergedTree = true).assertExists()
            onNodeWithTag("handwriting_preview", useUnmergedTree = true).performClick()
            assertEquals(written, page)
            onNodeWithContentDescription("Undo").performClick()
            waitForIdle()
            assertTrue(page.strokes.isEmpty())
            onNodeWithContentDescription("Redo").performClick()
            waitForIdle()
            assertEquals(written, page)
        }

    @Test
    fun `finger input does not mark the page in pen only mode`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            var page = HandwritingPage()
            setContent {
                LogDateTheme(dynamicColor = false) {
                    HandwritingEntryPrototype(onPageChanged = { page = it })
                }
            }
            onNodeWithTag("handwriting_preview", useUnmergedTree = true).performClick()
            onNodeWithTag("handwriting_canvas").performTouchInput {
                down(Offset(width / 3f, height / 3f))
                moveBy(Offset(60f, 30f))
                up()
            }
            waitForIdle()
            assertTrue(page.strokes.isEmpty())
        }

    @Test
    fun `opening entry context keeps subsequent ink in the same page coordinates`() =
        runDesktopComposeUiTest(width = 1000, height = 800) {
            var page = HandwritingPage()
            setContent {
                LogDateTheme(dynamicColor = false) {
                    HandwritingEntryPrototype(onPageChanged = { page = it })
                }
            }
            onNodeWithTag("handwriting_preview", useUnmergedTree = true).performClick()
            onNodeWithContentDescription("Drawing options").performClick()
            onNodeWithText("Draw with touch").performClick()
            onNodeWithTag("handwriting_canvas").performTouchInput {
                down(Offset(width / 3f, height / 3f))
                moveBy(Offset(25f, 0f))
                up()
            }
            onNodeWithContentDescription("Drawing options").performClick()
            onNodeWithText("Entry context").performClick()
            onNodeWithTag("handwriting_canvas").performTouchInput {
                down(Offset(width / 3f, height / 3f))
                moveBy(Offset(25f, 0f))
                up()
            }
            waitForIdle()
            assertEquals(2, page.strokes.size)
            assertEquals(
                page.strokes
                    .first()
                    .points
                    .first()
                    .x,
                page.strokes
                    .last()
                    .points
                    .first()
                    .x,
                absoluteTolerance = 1f,
            )
        }

    @Test
    fun `inline preview includes handwriting at the bottom of the page`() =
        runDesktopComposeUiTest(width = 720, height = 240) {
            val page =
                HandwritingPage(
                    strokes =
                        listOf(
                            CanvasElement.Ink(
                                Uuid.random(),
                                InkTool.PEN,
                                "#263044",
                                8f,
                                listOf(InkPoint(120f, 880f), InkPoint(580f, 920f)),
                            ),
                        ),
                )
            setContent {
                HandwritingCanvas(page, Modifier.fillMaxSize(), ruled = false, description = "Original handwriting")
            }
            val pixels = onNodeWithTag("handwriting_preview", useUnmergedTree = true).captureToImage().toPixelMap()
            var inkPixels = 0
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    val color = pixels[x, y]
                    if (color.red < 0.3f && color.green < 0.3f && color.blue < 0.3f) inkPixels++
                }
            }
            assertTrue(inkPixels > 10, "Bottom-of-page original ink must be visible in the inline preview")
        }

    @Test
    fun `dedicated handwriting focus uses the whole mobile editor and hides journal controls`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            setContent { LogDateTheme(dynamicColor = false) { HandwritingEntryPrototype() } }
            onNodeWithTag("handwriting_preview", useUnmergedTree = true).performClick()
            waitForIdle()
            val bounds = onNodeWithTag("handwriting_canvas").getBoundsInRoot()
            assertTrue((bounds.right - bounds.left).value >= 350f, "Canvas should meet the mobile editor edges")
            assertTrue((bounds.bottom - bounds.top).value >= 760f, "Drawing chrome should overlay the full-height canvas")
            onNodeWithText("Everyday").assertDoesNotExist()
        }

    @Test
    fun `finger panning reaches ink below the viewport without moving original strokes`() =
        runDesktopComposeUiTest(width = 1000, height = 800) {
            var page = HandwritingPage()
            setContent {
                LogDateTheme(
                    dynamicColor = false,
                ) { HandwritingEntryPrototype(initiallyFocused = true, onPageChanged = { page = it }) }
            }
            onNodeWithContentDescription("Drawing options").performClick()
            onNodeWithText("Draw with touch").performClick()
            onNodeWithTag("handwriting_drawing_area").performTouchInput {
                down(Offset(500f, 200f))
                moveBy(Offset(30f, 0f))
                up()
            }
            val original = page.strokes.single()
            onNodeWithContentDescription("Drawing options").performClick()
            onNodeWithText("Draw with touch").performClick()
            onNodeWithTag("handwriting_drawing_area").performTouchInput {
                swipe(Offset(500f, 620f), Offset(500f, 120f))
            }
            onNodeWithContentDescription("Drawing options").performClick()
            onNodeWithText("Draw with touch").performClick()
            onNodeWithTag("handwriting_drawing_area").performTouchInput {
                down(Offset(500f, 200f))
                moveBy(Offset(30f, 0f))
                up()
            }
            waitForIdle()
            assertEquals(original, page.strokes.first())
            assertEquals(2, page.strokes.size)
            assertTrue(
                page.strokes
                    .last()
                    .points
                    .first()
                    .y > original.points.first().y + 250f,
            )
        }

    @Test
    fun `last lines can be scrolled above the floating drawing palette`() =
        runDesktopComposeUiTest(width = 1000, height = 800) {
            val page =
                HandwritingPage(
                    strokes =
                        listOf(
                            CanvasElement.Ink(
                                Uuid.random(),
                                InkTool.PEN,
                                "#CC00CC",
                                8f,
                                listOf(InkPoint(350f, 920f), InkPoint(390f, 930f)),
                            ),
                        ),
                )
            setContent { LogDateTheme(dynamicColor = false) { HandwritingEntryPrototype(initialPage = page, initiallyFocused = true) } }
            repeat(2) {
                onNodeWithTag("handwriting_drawing_area").performTouchInput { swipe(Offset(500f, 620f), Offset(500f, 120f)) }
            }
            waitForIdle()
            val area = onNodeWithTag("handwriting_drawing_area")
            val bounds = area.getBoundsInRoot()
            val palette = onNodeWithTag("handwriting_floating_tools").getBoundsInRoot()
            val pixels = area.captureToImage().toPixelMap()
            val limit = ((palette.top - bounds.top).value * pixels.height / (bounds.bottom - bounds.top).value).toInt()
            var visibleInk = 0
            for (y in 0 until limit.coerceIn(0, pixels.height)) {
                for (x in 0 until pixels.width) {
                    val color = pixels[x, y]
                    if (color.red > 0.6f && color.green < 0.1f && color.blue > 0.6f) visibleInk++
                }
            }
            assertTrue(visibleInk > 10, "Last-line ink must be reachable above the floating controls")
        }
}
