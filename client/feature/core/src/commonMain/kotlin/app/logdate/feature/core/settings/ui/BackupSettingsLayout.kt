@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.client.sync.SyncStatus
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsNavigationItem
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.backup_archive_detail
import logdate.client.feature.core.generated.resources.backup_cloud_detail
import logdate.client.feature.core.generated.resources.backup_cloud_title
import logdate.client.feature.core.generated.resources.create_account
import logdate.client.feature.core.generated.resources.native_backup_export
import logdate.client.feature.core.generated.resources.sign_in
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
) {
    SettingsSection(stringResource(Res.string.backup_cloud_title), modifier) {
        if (isAuthenticated) {
            BackupStatusItem(syncStatus, cloudArchiveStatus, onSyncUsingMobileData)
        } else {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    stringResource(Res.string.backup_cloud_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(onClick = onCreateAccount) { Text(stringResource(Res.string.create_account)) }
                    TextButton(onClick = onSignIn) { Text(stringResource(Res.string.sign_in)) }
                }
            }
        }
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
