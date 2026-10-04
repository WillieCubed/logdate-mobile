package app.logdate.feature.core.settings.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncStatus
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AutomaticSyncSettingsTest {
    @Test
    fun backupFailuresDoNotCreateTroubleshootingChores() =
        runDesktopComposeUiTest(width = 720, height = 900) {
            setContent {
                LogDateTheme {
                    SyncSettingsContent(
                        onBack = {},
                        syncStatus = SyncStatus(true, null, 1, false, true, pausedReason = SyncPausedReason.NEEDS_RECOVERY_PHRASE),
                        cloudArchiveStatus = CloudArchiveStatus(CloudArchivePhase.NEEDS_RECOVERY),
                        isAuthenticated = true,
                        onSyncNow = {},
                        onNavigateToSignIn = {},
                        quotaUsage = StorageQuotaUi(100, 0, 0f, formattedTotal = "100 B", formattedUsed = "0 B"),
                        isQuotaAvailable = true,
                        snackbarHostState = SnackbarHostState(),
                    )
                }
            }
            onNodeWithText("Sync entries").assertDoesNotExist()
            onNodeWithText("Enter recovery phrase").assertDoesNotExist()
            onAllNodesWithText("Waiting to sync").assertCountEquals(2)
        }
}
