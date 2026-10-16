@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.logdate.client.device.restore.NativeBackupStatus
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.native_backup_cloud_detail
import logdate.client.feature.core.generated.resources.native_backup_cloud_requirement
import logdate.client.feature.core.generated.resources.native_backup_cloud_title
import logdate.client.feature.core.generated.resources.native_backup_export
import logdate.client.feature.core.generated.resources.native_backup_quota_failure
import logdate.client.feature.core.generated.resources.native_backup_title
import logdate.client.feature.core.generated.resources.native_backup_transfer_detail
import logdate.client.feature.core.generated.resources.native_backup_transfer_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NativeBackupSettingsSection(
    status: NativeBackupStatus,
    onExportArchive: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (status !is NativeBackupStatus.Available) return
    BackupSettingsGroup(stringResource(Res.string.native_backup_title), modifier) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth()) {
                NativeBackupCoverageRow(
                    Icons.Rounded.CloudQueue,
                    stringResource(Res.string.native_backup_cloud_title),
                    stringResource(Res.string.native_backup_cloud_detail),
                    stringResource(Res.string.native_backup_cloud_requirement),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                NativeBackupCoverageRow(
                    Icons.Rounded.Devices,
                    stringResource(Res.string.native_backup_transfer_title),
                    stringResource(Res.string.native_backup_transfer_detail),
                )
                if (status.lastQuotaFailure != null) {
                    Text(
                        stringResource(Res.string.native_backup_quota_failure),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                }
                if (onExportArchive != null) {
                    OutlinedButton(onClick = onExportArchive, modifier = Modifier.padding(Spacing.lg)) {
                        Text(stringResource(Res.string.native_backup_export))
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeBackupCoverageRow(
    icon: ImageVector,
    title: String,
    detail: String,
    requirement: String? = null,
) {
    Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (requirement != null) {
                Text(requirement, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
