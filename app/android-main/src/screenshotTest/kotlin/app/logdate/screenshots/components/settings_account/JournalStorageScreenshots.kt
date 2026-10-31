package app.logdate.screenshots.components.settings_account

import androidx.compose.runtime.Composable
import app.logdate.client.sync.space.migration.JournalDataKind
import app.logdate.client.sync.space.migration.JournalMoveProblem
import app.logdate.client.sync.space.migration.JournalMoveState
import app.logdate.feature.core.settings.ui.JournalStorageSection
import app.logdate.screenshots.common.OsIntegrationPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.screenshots.shared.SharedScreenshotScene
import app.logdate.screenshots.shared.SharedScreenshotSceneId
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Duration.Companion.days

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun JournalStoragePaused() {
    SettingsSectionScreenshot {
        JournalStorageSection(
            state = JournalMoveState.Stopped(JournalMoveProblem.MissingOnThisDevice(JournalDataKind.ENTRIES, 3), retryAt = null),
            onTryAgain = {},
            onSwitchBack = {},
            onEnterRecoveryPhrase = {},
        )
    }
}

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun JournalStorageSealed() {
    SettingsSectionScreenshot {
        JournalStorageSection(
            state = JournalMoveState.Moved(previousCopyKeptUntil = ScreenshotTestData.baseInstant + 30.days),
            onTryAgain = {},
            onSwitchBack = {},
            onEnterRecoveryPhrase = {},
        )
    }
}

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun SyncUpdateRequired() {
    ScreenshotTheme { SharedScreenshotScene(SharedScreenshotSceneId.SyncUpdateRequired) }
}

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun StoragePreviews() {
    ScreenshotTheme { SharedScreenshotScene(SharedScreenshotSceneId.StoragePreviews) }
}
