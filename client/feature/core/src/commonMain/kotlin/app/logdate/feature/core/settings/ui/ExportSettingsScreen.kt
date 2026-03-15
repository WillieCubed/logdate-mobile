@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.logdate.feature.core.export.ExportBottomSheet
import app.logdate.feature.core.export.ExportOptions
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.export.ExportViewModel
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.audit_and_repair_local_links_and_sync_metadata
import logdate.client.feature.core.generated.resources.cancel
import logdate.client.feature.core.generated.resources.check
import logdate.client.feature.core.generated.resources.checking
import logdate.client.feature.core.generated.resources.data_management
import logdate.client.feature.core.generated.resources.export
import logdate.client.feature.core.generated.resources.export_and_import
import logdate.client.feature.core.generated.resources.exported_label
import logdate.client.feature.core.generated.resources.`import`
import logdate.client.feature.core.generated.resources.import_backup
import logdate.client.feature.core.generated.resources.importing
import logdate.client.feature.core.generated.resources.integrity_check
import logdate.client.feature.core.generated.resources.journals_count_with_comma
import logdate.client.feature.core.generated.resources.last_check_issue_count
import logdate.client.feature.core.generated.resources.media_count
import logdate.client.feature.core.generated.resources.notes_count_with_comma
import logdate.client.feature.core.generated.resources.repair
import logdate.client.feature.core.generated.resources.repairing
import logdate.client.feature.core.generated.resources.restore_entries_from_a_logdate_export_archive
import logdate.client.feature.core.generated.resources.restoring_backup
import logdate.client.feature.core.generated.resources.separator_pipe
import logdate.client.feature.core.generated.resources.settings_export_entries_description
import logdate.client.feature.core.generated.resources.settings_export_entries_label
import logdate.client.feature.core.generated.resources.waiting_for_backup_selection
import logdate.client.feature.core.generated.resources.warnings_count
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ExportSettingsScreen(
    onBack: () -> Unit,
    onBrowseFile: (String) -> Unit = {},
    viewModel: DataSettingsViewModel = koinViewModel(),
    exportViewModel: ExportViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val exportState by exportViewModel.exportState.collectAsState()
    val isExportSheetVisible by exportViewModel.isSheetVisible.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.restoreState) {
        when (val state = uiState.restoreState) {
            is RestoreState.Completed -> {
                if (state.showSnackbar) {
                    scope.launch {
                        val warningCount = state.summary.warnings.size
                        val message =
                            if (warningCount > 0) {
                                "Restore completed with $warningCount warnings"
                            } else {
                                "Restore completed successfully"
                            }
                        snackbarHostState.showSnackbar(message)
                        viewModel.markRestoreSnackbarShown()
                    }
                }
            }
            is RestoreState.Failed -> {
                if (state.showSnackbar) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Restore failed: ${state.message}")
                        viewModel.markRestoreSnackbarShown()
                    }
                }
            }
            else -> Unit
        }
    }

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
        onRestoreContent = viewModel::restoreContent,
        onCancelRestore = viewModel::cancelRestore,
        restoreState = uiState.restoreState,
        integrityState = uiState.integrityState,
        onRunIntegrityCheck = viewModel::runIntegrityCheck,
        onRepairIntegrity = viewModel::repairIntegrity,
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
    onRestoreContent: () -> Unit,
    onCancelRestore: () -> Unit,
    restoreState: RestoreState,
    integrityState: IntegrityState,
    onRunIntegrityCheck: () -> Unit,
    onRepairIntegrity: () -> Unit,
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
                Column {
                    ExportDataItem(onShowExportOptions = onShowExportOptions)
                    ImportBackupItem(
                        restoreState = restoreState,
                        onRestoreContent = onRestoreContent,
                        onCancelRestore = onCancelRestore,
                    )
                    IntegrityCheckItem(
                        integrityState = integrityState,
                        onRunIntegrityCheck = onRunIntegrityCheck,
                        onRepairIntegrity = onRepairIntegrity,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportDataItem(onShowExportOptions: () -> Unit) {
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
private fun ImportBackupItem(
    restoreState: RestoreState,
    onRestoreContent: () -> Unit,
    onCancelRestore: () -> Unit,
) {
    val restoreInProgress = restoreState is RestoreState.Selecting || restoreState is RestoreState.Restoring
    ListItem(
        headlineContent = { Text(stringResource(Res.string.import_backup)) },
        supportingContent = {
            Column {
                Text(stringResource(Res.string.restore_entries_from_a_logdate_export_archive))
                RestoreStatusText(restoreState)
            }
        },
        trailingContent = {
            Column {
                Button(
                    onClick = onRestoreContent,
                    enabled = !restoreInProgress,
                ) {
                    Text(
                        if (restoreInProgress) {
                            stringResource(Res.string.importing)
                        } else {
                            stringResource(Res.string.`import`)
                        },
                    )
                }
                if (restoreInProgress) {
                    TextButton(onClick = onCancelRestore) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            }
        },
    )
}

@Composable
private fun RestoreStatusText(restoreState: RestoreState) {
    when (val state = restoreState) {
        is RestoreState.Selecting -> {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(Res.string.waiting_for_backup_selection),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is RestoreState.Restoring -> {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(Res.string.restoring_backup),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        is RestoreState.Completed -> {
            Spacer(modifier = Modifier.height(Spacing.xs))
            RestoreCompletedSummary(state)
        }
        is RestoreState.Failed -> {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        else -> Unit
    }
}

@Composable
private fun RestoreCompletedSummary(state: RestoreState.Completed) {
    val exportedAt = state.summary.exportDate?.toReadableDateTimeShort()
    val summaryLine =
        buildString {
            if (exportedAt != null) {
                append(stringResource(Res.string.exported_label))
                append(exportedAt)
                append(stringResource(Res.string.separator_pipe))
            }
            append(stringResource(Res.string.journals_count_with_comma, state.summary.journalsImported))
            append(stringResource(Res.string.notes_count_with_comma, state.summary.notesImported))
            append(stringResource(Res.string.media_count, state.summary.mediaImported))
        }
    Text(
        text = summaryLine,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.summary.warnings.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.warnings_count, state.summary.warnings.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun IntegrityCheckItem(
    integrityState: IntegrityState,
    onRunIntegrityCheck: () -> Unit,
    onRepairIntegrity: () -> Unit,
) {
    val report = integrityState.lastReport
    val issueCount =
        report?.let {
            it.orphanedJournalLinks +
                it.orphanedContentLinks +
                it.pendingMissingJournals +
                it.pendingMissingNotes +
                it.pendingAssociationMissingLinks +
                it.pendingAssociationMalformed
        } ?: 0
    ListItem(
        headlineContent = { Text(stringResource(Res.string.integrity_check)) },
        supportingContent = {
            Column {
                Text(stringResource(Res.string.audit_and_repair_local_links_and_sync_metadata))
                report?.let {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text =
                            stringResource(
                                Res.string.last_check_issue_count,
                                it.checkedAt.toReadableDateTimeShort(),
                                issueCount,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                integrityState.errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        trailingContent = {
            Column {
                Button(
                    onClick = onRunIntegrityCheck,
                    enabled = !integrityState.isChecking,
                ) {
                    Text(
                        if (integrityState.isChecking) {
                            stringResource(Res.string.checking)
                        } else {
                            stringResource(Res.string.check)
                        },
                    )
                }
                TextButton(
                    onClick = onRepairIntegrity,
                    enabled = issueCount > 0 && !integrityState.isRepairing,
                ) {
                    Text(
                        if (integrityState.isRepairing) {
                            stringResource(Res.string.repairing)
                        } else {
                            stringResource(Res.string.repair)
                        },
                    )
                }
            }
        },
    )
}
