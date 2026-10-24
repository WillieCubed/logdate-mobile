@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun SyncSettingsSection(
    syncStatus: app.logdate.client.sync.SyncStatus?,
    cloudArchiveStatus: CloudArchiveStatus,
    isAuthenticated: Boolean,
    onSyncUsingMobileData: () -> Unit = {},
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    onEnterRecoveryPhrase: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BackupCloudSection(
        isAuthenticated = isAuthenticated,
        syncStatus = syncStatus,
        cloudArchiveStatus = cloudArchiveStatus,
        onSyncUsingMobileData = onSyncUsingMobileData,
        onCreateAccount = onNavigateToCloudAccountCreation,
        onSignIn = onNavigateToSignIn,
        onEnterRecoveryPhrase = onEnterRecoveryPhrase,
        modifier = modifier,
    )
}
