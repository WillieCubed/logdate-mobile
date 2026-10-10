@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.provenance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

/**
 * A moment captured on the watch carries a small watch glyph next to its time, but only while the
 * redesigned watch experience is on; a moment from the phone looks the same as before.
 */
@OptIn(ExperimentalTestApi::class)
class CapturedOnWatchTest {
    @Test
    fun `the watch glyph follows the time of a moment captured on the watch`() =
        runDesktopComposeUiTest {
            setContent { Provenance(visible = true) { CaptureTimeLabel(text = "3:42 PM", capturedOnWatch = true) } }

            onNodeWithText("3:42 PM").assertExists()
            onNodeWithContentDescription("Captured on your watch").assertExists()
        }

    @Test
    fun `a moment from the phone shows only its time`() =
        runDesktopComposeUiTest {
            setContent { Provenance(visible = true) { CaptureTimeLabel(text = "3:42 PM", capturedOnWatch = false) } }

            onNodeWithText("3:42 PM").assertExists()
            onNodeWithContentDescription("Captured on your watch").assertDoesNotExist()
        }

    @Test
    fun `nothing shows while the redesigned watch experience is off`() =
        runDesktopComposeUiTest {
            setContent { Provenance(visible = false) { CaptureTimeLabel(text = "3:42 PM", capturedOnWatch = true) } }

            onNodeWithContentDescription("Captured on your watch").assertDoesNotExist()
        }

    @Test
    fun `the editor banner names the watch`() =
        runDesktopComposeUiTest {
            setContent { Provenance(visible = true) { CapturedOnWatchBanner() } }

            onNodeWithText("Captured on your watch").assertExists()
        }
}

@Composable
private fun Provenance(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    LogDateTheme(dynamicColor = false, darkTheme = false) {
        CompositionLocalProvider(LocalWatchProvenanceVisible provides visible, content = content)
    }
}
