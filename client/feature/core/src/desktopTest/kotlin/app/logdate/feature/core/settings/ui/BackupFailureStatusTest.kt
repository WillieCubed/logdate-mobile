package app.logdate.feature.core.settings.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import app.logdate.client.sync.SyncStatus
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BackupFailureStatusTest {
    @Test
    fun encryptedCloudEntriesOfferAccountBoundPhraseRecoveryAtLargeText() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            var phraseOpens = 0
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    LogDateTheme(dynamicColor = false) {
                        BackupStatusItem(
                            syncStatus = status().copy(unreadableCloudCount = 1),
                            cloudArchiveStatus = CloudArchiveStatus(CloudArchivePhase.CHECKING),
                            onEnterRecoveryPhrase = { phraseOpens++ },
                        )
                    }
                }
            }
            onNodeWithText(
                "Some cloud entries could not be opened on this device. " +
                    "Check another signed-in device for a readable copy.",
            ).assertIsDisplayed()
            onNodeWithText("Enter recovery phrase").assertIsDisplayed().performClick()
            runOnIdle { assertEquals(1, phraseOpens) }
        }

    @Test
    fun unreadableLocalQueueDoesNotPromiseAutomaticRepair() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            setContent {
                LogDateTheme(dynamicColor = false) {
                    BackupStatusItem(
                        syncStatus = status().copy(queueReadable = false),
                        cloudArchiveStatus = CloudArchiveStatus(CloudArchivePhase.CHECKING),
                    )
                }
            }
            onNodeWithText(
                "Some local data is unavailable. Your readable journal remains here. " +
                    "If you saved an archive, import it from Settings > Data & Storage.",
            ).assertIsDisplayed()
        }

    private fun status() =
        SyncStatus(
            isEnabled = true,
            lastSyncTime = null,
            pendingUploads = 0,
            isSyncing = false,
            hasErrors = false,
        )
}
