package app.logdate.feature.core.account

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import app.logdate.feature.core.settings.ui.ServerPreset
import app.logdate.feature.core.settings.ui.ServerSelectionCard
import app.logdate.feature.core.settings.ui.ServerSelectionState
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CloudAccountWelcomeSettingsTest {
    @Test
    fun serverInformationDoesNotChangeTheSelectedServer() =
        runDesktopComposeUiTest(width = 444, height = 400) {
            var selections = 0
            var explanations = 0
            setContent {
                LogDateTheme {
                    ServerSelectionCard(
                        serverSelectionState = ServerSelectionState(),
                        onSelectPreset = { selections++ },
                        onUpdateCustomUrl = {},
                        onShowCustomServerInfo = { explanations++ },
                    )
                }
            }
            onNodeWithContentDescription("Use a custom server").performClick()
            assertEquals(1, explanations)
            assertEquals(0, selections)
            onNodeWithText("Custom server").performClick()
            assertEquals(1, selections)
        }

    @Test
    fun largeTextKeepsPrivacyInformationAndAccountActionsReachable() =
        runDesktopComposeUiTest(width = 411, height = 900) {
            var skips = 0
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    LogDateTheme {
                        CloudAccountWelcomeContent(
                            onContinue = {},
                            onSignIn = {},
                            onSkip = { skips++ },
                            serverSelectionState = ServerSelectionState(selectedPreset = ServerPreset.CUSTOM),
                            onSelectServerPreset = {},
                            onCustomServerUrlChange = {},
                            onShowCustomServerInfo = {},
                        )
                    }
                }
            }
            saveScreenshot("cloud-account-custom-large-text", onRoot().captureToImage())
            onNodeWithText("Create new account").assertIsDisplayed()
            onNodeWithText("Sign in").assertIsDisplayed()
            onNode(hasScrollAction()).performScrollToNode(hasText("Server URL"))
            onNodeWithText("Server URL").assertIsDisplayed()
            saveScreenshot("cloud-account-custom-large-text-server", onRoot().captureToImage())
            onNode(hasScrollAction()).performScrollToNode(hasText("Server policies come from the server you choose."))
            onNodeWithText("Server policies come from the server you choose.").assertIsDisplayed()
            saveScreenshot("cloud-account-custom-large-text-scrolled", onRoot().captureToImage())
            onNodeWithText("Skip").performClick()
            assertEquals(1, skips)
        }

    private fun saveScreenshot(
        name: String,
        image: ImageBitmap,
    ) {
        val directory = System.getenv("LOGDATE_SETTINGS_SCREENSHOT_DIR") ?: return
        val pixels = image.toPixelMap()
        val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) output.setRGB(x, y, pixels[x, y].toArgb())
        }
        val file = File(directory, "$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(output, "png", file)
    }
}
