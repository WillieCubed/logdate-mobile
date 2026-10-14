package app.logdate.ui.audio

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RecordingSessionControlTest {
    @Test
    fun pausedRemoteRecordingRetainsItsOriginElapsedTimeAndExplicitSaveAction() =
        runDesktopComposeUiTest(width = 320) {
            var stops = 0
            setContent {
                LogDateTheme {
                    RecordingSessionControl("On your watch", 73_000, true, false, null, { stops++ })
                }
            }
            onNodeWithText("Recording paused").assertIsDisplayed()
            onNodeWithText("On your watch · 1:13").assertIsDisplayed()
            val status = onNodeWithText("Recording paused").getUnclippedBoundsInRoot()
            val stop = onNodeWithText("Stop").getUnclippedBoundsInRoot()
            assertTrue(status.top < stop.bottom && stop.top < status.bottom, "Compact status and Stop should share one row")
            onNodeWithText("Stop").performClick()
            assertEquals(1, stops)
        }

    @Test
    fun pendingAcknowledgementPreventsDuplicateStopAndKeepsTheRecordingVisible() =
        runDesktopComposeUiTest {
            setContent {
                LogDateTheme {
                    RecordingSessionControl("On your watch", 73_000, false, true, null, {})
                }
            }
            onNodeWithText("Saving…").assertIsNotEnabled()
            onNodeWithText("On your watch · 1:13").assertIsDisplayed()
        }

    @Test
    fun wideRecordingControlSeparatesStatusAndStopAcrossItsSurface() =
        runDesktopComposeUiTest(width = 600) {
            setContent {
                LogDateTheme {
                    RecordingSessionControl("Watch", 73_000, false, false, null, {})
                }
            }
            val status = onNodeWithText("Watch · 1:13").getUnclippedBoundsInRoot()
            val stop = onNodeWithText("Stop").getUnclippedBoundsInRoot()
            assertTrue(stop.left - status.right >= 100.dp, "The action should use the far edge of a wide recording surface")
        }
}
