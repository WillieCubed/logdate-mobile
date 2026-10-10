package app.logdate.screenshots.flows.flow20_os_integration

import androidx.compose.runtime.Composable
import app.logdate.feature.editor.handoff.HandoffDraftArrival
import app.logdate.feature.editor.handoff.HandoffDraftArrivalContent
import app.logdate.feature.editor.handoff.HandoffDraftMissingReason
import app.logdate.screenshots.common.OsIntegrationPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

/** An entry handed off from another device, while its draft downloads through backup. */
@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun S01_EditorHandoffWaiting() {
    ScreenshotTheme {
        HandoffDraftArrivalContent(arrival = HandoffDraftArrival.Waiting, onRetry = {}, onStartNewEntry = {})
    }
}

/** A handed-off entry that cannot arrive, because backup is off on this device. */
@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun S02_EditorHandoffBackupUnavailable() {
    ScreenshotTheme {
        HandoffDraftArrivalContent(
            arrival = HandoffDraftArrival.Missing(HandoffDraftMissingReason.BackupUnavailable),
            onRetry = {},
            onStartNewEntry = {},
        )
    }
}

/** A handed-off entry the other device has not uploaded within the wait. */
@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun S03_EditorHandoffNotUploaded() {
    ScreenshotTheme {
        HandoffDraftArrivalContent(
            arrival = HandoffDraftArrival.Missing(HandoffDraftMissingReason.NotUploaded),
            onRetry = {},
            onStartNewEntry = {},
        )
    }
}
