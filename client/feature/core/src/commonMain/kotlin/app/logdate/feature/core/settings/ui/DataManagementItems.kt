@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_export_entries_label)) },
        supportingContent = {
            Text(stringResource(Res.string.settings_export_entries_description))
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
    ListItem(
        headlineContent = { Text(stringResource(Res.string.import_backup)) },
        supportingContent = {
            Text(stringResource(Res.string.restore_entries_from_a_logdate_export_archive))
        },
        trailingContent = {
            Button(onClick = onShowRestoreSheet) {
                Text(stringResource(Res.string.`import`))
            }
        },
    )
}
