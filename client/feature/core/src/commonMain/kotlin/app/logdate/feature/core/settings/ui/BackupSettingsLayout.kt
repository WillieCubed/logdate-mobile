@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.client.sync.SyncStatus
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsNavigationItem
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.backup_archive_detail
import logdate.client.feature.core.generated.resources.backup_cloud_detail
import logdate.client.feature.core.generated.resources.backup_cloud_title
import logdate.client.feature.core.generated.resources.native_backup_export
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BackupCloudSection(
    isAuthenticated: Boolean,
    syncStatus: SyncStatus?,
    cloudArchiveStatus: CloudArchiveStatus,
    onSyncUsingMobileData: () -> Unit,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    onUpdateApp: (() -> Unit)? = null,
    onEnterRecoveryPhrase: (() -> Unit)? = null,
    onArchiveBackupNow: () -> Unit = {},
    reviewableIssueCount: Int = 0,
) {
    if (isAuthenticated) {
        BackupStatusItem(
            syncStatus = syncStatus,
            cloudArchiveStatus = cloudArchiveStatus,
            onSyncUsingMobileData = onSyncUsingMobileData,
            onArchiveBackupNow = onArchiveBackupNow,
            reviewableIssueCount = reviewableIssueCount,
            modifier = modifier,
            onUpdateApp = onUpdateApp,
            onEnterRecoveryPhrase = onEnterRecoveryPhrase,
        )
    } else {
        SyncPromotionCard(
            onCreateAccount = onCreateAccount,
            onSignIn = onSignIn,
            title = stringResource(Res.string.backup_cloud_title),
            description = stringResource(Res.string.backup_cloud_detail),
            modifier = modifier,
        )
    }
}

@Composable
internal fun BackupArchiveItem(
    onExportArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MaterialContainer(modifier = modifier) {
        SettingsNavigationItem(
            title = stringResource(Res.string.native_backup_export),
            description = stringResource(Res.string.backup_archive_detail),
            icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
            onClick = onExportArchive,
        )
    }
}
