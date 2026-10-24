package app.logdate.feature.core.settings.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RecoveryPhraseWordFieldsTest {
    @Test
    fun `next moves to the following word at large text on a compact screen`() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    LogDateTheme {
                        RecoveryPhraseWordFields(List(12) { "" }, enabled = true, onWordChange = { _, _ -> })
                    }
                }
            }
            onNodeWithTag("recovery_word_0")
                .assert(hasImeAction(ImeAction.Next))
                .performClick()
                .assertIsFocused()
                .performImeAction()
            onNodeWithTag("recovery_word_1").assertIsFocused()
            saveScreenshot("phone-large-text-phrase-entry", onRoot().captureToImage())
        }

    @Test
    fun `last word uses done and releases keyboard focus`() =
        runDesktopComposeUiTest(width = 720, height = 900) {
            setContent {
                LogDateTheme {
                    RecoveryPhraseWordFields(List(12) { "" }, enabled = true, onWordChange = { _, _ -> })
                }
            }
            onNodeWithTag("recovery_word_11")
                .assert(hasImeAction(ImeAction.Done))
                .performClick()
                .assertIsFocused()
                .performImeAction()
            onNodeWithTag("recovery_word_11").assertIsNotFocused()
        }

    private fun saveScreenshot(
        name: String,
        image: ImageBitmap,
    ) {
        val directory = System.getenv("LOGDATE_SYNC_SCREENSHOT_DIR") ?: return
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
