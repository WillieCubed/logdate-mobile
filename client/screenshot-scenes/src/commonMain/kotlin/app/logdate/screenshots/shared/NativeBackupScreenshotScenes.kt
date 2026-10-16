@file:Suppress("ktlint:standard:function-naming")

package app.logdate.screenshots.shared

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.logdate.client.device.restore.NativeBackupQuotaFailure
import app.logdate.client.device.restore.NativeBackupStatus
import app.logdate.client.sync.SyncStatus
import app.logdate.feature.core.settings.ui.CloudArchivePhase
import app.logdate.feature.core.settings.ui.CloudArchiveStatus
import app.logdate.feature.core.settings.ui.StorageQuotaUi
import app.logdate.feature.core.settings.ui.SyncSettingsContent

/** Synthetic presentation states, shared by Android previews and desktop screenshot tests. */
internal val nativeBackupScenes =
    listOf(
        sharedScene(SharedScreenshotSceneId.NativeBackupSignedOut, ScreenshotSceneGroup.SETTINGS, osIntegrationVariants) {
            NativeBackupPage(authenticated = false)
        },
        sharedScene(SharedScreenshotSceneId.NativeBackupReady, ScreenshotSceneGroup.SETTINGS, osIntegrationVariants) {
            NativeBackupPage(authenticated = true)
        },
        sharedScene(SharedScreenshotSceneId.NativeBackupQuota, ScreenshotSceneGroup.SETTINGS, osIntegrationVariants) {
            NativeBackupPage(authenticated = true, quotaFailure = NativeBackupQuotaFailure(30_000_000, 25_000_000))
        },
    )

@Composable
private fun NativeBackupPage(
    authenticated: Boolean,
    quotaFailure: NativeBackupQuotaFailure? = null,
) {
    SyncSettingsContent(
        onBack = {},
        syncStatus = if (authenticated) SyncStatus(true, baseInstant, 0, false, false) else null,
        cloudArchiveStatus =
            CloudArchiveStatus(
                phase = if (authenticated) CloudArchivePhase.COMPLETE else CloudArchivePhase.SIGNED_OUT,
                lastCompletedAt = if (authenticated) baseInstant.toEpochMilliseconds() else null,
            ),
        nativeBackupStatus = NativeBackupStatus.Available(quotaFailure),
        isAuthenticated = authenticated,
        onSyncNow = {},
        onNavigateToSignIn = {},
        quotaUsage =
            StorageQuotaUi(
                totalBytes = 5_000_000_000,
                usedBytes = 0,
                usagePercentage = 0f,
                formattedTotal = "5 GB",
                formattedUsed = "0 GB",
            ),
        isQuotaAvailable = false,
        snackbarHostState = remember { SnackbarHostState() },
    )
}
