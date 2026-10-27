package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class StoriesEntryScreenshotsTest(
    private val name: String,
    private val width: Int,
    private val height: Int,
) {
    @Test
    fun capture() =
        runDesktopComposeUiTest(width = width, height = height) {
            val ink = name.contains("ink") || name.contains("focus")
            setContent {
                LogDateTheme(dynamicColor = false, darkTheme = name.contains("dark")) {
                    StoriesEntryPrototype(
                        initialPage = if (ink) sampleInk() else null,
                        initialText = if (name.contains("written")) "Took the long way home.\n\nThe light was too good to miss." else "",
                        initiallyFocused = name.contains("focus"),
                    )
                }
            }
            waitForIdle()
            File("build/reports/stories-entry/$name.png").apply {
                parentFile.mkdirs()
                writeBytes(
                    Image
                        .makeFromBitmap(
                            onRoot().captureToImage().asSkiaBitmap(),
                        ).encodeToData()!!
                        .bytes,
                )
            }
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> =
            listOf(
                arrayOf("phone-dark-write", 360, 800),
                arrayOf("phone-dark-ink", 360, 800),
                arrayOf("phone-dark-focus", 360, 800),
                arrayOf("phone-write", 360, 800),
                arrayOf("phone-written", 360, 800),
                arrayOf("phone-ink", 360, 800),
                arrayOf("phone-focus", 360, 800),
                arrayOf("phone-earlier", 360, 800),
                arrayOf("tablet-write", 1280, 800),
                arrayOf("tablet-ink", 1280, 800),
            )
    }
}
