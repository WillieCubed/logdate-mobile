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
import logdate.client.feature.core.generated.resources.native_backup_coverage
import logdate.client.feature.core.generated.resources.native_backup_export
import logdate.client.feature.core.generated.resources.native_backup_quota_failure
import logdate.client.feature.core.generated.resources.native_backup_title
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
            Text(stringResource(Res.string.native_backup_coverage), style = MaterialTheme.typography.bodyMedium)
            if (status.lastQuotaFailure != null) {
                Text(
                    stringResource(Res.string.native_backup_quota_failure),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(onClick = onExportArchive) { Text(stringResource(Res.string.native_backup_export)) }
        }
    }
}
