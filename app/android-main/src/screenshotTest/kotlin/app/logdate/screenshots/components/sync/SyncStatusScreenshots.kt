package app.logdate.screenshots.components.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.metadata.EntityType
import app.logdate.feature.core.sync.QueuedGroup
import app.logdate.feature.core.sync.SyncStatusContent
import app.logdate.feature.core.sync.SyncStatusFeedback
import app.logdate.feature.core.sync.SyncStatusUiState
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

/**
 * Backup status, as the sheet (phones) and dialog (wider windows) show it. The container itself is
 * a popup, which previews don't render, so these capture the content both containers share.
 */

private val waitingGroups =
    listOf(
        QueuedGroup(EntityType.NOTE, count = 12, retrying = 3),
        QueuedGroup(EntityType.MEDIA, count = 6, retrying = 0),
        QueuedGroup(EntityType.JOURNAL, count = 1, retrying = 0),
    )

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun StatusSheet_Waiting_offline() {
    StatusHarness(
        SyncStatusUiState(
            pendingCount = 19,
            pausedReason = SyncPausedReason.OFFLINE,
            lastSyncTime = ScreenshotTestData.baseInstant,
            groups = waitingGroups,
        ),
        feedback = SyncStatusFeedback.Requested,
    )
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun StatusSheet_Backing_up_with_progress() {
    StatusHarness(
        SyncStatusUiState(
            isSyncing = true,
            completedInRun = 7,
            totalForRun = 19,
            pendingCount = 12,
            lastSyncTime = ScreenshotTestData.baseInstant,
            groups = waitingGroups.take(2),
        ),
    )
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun StatusSheet_Background_data_blocked() {
    StatusHarness(
        SyncStatusUiState(
            pendingCount = 19,
            pausedReason = SyncPausedReason.BACKGROUND_DATA_OFF,
            groups = waitingGroups,
        ),
    )
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun StatusSheet_Failed_attempt_and_stuck_items() {
    StatusHarness(
        SyncStatusUiState(
            pendingCount = 4,
            lastAttemptFailed = true,
            lastSyncTime = ScreenshotTestData.baseInstant,
            groups = listOf(QueuedGroup(EntityType.NOTE, count = 4, retrying = 4)),
            failedCount = 2,
        ),
        feedback = SyncStatusFeedback.CouldNotStart,
    )
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun StatusSheet_Queue_unreadable() {
    StatusHarness(SyncStatusUiState(pendingCount = 19, queueUnavailable = true))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun StatusSheet_All_backed_up() {
    StatusHarness(SyncStatusUiState(lastSyncTime = ScreenshotTestData.baseInstant))
}

@Composable
private fun StatusHarness(
    uiState: SyncStatusUiState,
    feedback: SyncStatusFeedback? = null,
) {
    ScreenshotTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .systemBarsPadding()
                    .padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            SyncStatusContent(
                uiState = uiState,
                feedback = feedback,
                onSyncNow = {},
                onOpenSyncSettings = {},
                onOpenSyncIssues = {},
                onSignIn = {},
                modifier = Modifier.widthIn(max = 480.dp),
            )
        }
    }
}
