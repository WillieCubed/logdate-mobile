@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import app.logdate.ui.common.SettingsNavigationItem
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.import_backup
import logdate.client.feature.core.generated.resources.restore_entries_from_a_logdate_export_archive
import logdate.client.feature.core.generated.resources.settings_export_entries_description
import logdate.client.feature.core.generated.resources.settings_export_entries_label
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExportDataItem(onShowExportOptions: () -> Unit) {
    SettingsNavigationItem(
        title = stringResource(Res.string.settings_export_entries_label),
        description = stringResource(Res.string.settings_export_entries_description),
        icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
        onClick = onShowExportOptions,
    )
}

@Composable
internal fun ImportBackupItem(onShowRestoreSheet: () -> Unit) {
    SettingsNavigationItem(
        title = stringResource(Res.string.import_backup),
        description = stringResource(Res.string.restore_entries_from_a_logdate_export_archive),
        icon = { Icon(Icons.Rounded.Upload, contentDescription = null) },
        onClick = onShowRestoreSheet,
    )
}
