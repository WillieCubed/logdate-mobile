@file:Suppress("ktlint:standard:function-naming")

package app.logdate.screenshots.shared

import androidx.compose.runtime.Composable
import app.logdate.client.domain.watch.WatchNotificationSettings
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.feature.core.settings.ui.watch.WatchConnectionState
import app.logdate.feature.core.settings.ui.watch.WatchIssue
import app.logdate.feature.core.settings.ui.watch.WatchLinkLine
import app.logdate.feature.core.settings.ui.watch.WatchNotificationSettingsContent
import app.logdate.feature.core.settings.ui.watch.WatchRequestFailure
import app.logdate.feature.core.settings.ui.watch.WatchSettingsContent
import app.logdate.feature.core.settings.ui.watch.WatchStatus
import app.logdate.feature.core.settings.ui.watch.WatchSyncLine
import app.logdate.feature.core.settings.ui.watch.WatchTroubleshootingContent
import app.logdate.feature.core.settings.ui.watch.WatchWindowLine

/** The Watch page and its detail screens, in the states a phone can be in with its watch. */
internal val watchSettingsScenes: List<SharedScreenshotSceneSpec> =
    listOf(
        sharedScene(SharedScreenshotSceneId.WatchSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
            WatchSettingsPage(WatchStatus.Ready(watchName = WATCH_NAME, nearby = true))
        },
        sharedScene(SharedScreenshotSceneId.WatchSettingsReporting, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
            WatchSettingsPage(
                WatchStatus.Ready(
                    watchName = WATCH_NAME,
                    nearby = true,
                    link = WatchLinkLine.ON_ITS_OWN,
                    sync = WatchSyncLine(waiting = 2),
                    window = WatchWindowLine(days = 14),
                    capabilities = setOf(WearFeatures.RECEIPTS, WearFeatures.WINDOW, WearFeatures.LINK_CLOUD),
                ),
            )
        },
        sharedScene(SharedScreenshotSceneId.WatchSettingsAttention, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
            WatchSettingsPage(
                WatchStatus.NeedsAttention(
                    watchName = WATCH_NAME,
                    nearby = true,
                    issue = WatchIssue.CapturesNotSent(count = 3),
                ),
            )
        },
        sharedScene(SharedScreenshotSceneId.WatchNotificationSettings, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
            WatchNotificationSettingsContent(
                settings = WatchNotificationSettings(showEntryNotifications = true),
                onBack = {},
                onSetShowEntryNotifications = {},
            )
        },
        sharedScene(SharedScreenshotSceneId.WatchTroubleshooting, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
            WatchTroubleshootingContent(
                connectionState = WatchConnectionState.AppNotInstalled(watchName = WATCH_NAME),
                requestFailure = null,
                onBack = {},
                onBeginAssociation = {},
                onInstallOnWatch = {},
                onOpenOnWatch = {},
            )
        },
        sharedScene(SharedScreenshotSceneId.WatchTroubleshootingRequestFailed, ScreenshotSceneGroup.SETTINGS, standardMatrixVariants) {
            WatchTroubleshootingContent(
                connectionState = WatchConnectionState.Connected(watchName = WATCH_NAME),
                requestFailure = WatchRequestFailure.WATCH_DID_NOT_RESPOND,
                onBack = {},
                onBeginAssociation = {},
                onInstallOnWatch = {},
                onOpenOnWatch = {},
            )
        },
    )

@Composable
private fun WatchSettingsPage(status: WatchStatus) {
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

private const val WATCH_NAME = "Pixel Watch 3"
