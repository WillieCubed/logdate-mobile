@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.sync.SyncProgressIndicator
import app.logdate.feature.core.sync.accountSyncStatus
import app.logdate.feature.core.sync.messageResource
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_sign_in_to_enable_sync
import logdate.client.feature.core.generated.resources.cloud_archive_title
import logdate.client.feature.core.generated.resources.create_account
import logdate.client.feature.core.generated.resources.entry_sync_title
import logdate.client.feature.core.generated.resources.sign_in
import logdate.client.feature.core.generated.resources.sync_feature_access
import logdate.client.feature.core.generated.resources.sync_feature_backup
import logdate.client.feature.core.generated.resources.sync_feature_sync
import logdate.client.ui.generated.resources.common_loading
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

@Composable
private fun SyncFeatureRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun SyncSettingsSection(
    syncStatus: app.logdate.client.sync.SyncStatus?,
    isAuthenticated: Boolean,
    onSyncNow: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.entry_sync_title),
        modifier = modifier,
    ) {
        if (!isAuthenticated) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.account_sign_in_to_enable_sync),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                SyncFeatureRow(
                    icon = Icons.Rounded.CloudDone,
                    text = stringResource(Res.string.sync_feature_backup),
                )
                SyncFeatureRow(
                    icon = Icons.Rounded.Devices,
                    text = stringResource(Res.string.sync_feature_access),
                )
                SyncFeatureRow(
                    icon = Icons.Rounded.Sync,
                    text = stringResource(Res.string.sync_feature_sync),
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(onClick = onNavigateToCloudAccountCreation) {
                        Text(stringResource(Res.string.create_account))
                    }
                    OutlinedButton(onClick = onNavigateToSignIn) {
                        Text(stringResource(Res.string.sign_in))
                    }
                }
            }
        } else {
            Column {
                SyncStatusItem(
                    syncStatus = syncStatus,
                    onSyncNow = onSyncNow,
                )
            }
        }
    }
}

@Composable
private fun SyncStatusItem(
    syncStatus: app.logdate.client.sync.SyncStatus?,
    onSyncNow: () -> Unit,
) {
    ListItem(
        headlineContent = { SyncStatusText(syncStatus) },
        leadingContent = {
            if (syncStatus?.isSyncing == true) {
                SyncProgressIndicator(
                    total = syncStatus.totalForRun,
                    completed = syncStatus.completedInRun,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    )
}

@Composable
internal fun CloudArchiveSection(
    status: CloudArchiveStatus,
    onArchiveBackupNow: () -> Unit,
    onNavigateToRecoveryPhrase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(Res.string.cloud_archive_title), modifier = modifier) {
        ListItem(headlineContent = { Text(stringResource(status.messageResource())) })
    }
}

@Composable
private fun SyncStatusText(syncStatus: app.logdate.client.sync.SyncStatus?) {
    syncStatus?.let { status ->
        Text(
            text = stringResource(accountSyncStatus(status).messageResource()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } ?: Text(stringResource(UiRes.string.common_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
}
