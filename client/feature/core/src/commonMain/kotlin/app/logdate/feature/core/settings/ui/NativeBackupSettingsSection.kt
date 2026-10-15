@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.client.device.restore.NativeBackupStatus
import app.logdate.ui.common.SettingsSection
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
    onExportArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status !is NativeBackupStatus.Available) return
    SettingsSection(title = stringResource(Res.string.native_backup_title), modifier = modifier) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(Res.string.native_backup_cloud_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(Res.string.native_backup_cloud_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(Res.string.native_backup_cloud_requirement),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(Res.string.native_backup_transfer_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(Res.string.native_backup_transfer_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status.lastQuotaFailure != null) {
                Text(
                    stringResource(Res.string.native_backup_quota_failure),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onExportArchive) { Text(stringResource(Res.string.native_backup_export)) }
        }
    }
}
