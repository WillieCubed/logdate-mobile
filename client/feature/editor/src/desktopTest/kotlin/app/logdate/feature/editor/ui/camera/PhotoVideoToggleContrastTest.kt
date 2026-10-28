package app.logdate.feature.editor.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class PhotoVideoToggleContrastTest(
    private val dark: Boolean,
    private val brightPreview: Boolean,
    private val selected: CaptureMode,
) {
    @Test
    fun bothChoicesStayReadableIndependentOfPreviewAndSelection() =
        runDesktopComposeUiTest(width = 400, height = 200) {
            val colors = if (dark) darkColorScheme() else lightColorScheme()
            setContent {
                MaterialTheme(colorScheme = colors) {
                    Box(Modifier.fillMaxSize().background(if (brightPreview) Color.White else Color.Black).padding(24.dp)) {
                        PhotoVideoToggle(selected, {})
                    }
                }
            }
            val pixels = onRoot().captureToImage().toPixelMap()
            for ((mode, text) in listOf(CaptureMode.PHOTO to "Photo", CaptureMode.VIDEO to "Video")) {
                val button = onNode(hasText(text) and hasClickAction()).fetchSemanticsNode().boundsInRoot
                val label = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val background = pixels[(button.left + 8).toInt(), button.center.y.toInt()]
                if (mode != selected) {
                    assertEquals(colors.surfaceContainerHigh.toArgb(), background.toArgb(), "Inactive choice must own an opaque surface")
                }
                var strongestContrast = 1f
                for (y in label.top.toInt() until label.bottom.toInt()) {
                    for (x in label.left.toInt() until label.right.toInt()) {
                        val ink = pixels[x, y].luminance()
                        val surface = background.luminance()
                        strongestContrast = max(strongestContrast, (max(ink, surface) + 0.05f) / (min(ink, surface) + 0.05f))
                    }
                }
                assertTrue(strongestContrast >= 4.5f, "$text label contrast must be at least 4.5:1; was $strongestContrast")
            }
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "dark={0}-bright={1}-selected={2}")
        fun variants() =
            listOf(false, true).flatMap { dark ->
                listOf(false, true).flatMap { bright -> CaptureMode.entries.map { arrayOf<Any>(dark, bright, it) } }
            }
    }
}
