package app.logdate.feature.core.settings.ui.watch

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WatchDeviceCardTest {
    @Test
    fun `watch card opens settings without repeating device details`() =
        runDesktopComposeUiTest(width = 411, height = 600) {
            var opens = 0
            setContent {
                LogDateTheme(dynamicColor = false, darkTheme = false) {
                    WatchDeviceCard(
                        WatchStatus.Ready("Pixel Watch", true, sync = WatchSyncLine(0), window = WatchWindowLine(14)),
                        onClick = { opens++ },
                    )
                }
            }
            onNodeWithText("Watch").performClick()
            assertEquals(1, opens)
            System.getenv("LOGDATE_SETTINGS_SCREENSHOT_DIR")?.let { directory ->
                val image = onRoot().captureToImage()
                val pixels = image.toPixelMap()
                val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until image.height) {
                    for (x in 0 until image.width) output.setRGB(x, y, pixels[x, y].toArgb())
                }
                ImageIO.write(output, "png", File(directory, "watch-device-card.png").also { it.parentFile.mkdirs() })
            }
            onNodeWithText("Up to date").assertExists()
            onNodeWithText("Pixel Watch").assertDoesNotExist()
            onNodeWithText("14 days of moments on your watch").assertDoesNotExist()
        }

    @Test
    fun `an unreported queue is never called up to date`() =
        runDesktopComposeUiTest(width = 411, height = 600) {
            setContent {
                LogDateTheme(dynamicColor = false, darkTheme = false) {
                    WatchDeviceCard(WatchStatus.Ready("Pixel Watch", false), onClick = {})
                }
            }
            onNodeWithText("Watch").assertExists()
            onNodeWithText("Out of range").assertExists()
            onNodeWithText("Up to date", substring = true).assertDoesNotExist()
        }
}
