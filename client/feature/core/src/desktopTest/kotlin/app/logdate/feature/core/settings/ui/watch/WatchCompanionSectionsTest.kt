package app.logdate.feature.core.settings.ui.watch

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.core.settings.ui.watch.v2.WatchCompanionPreferences
import app.logdate.feature.core.settings.ui.watch.v2.WatchCompanionSections
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WatchCompanionSectionsTest {
    @Test
    fun `library choice and capture rows send explicit shared preferences`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            val choices = mutableListOf<Any>()
            setContent {
                LogDateTheme(dynamicColor = false, darkTheme = false) {
                    WatchCompanionSections(
                        WatchCompanionPreferences(),
                        false,
                        false,
                        onDays = { choices.add(it) },
                        onThisDay = { choices.add(it) },
                        onRewind = { choices.add(it) },
                        onPlaces = { choices.add(it) },
                        onMood = { choices.add(it) },
                    )
                }
            }
            onNodeWithText("Days on your watch").performClick()
            onNodeWithText("30 days").performClick()
            onNodeWithText("Places").performClick()
            onNodeWithText("Mood after recording").performClick()
            assertEquals(listOf<Any>(30, true, false), choices)
            saveScreenshot(onRoot().captureToImage())
        }

    @Test
    fun `while saving dependent controls are disabled and failure is visible`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent {
                LogDateTheme(dynamicColor = false, darkTheme = false) {
                    WatchCompanionSections(
                        WatchCompanionPreferences(),
                        true,
                        true,
                        onDays = {},
                        onThisDay = {},
                        onRewind = {},
                        onPlaces = {},
                        onMood = {},
                    )
                }
            }
            onNodeWithText("Places").assertIsNotEnabled()
            onNodeWithText("Couldn't save your watch settings. Try again.").assertExists()
        }

    private fun saveScreenshot(image: ImageBitmap) {
        val directory = System.getenv("LOGDATE_SETTINGS_SCREENSHOT_DIR") ?: return
        val pixels = image.toPixelMap()
        val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) output.setRGB(x, y, pixels[x, y].toArgb())
        }
        val file = File(directory, "watch-companion-preferences.png")
        file.parentFile.mkdirs()
        ImageIO.write(output, "png", file)
    }
}
