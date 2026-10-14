package app.logdate.feature.core.settings.ui.watch

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.core.settings.ui.watch.v2.WatchCompanionPage
import app.logdate.feature.core.settings.ui.watch.v2.WatchCompanionPreferences
import app.logdate.feature.core.settings.ui.watch.v2.WatchCompanionSections
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WatchCompanionPageTest {
    @Test fun `wide companion keeps watch identity beside settings`() =
        runDesktopComposeUiTest(width = 1100, height = 900) {
            setContent {
                LogDateTheme(dynamicColor = false) {
                    WatchCompanionPage(
                        status = WatchStatus.Ready("Pixel Watch", true),
                        requestFailure = null,
                        onBack = {},
                        onAssociate = {},
                        onInstall = {},
                        onReminders = {},
                        onTiles = {},
                        onMicrophone = {},
                        onNotifications = {},
                        onDiagnostics = {},
                        onRemove = {},
                        preferences = { captureActions ->
                            WatchCompanionSections(
                                WatchCompanionPreferences(),
                                false,
                                false,
                                {},
                                {},
                                {},
                                {},
                                {},
                                captureActions = captureActions,
                            )
                        },
                    )
                }
            }
            val watch = onNodeWithText("Pixel Watch").fetchSemanticsNode().boundsInRoot
            val settings = onNodeWithText("Timeline history").fetchSemanticsNode().boundsInRoot
            val title = onNodeWithText("Watch").fetchSemanticsNode().boundsInRoot
            val leadingSection = onNodeWithText("Connection").fetchSemanticsNode().boundsInRoot
            saveScreenshot(onRoot().captureToImage(), "watch-companion-page-tablet.png")
            assertTrue(settings.left > watch.right, "Watch identity $watch and settings $settings should occupy separate panes")
            assertTrue(abs(title.left - leadingSection.left) <= 1f, "Settings title $title should align with content $leadingSection")
        }

    @Test fun `companion destinations are actionable through shared settings rows`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            val actions = mutableListOf<String>()
            setContent {
                LogDateTheme(dynamicColor = false) {
                    WatchCompanionPage(
                        WatchStatus.Ready("Pixel Watch", true),
                        null,
                        {},
                        {},
                        {},
                        onReminders = { actions += "reminders" },
                        onTiles = { actions += "tiles" },
                        onMicrophone = { actions += "microphone" },
                        onNotifications = { actions += "notifications" },
                        onDiagnostics = { actions += "diagnostics" },
                        onRemove = { actions += "remove" },
                        preferences = { captureActions ->
                            WatchCompanionSections(
                                WatchCompanionPreferences(),
                                false,
                                false,
                                {},
                                {},
                                {},
                                {},
                                {},
                                captureActions = captureActions,
                            )
                        },
                    )
                }
            }
            saveScreenshot(onRoot().captureToImage())
            onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Reminders"))
            onNodeWithText("Reminders").performClick()
            onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Tiles & complications"))
            onNodeWithText("Tiles & complications").performClick()
            onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Microphone"))
            onNodeWithText("Microphone").performClick()
            onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Check connection"))
            onNodeWithText("Check connection").performClick()
            onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Remove watch"))
            onNodeWithText("Remove watch").performClick()
            assertEquals(listOf("reminders", "tiles", "microphone", "diagnostics", "remove"), actions)
        }

    private fun saveScreenshot(
        image: ImageBitmap,
        filename: String = "watch-companion-page.png",
    ) {
        val directory = System.getenv("LOGDATE_SETTINGS_SCREENSHOT_DIR") ?: return
        val pixels = image.toPixelMap()
        val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) output.setRGB(x, y, pixels[x, y].toArgb())
        }
        val file = File(directory, filename)
        file.parentFile.mkdirs()
        ImageIO.write(output, "png", file)
    }
}
