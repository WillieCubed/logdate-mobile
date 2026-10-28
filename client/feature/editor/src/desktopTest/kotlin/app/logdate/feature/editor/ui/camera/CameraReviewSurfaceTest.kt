package app.logdate.feature.editor.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.layout.LocalEntryCreationReserve
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class CameraReviewSurfaceTest(
    private val width: Int,
    private val height: Int,
    private val reserve: Int,
) {
    @Test
    fun reviewControlsUseInnerSafeRailWithoutAFullWidthBackingBand() =
        runDesktopComposeUiTest(width = width, height = height) {
            var retakes = 0
            var accepts = 0
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalEntryCreationReserve provides reserve.dp) {
                        Box(Modifier.fillMaxSize().background(Color.Blue)) {
                            CameraReviewActions(
                                CapturedMediaType.PHOTO,
                                { retakes++ },
                                { accepts++ },
                                Modifier.align(Alignment.BottomCenter),
                                WindowInsets(0, 24, 0, 20),
                            )
                        }
                    }
                }
            }
            val panel = onNodeWithTag("camera_review_surface").fetchSemanticsNode().boundsInRoot
            assertEquals((min(width, 640) - 32).toFloat(), panel.width)
            assertEquals((width - panel.width) / 2, panel.left)
            assertEquals((height - 20 - reserve - 16).toFloat(), panel.bottom)
            assertTrue(panel.left >= 16f && width - panel.right >= 16f)
            val pixels = onRoot().captureToImage().toPixelMap()
            assertEquals(Color.Blue, pixels[0, height - 24], "Outer scene must remain continuous alongside the review controls")
            assertEquals(Color.Blue, pixels[0, height - 10], "The OS-safe area must inherit the same scene paint")
            onNodeWithText("Retake").performTouchInput { click() }
            onNodeWithText("Use Photo").performTouchInput { click() }
            assertEquals(1, retakes)
            assertEquals(1, accepts)
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}x{1}-creationReserve={2}")
        fun sizes() =
            listOf(
                arrayOf(400, 800, 0),
                arrayOf(400, 800, 80),
                arrayOf(1000, 800, 0),
                arrayOf(1000, 800, 80),
                arrayOf(1280, 800, 0),
                arrayOf(1280, 800, 80),
            )
    }
}
