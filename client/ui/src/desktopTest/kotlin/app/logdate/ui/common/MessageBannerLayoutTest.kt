package app.logdate.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MessageBannerLayoutTest {
    @Test
    fun storageRecoveryKeepsItsMessageAndActionAtLargeText() = verify(2f, false, LayoutDirection.Ltr)

    @Test
    fun storageRecoveryKeepsItsMessageAndActionInDarkRtl() = verify(2f, true, LayoutDirection.Rtl)

    @Test
    fun storageRecoveryFitsACompactPhoneAtDefaultText() = verify(1f, false, LayoutDirection.Ltr)

    private fun verify(
        fontScale: Float,
        dark: Boolean,
        direction: LayoutDirection,
    ) = runDesktopComposeUiTest(width = 320, height = 800) {
        val message = "Free up storage to finish saving your widget. Your selection is kept here."
        var actions = 0
        var dismissals = 0
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalLayoutDirection provides direction,
            ) {
                LogDateTheme(dynamicColor = false, darkTheme = dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxWidth()) {
                            MessageBanner(
                                content =
                                    BannerContent(
                                        message = message,
                                        icon = Icons.Rounded.Storage,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        action = "Manage storage" to { actions++ },
                                        dismissible = true,
                                    ),
                                onDismiss = { dismissals++ },
                            )
                        }
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(message, useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse(layout.hasVisualOverflow)
        for (line in 0 until layout.lineCount) assertFalse(layout.isLineEllipsized(line))
        if (fontScale == 2f) assertTrue(layout.lineCount > 3)
        onNodeWithText("Manage storage").assertIsDisplayed().performClick()
        onNodeWithText("Dismiss").assertIsDisplayed().performClick()
        assertEquals(1, actions)
        assertEquals(1, dismissals)
        saveScreenshot("message-banner-$fontScale-$dark-$direction", onRoot().captureToImage())
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
