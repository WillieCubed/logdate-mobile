package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EntryPagePaperLayerTest {
    @Test
    fun `pressing the handwriting preview does not paint an inset rectangular layer`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            setContent { EntryPageStudy() }
            waitForIdle()
            val bounds = onNodeWithTag("handwriting_preview").fetchSemanticsNode().boundsInRoot
            onNodeWithTag("handwriting_preview").performTouchInput { down(Offset(width / 2f, height - 32f)) }
            mainClock.advanceTimeBy(600)
            val pixels = onRoot().captureToImage().toPixelMap()
            val y = (bounds.bottom - 8f).toInt()
            val outside = pixels[(bounds.left - 8f).toInt(), y]
            val inside = pixels[(bounds.left + 8f).toInt(), y]
            val difference = abs(outside.red - inside.red) + abs(outside.green - inside.green) + abs(outside.blue - inside.blue)
            assertTrue(difference < 0.02f, "Canvas and surrounding paper must stay continuous during press: $outside / $inside")
            onNodeWithTag("handwriting_preview").performTouchInput { up() }
        }
}
