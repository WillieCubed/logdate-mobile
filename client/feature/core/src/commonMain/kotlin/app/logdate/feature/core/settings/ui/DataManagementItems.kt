@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.logdate.ui.common.SettingsRow
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.export
import logdate.client.feature.core.generated.resources.`import`
import logdate.client.feature.core.generated.resources.import_backup
import logdate.client.feature.core.generated.resources.restore_entries_from_a_logdate_export_archive
import logdate.client.feature.core.generated.resources.settings_export_entries_description
import logdate.client.feature.core.generated.resources.settings_export_entries_label
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExportDataItem(onShowExportOptions: () -> Unit) {
    SettingsRow(
        headlineContent = { Text(stringResource(Res.string.settings_export_entries_label)) },
        supportingContent = {
            Text(
                text = stringResource(Res.string.settings_export_entries_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Button(onClick = onShowExportOptions) {
                Text(stringResource(Res.string.export))
            }
        },
    )
}

@Composable
internal fun ImportBackupItem(onShowRestoreSheet: () -> Unit) {
    SettingsRow(
        headlineContent = { Text(stringResource(Res.string.import_backup)) },
        supportingContent = {
            Text(
                text = stringResource(Res.string.restore_entries_from_a_logdate_export_archive),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Button(onClick = onShowRestoreSheet) {
                Text(stringResource(Res.string.`import`))
            }
        },
    )
}
