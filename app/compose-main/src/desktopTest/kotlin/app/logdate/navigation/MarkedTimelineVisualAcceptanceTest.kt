package app.logdate.navigation

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.screenshots.shared.MarkedTimelineAcceptanceContent
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/** Reviews the pending-memory flow inside the same populated workspace used by Home. */
@OptIn(ExperimentalTestApi::class)
class MarkedTimelineVisualAcceptanceTest {
    @Test
    fun pendingMemoryUsesTimelineAndContextualEditing() {
        val photo =
            generateSequence(File(".").absoluteFile) { it.parentFile }
                .map { File(it, "app/android-main/src/debug/assets/sample_note_photo.jpg") }
                .first { it.isFile }
        requireNotNull(ImageIO.read(photo)) { "The photo fixture must contain readable media" }
        listOf(Triple("phone", 411, 891), Triple("tablet", 1280, 800), Triple("compact", 320, 568)).forEach { (name, width, height) ->
            runDesktopComposeUiTest(width = width, height = height) {
                setContent {
                    LogDateTheme(dynamicColor = false) {
                        MarkedTimelineAcceptanceContent(photo.toURI().toString())
                    }
                }
                onNodeWithText("Stop").assertIsDisplayed().assertHasClickAction()
                onNodeWithText("Continue on watch").assertDoesNotExist()
                onNodeWithText("Save caption").assertDoesNotExist()
                onNodeWithText("Delete mark").assertDoesNotExist()
                onNodeWithText("Congrats, curious explorer!").assertDoesNotExist()
                onAllNodesWithText("Timeline").assertCountEquals(1)
                saveImage("home-$name", onRoot().captureToImage())

                onNodeWithText("Edit caption").performScrollTo().assertHasClickAction().performClick()
                onNodeWithText("Save caption").performScrollTo().assertIsDisplayed().assertHasClickAction()
                saveImage("caption-$name", onRoot().captureToImage())

                onNodeWithText("Options").performScrollTo().assertHasClickAction()
            }
        }
    }

    private fun saveImage(
        name: String,
        image: androidx.compose.ui.graphics.ImageBitmap,
    ) {
        val directory = File(System.getenv("LOGDATE_COMPANION_SCREENSHOT_DIR") ?: "/tmp/logdate-firstclass-home-renders")
        directory.mkdirs()
        val pixels = image.toPixelMap()
        val buffered = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) buffered.setRGB(x, y, pixels[x, y].toArgb())
        }
        ImageIO.write(buffered, "png", File(directory, "$name.png"))
    }
}
