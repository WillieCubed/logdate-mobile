package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class EntryStartPrototypeScreenshotsTest(
    private val name: String,
    private val width: Int,
    private val height: Int,
    private val dark: Boolean,
    private val populated: Boolean,
    private val focused: Boolean,
) {
    @Test
    fun renderAndOpenHandwriting() =
        runDesktopComposeUiTest(width = width, height = height) {
            setContent {
                LogDateTheme(dynamicColor = false, darkTheme = dark) {
                    EntryStartPrototype(
                        initialPage = if (populated) sampleInk() else null,
                        initiallyFocused = focused,
                        initialText =
                            if (populated && !name.endsWith("-ink")) {
                                "Took the long way home.\n\nStopped at the park for a while. The light was too good to miss."
                            } else {
                                ""
                            },
                    )
                }
            }
            waitForIdle()
            onNodeWithTag("handwriting_editor").assertExists()
            if (!focused) onNodeWithTag("earlier_today").assertExists()
            if (!populated && !focused) onNodeWithText("Add handwriting").assertDoesNotExist()
            val output = File("build/reports/entry-start-prototype/$name.png")
            output.parentFile.mkdirs()
            output.writeBytes(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
            if (!focused) {
                if (populated) {
                    onNodeWithTag("editor_block_list").assertExists()
                    onNodeWithTag("handwriting_preview", useUnmergedTree = true).performClick()
                } else {
                    onNodeWithTag("editor_start_handwriting_block").performClick()
                }
                onNodeWithTag("handwriting_canvas").assertExists()
                onNodeWithTag("earlier_today").assertDoesNotExist()
                onNodeWithContentDescription("Back").performClick()
                onNodeWithTag("handwriting_preview", useUnmergedTree = true).assertExists()
                onNodeWithTag("earlier_today").assertExists()
            }
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> =
            listOf(
                arrayOf("phone-empty", 360, 800, false, false, false),
                arrayOf("phone-ink", 360, 800, false, true, false),
                arrayOf("phone-entry", 360, 800, false, true, false),
                arrayOf("phone-focused", 360, 800, false, true, true),
                arrayOf("phone-dark", 360, 800, true, false, false),
                arrayOf("tablet-empty", 1280, 800, false, false, false),
                arrayOf("tablet-ink", 1280, 800, false, true, false),
                arrayOf("tablet-entry", 1280, 800, false, true, false),
                arrayOf("tablet-focused", 1280, 800, false, true, true),
            )
    }
}
