package app.logdate.feature.editor.ui.handwriting

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StoriesThemeTest {
    @Test fun `light shell follows app theme`() = checkShell(false)

    @Test fun `dark shell follows app theme`() = checkShell(true)

    private fun checkShell(dark: Boolean) =
        runDesktopComposeUiTest(width = 360, height = 800) {
            var expected = Color.Unspecified
            setContent {
                LogDateTheme(darkTheme = dark, dynamicColor = false) {
                    expected = MaterialTheme.colorScheme.surfaceDim
                    StoriesEntryPrototype()
                }
            }
            val actual = onNodeWithTag("stories_editor").captureToImage().toPixelMap()[0, 0]
            assertEquals(expected.red, actual.red, 0.01f)
            assertEquals(expected.green, actual.green, 0.01f)
            assertEquals(expected.blue, actual.blue, 0.01f)
        }
}
