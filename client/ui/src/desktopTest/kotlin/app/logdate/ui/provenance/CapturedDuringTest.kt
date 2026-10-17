@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.provenance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

/**
 * A moment captured during a walk or a trip says so after its time, but only while activity awareness
 * is on; a moment without an activity shows only its time.
 */
@OptIn(ExperimentalTestApi::class)
class CapturedDuringTest {
    @Test
    fun `the activity follows the time of a moment captured during it`() =
        runDesktopComposeUiTest {
            setContent {
                Activity(
                    visible = true,
                ) { CaptureTimeLabel(text = "3:42 PM", capturedOnWatch = false, capturedDuring = CaptureActivity.MOVING) }
            }

            onNodeWithText("3:42 PM · While moving").assertExists()
        }

    @Test
    fun `a trip captured on the watch shows both`() =
        runDesktopComposeUiTest {
            setContent {
                Activity(visible = true) {
                    CompositionLocalProvider(LocalWatchProvenanceVisible provides true) {
                        CaptureTimeLabel(text = "8:05 AM", capturedOnWatch = true, capturedDuring = CaptureActivity.IN_VEHICLE)
                    }
                }
            }

            onNodeWithText("8:05 AM · In a vehicle").assertExists()
            onNodeWithContentDescription("Captured on your watch").assertExists()
        }

    @Test
    fun `nothing is added while activity awareness is off`() =
        runDesktopComposeUiTest {
            setContent {
                Activity(
                    visible = false,
                ) { CaptureTimeLabel(text = "3:42 PM", capturedOnWatch = false, capturedDuring = CaptureActivity.MOVING) }
            }

            onNodeWithText("3:42 PM").assertExists()
        }

    @Test
    fun `a moment without an activity shows only its time`() =
        runDesktopComposeUiTest {
            setContent { Activity(visible = true) { CaptureTimeLabel(text = "3:42 PM", capturedOnWatch = false) } }

            onNodeWithText("3:42 PM").assertExists()
        }
}

@Composable
private fun Activity(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    LogDateTheme(dynamicColor = false, darkTheme = false) {
        CompositionLocalProvider(LocalActivityContextVisible provides visible, content = content)
    }
}
