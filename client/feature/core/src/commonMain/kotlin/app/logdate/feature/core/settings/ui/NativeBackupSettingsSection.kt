@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.client.device.restore.NativeBackupStatus
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.SimpleSettingsItem
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
    val cloudDescription =
        listOfNotNull(
            stringResource(Res.string.native_backup_cloud_detail),
            stringResource(Res.string.native_backup_cloud_requirement),
            if (status.lastQuotaFailure != null) stringResource(Res.string.native_backup_quota_failure) else null,
        ).joinToString("\n")
    SettingsSection(stringResource(Res.string.native_backup_title), modifier) {
        SimpleSettingsItem(
            title = stringResource(Res.string.native_backup_cloud_title),
            leadingContent = { Icon(Icons.Rounded.CloudQueue, contentDescription = null) },
            description = cloudDescription,
            onClick = null,
            action = {},
        )
        SimpleSettingsItem(
            title = stringResource(Res.string.native_backup_transfer_title),
            leadingContent = { Icon(Icons.Rounded.Devices, contentDescription = null) },
            description = stringResource(Res.string.native_backup_transfer_detail),
            onClick = null,
            action = {},
        )
        if (onExportArchive != null) {
            OutlinedButton(onClick = onExportArchive, modifier = Modifier.padding(Spacing.lg)) {
                Text(stringResource(Res.string.native_backup_export))
            }
        }
    }
}
