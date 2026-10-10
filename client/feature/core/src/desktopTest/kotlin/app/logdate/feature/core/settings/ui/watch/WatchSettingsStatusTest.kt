@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

/**
 * The Watch page says only what the watch reported. A connected watch that reports nothing shows its
 * name and that it is connected, never a sync state the phone does not know.
 */
@OptIn(ExperimentalTestApi::class)
class WatchSettingsStatusTest {
    @Test
    fun `a connected watch that reports nothing shows no sync status`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent { WatchPage(WatchStatus.Ready(watchName = "Pixel Watch", nearby = true)) }

            onNodeWithText("Pixel Watch").assertExists()
            onNodeWithText("Connected").assertExists()
            onNodeWithText("Not yet synced").assertDoesNotExist()
            onNodeWithText("Up to date").assertDoesNotExist()
        }

    @Test
    fun `a watch that reports waiting moments says how many`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent { WatchPage(WatchStatus.Ready(watchName = "Pixel Watch", nearby = true, sync = WatchSyncLine(waiting = 2))) }

            onNodeWithText("2 entries waiting to sync").assertExists()
        }

    @Test
    fun `a watch that reports an empty queue is up to date`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent { WatchPage(WatchStatus.Ready(watchName = "Pixel Watch", nearby = true, sync = WatchSyncLine(waiting = 0))) }

            onNodeWithText("Up to date").assertExists()
        }

    @Test
    fun `reported link and library lines appear`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent {
                WatchPage(
                    WatchStatus.Ready(
                        watchName = "Pixel Watch",
                        nearby = true,
                        link = WatchLinkLine.ON_ITS_OWN,
                        window = WatchWindowLine(days = 14),
                    ),
                )
            }

            onNodeWithText("Backs up on its own over Wi-Fi or mobile data").assertExists()
            onNodeWithText("14 days of moments on your watch").assertExists()
        }

    @Test
    fun `moments that did not send are named`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent {
                WatchPage(
                    WatchStatus.NeedsAttention(
                        watchName = "Pixel Watch",
                        nearby = true,
                        issue = WatchIssue.CapturesNotSent(count = 3),
                    ),
                )
            }

            onNodeWithText("3 moments on your watch didn't send").assertExists()
        }

    @Test
    fun `a watch that lost its link asks to be set up again`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent { WatchPage(WatchStatus.NeedsAttention(watchName = "Pixel Watch", nearby = true, issue = WatchIssue.SetUpAgain)) }

            onNodeWithText("Set up LogDate on your watch again").assertExists()
        }
}

@Composable
private fun WatchPage(status: WatchStatus) {
    LogDateTheme(dynamicColor = false, darkTheme = false) {
        WatchSettingsContent(
            status = status,
            requestFailure = null,
            onBack = {},
            onBeginAssociation = {},
            onRequestSync = {},
            onInstallOnWatch = {},
            onNavigateToNotifications = {},
            onNavigateToTroubleshooting = {},
        )
    }
}
