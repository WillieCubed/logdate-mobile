package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkPoint
import app.logdate.feature.postcards.model.InkTool
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.uuid.Uuid

/** Populated shared Compose renders, not Android runtime or pen-latency acceptance. */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class HandwritingPrototypeScreenshotsTest(
    private val name: String,
    private val width: Int,
    private val height: Int,
    private val dark: Boolean,
) {
    @Test
    fun inline() = capture(false)

    @Test
    fun focused() = capture(true)

    @Test
    fun focusedWithContext() {
        org.junit.Assume.assumeTrue(width >= 840)
        capture(true, withContext = true)
    }

    private fun capture(
        focused: Boolean,
        withContext: Boolean = false,
    ) = runDesktopComposeUiTest(width = width, height = height) {
        setContent {
            LogDateTheme(dynamicColor = false, darkTheme = dark) {
                HandwritingEntryPrototype(initialPage = sampleInk(), initiallyFocused = focused)
            }
        }
        if (withContext) {
            onNodeWithContentDescription("Drawing options").performClick()
            onNodeWithText("Entry context").performClick()
        }
        waitForIdle()
        val output =
            File(
                "build/reports/handwriting-prototype/$name-${if (focused) {
                    if (withContext) {
                        "context"
                    } else {
                        "focused"
                    }
                } else {
                    "inline"
                }}.png",
            )
        output.parentFile.mkdirs()
        output.writeBytes(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> =
            listOf(
                arrayOf("phone", 360, 800, false),
                arrayOf("tablet", 1280, 800, false),
                arrayOf("tablet-dark", 1280, 800, true),
                arrayOf("phone-landscape", 800, 360, false),
            )
    }
}

/** Vector fixture: every visible mark is an ink stroke, including the sample lettering. */
internal fun sampleInk(): HandwritingPage {
    val letters =
        mapOf(
            'a' to "0,12 2,3 8,1 12,4 11,14 7,17 1,15 0,10;12,2 12,17 16,15",
            'e' to "0,9 12,7 10,2 5,1 1,5 0,12 4,17 11,16",
            'i' to "3,3 2,16 7,15;4,-5 4,-4",
            'l' to "1,15 4,-9 7,-12 9,-9 6,2 1,13 4,17 9,15",
            'm' to "0,17 2,2 3,8 7,2 11,3 10,16;11,8 16,2 20,4 20,16 24,15",
            'o' to "11,3 5,1 1,5 0,12 3,17 9,16 12,10 11,3",
            'r' to "1,17 2,2 3,8 7,2 12,3",
            's' to "12,3 7,1 2,3 1,7 10,11 11,14 7,17 0,15",
            't' to "6,-8 3,13 5,17 12,15;0,1 13,0",
            'u' to "1,2 0,12 3,17 8,15 12,3 11,17 16,15",
            'd' to "11,3 5,1 1,5 0,12 3,17 9,16 12,10;12,16 15,-12 18,-10 14,13 17,16",
            'y' to "1,2 0,12 3,17 9,14 12,2 10,24 6,30 2,28",
            ' ' to "",
        )
    val strokes = mutableListOf<CanvasElement.Ink>()
    listOf("a little more time", "outside today").forEachIndexed { line, text ->
        var x = 55f
        text.forEachIndexed { index, letter ->
            val paths = letters.getValue(letter)
            if (paths.isNotEmpty()) {
                paths.split(';').forEach { path ->
                    val points =
                        path.split(' ').map { pair ->
                            val (px, py) = pair.split(',').map(String::toFloat)
                            InkPoint(x + px * 2.1f, 100f + line * 96f + py * 2.1f + (index % 3 - 1) * 1.5f, 0.7f)
                        }
                    strokes += CanvasElement.Ink(Uuid.random(), InkTool.PEN, "#253F99", 3.5f, points)
                }
            }
            x +=
                when (letter) {
                    'm' -> 53f
                    ' ' -> 24f
                    'i', 'l' -> 23f
                    else -> 34f
                }
        }
    }
    strokes +=
        CanvasElement.Ink(
            Uuid.random(),
            InkTool.HIGHLIGHTER,
            "#E0B441",
            20f,
            listOf(InkPoint(53f, 244f), InkPoint(465f, 238f)),
        )
    return HandwritingPage(strokes = strokes)
}
