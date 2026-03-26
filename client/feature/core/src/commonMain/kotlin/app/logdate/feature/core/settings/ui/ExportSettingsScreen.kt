@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.logdate.feature.core.export.ExportBottomSheet
import app.logdate.feature.core.export.ExportOptions
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.export.UserDataExportViewModel
import app.logdate.feature.core.restore.ImportOptions
import app.logdate.feature.core.restore.RestoreBottomSheet
import app.logdate.feature.core.restore.RestoreState
import app.logdate.feature.core.restore.UserDataRestoreViewModel
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.data_management
import logdate.client.feature.core.generated.resources.export
import logdate.client.feature.core.generated.resources.export_and_import
import logdate.client.feature.core.generated.resources.`import`
import logdate.client.feature.core.generated.resources.import_backup
import logdate.client.feature.core.generated.resources.restore_entries_from_a_logdate_export_archive
import logdate.client.feature.core.generated.resources.settings_export_entries_description
import logdate.client.feature.core.generated.resources.settings_export_entries_label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ExportSettingsScreen(
    onBack: () -> Unit,
    onBrowseFile: (String) -> Unit = {},
    exportViewModel: UserDataExportViewModel = koinViewModel(),
    restoreViewModel: UserDataRestoreViewModel = koinViewModel(),
) {
    val exportState by exportViewModel.exportState.collectAsState()
    val isExportSheetVisible by exportViewModel.isSheetVisible.collectAsState()
    val restoreState by restoreViewModel.restoreState.collectAsState()
    val isRestoreSheetVisible by restoreViewModel.isSheetVisible.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    ExportSettingsContent(
        onBack = onBack,
        exportState = exportState,
        isExportSheetVisible = isExportSheetVisible,
        onShowExportOptions = exportViewModel::showExportOptions,
        onUpdateExportOptions = exportViewModel::updateExportOptions,
        onConfirmExport = exportViewModel::confirmExport,
        onCancelExport = exportViewModel::cancelExport,
        onRetryExport = exportViewModel::retryExport,
        onDismissExport = exportViewModel::dismissSheet,
        onBrowseExport = { path -> onBrowseFile(path) },
        restoreState = restoreState,
        isRestoreSheetVisible = isRestoreSheetVisible,
        onShowRestoreSheet = restoreViewModel::showRestoreSheet,
        onSelectRestoreFile = restoreViewModel::selectFile,
        onUpdateImportOptions = restoreViewModel::updateImportOptions,
        onConfirmImport = restoreViewModel::confirmImport,
        onCancelRestore = restoreViewModel::cancelRestore,
        onRetryRestore = restoreViewModel::retryRestore,
        onDismissRestore = restoreViewModel::dismissSheet,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun ExportSettingsContent(
    onBack: () -> Unit,
    exportState: ExportState,
    isExportSheetVisible: Boolean = exportState !is ExportState.Idle,
    onShowExportOptions: () -> Unit,
    onUpdateExportOptions: (ExportOptions) -> Unit,
    onConfirmExport: () -> Unit,
    onCancelExport: () -> Unit,
    onRetryExport: () -> Unit,
    onDismissExport: () -> Unit,
    onBrowseExport: (String) -> Unit,
    restoreState: RestoreState,
    isRestoreSheetVisible: Boolean = restoreState !is RestoreState.Idle,
    onShowRestoreSheet: () -> Unit,
    onSelectRestoreFile: () -> Unit,
    onUpdateImportOptions: (ImportOptions) -> Unit,
    onConfirmImport: () -> Unit,
    onCancelRestore: () -> Unit,
    onRetryRestore: () -> Unit,
    onDismissRestore: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    if (isExportSheetVisible) {
        ExportBottomSheet(
            exportState = exportState,
            onOptionsChanged = onUpdateExportOptions,
            onConfirm = onConfirmExport,
            onCancel = onCancelExport,
            onRetry = onRetryExport,
            onDismiss = onDismissExport,
            onBrowse = onBrowseExport,
        )
    }

    if (isRestoreSheetVisible) {
        RestoreBottomSheet(
            restoreState = restoreState,
            onSelectFile = onSelectRestoreFile,
            onUpdateOptions = onUpdateImportOptions,
            onConfirmImport = onConfirmImport,
            onCancel = onCancelRestore,
            onRetry = onRetryRestore,
            onDismiss = onDismissRestore,
        )
    }

    SettingsScaffold(
        title = stringResource(Res.string.export_and_import),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) {
        item {
            SettingsSection(
                title = stringResource(Res.string.data_management),
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
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
        }
    }
}
