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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.client.text.toUiText
import app.logdate.ui.media.MediaDeviceSelectorTags
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class CameraRouteContrastTest(
    private val dark: Boolean,
    private val brightPreview: Boolean,
) {
    @Test
    fun cameraRouteLabelStaysReadableAcrossThemeAndPreviewBrightness() =
        runDesktopComposeUiTest(width = 400, height = 200) {
            val device =
                MediaDeviceUiState(
                    "synthetic-back",
                    "Back camera".toUiText(),
                    MediaDeviceKind.CAMERA,
                    MediaDeviceCategory.BACK_CAMERA,
                )
            setContent {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.fillMaxSize().background(if (brightPreview) Color.White else Color.Black).padding(24.dp)) {
                        CameraRouteSelector(
                            MediaDeviceSelectionUiState(MediaDeviceKind.CAMERA, listOf(device), device.id),
                            {},
                            "Camera",
                            true,
                        )
                    }
                }
            }
            val chip = onNodeWithTag(MediaDeviceSelectorTags.chip("Camera")).fetchSemanticsNode().boundsInRoot
            val label = onNodeWithText("Back camera", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val pixels = onRoot().captureToImage().toPixelMap()
            val background = pixels[(chip.left + 8).toInt(), chip.center.y.toInt()].luminance()
            var strongestContrast = 1f
            for (y in label.top.toInt() until label.bottom.toInt()) {
                for (x in label.left.toInt() until label.right.toInt()) {
                    val ink = pixels[x, y].luminance()
                    strongestContrast = max(strongestContrast, (max(ink, background) + 0.05f) / (min(ink, background) + 0.05f))
                }
            }
            assertTrue(strongestContrast >= 4.5f, "Route label contrast must be at least 4.5:1; was $strongestContrast")
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "dark={0}-brightPreview={1}")
        fun variants() = listOf(arrayOf(false, false), arrayOf(false, true), arrayOf(true, false), arrayOf(true, true))
    }
}
