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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.export.ExportBottomSheet
import app.logdate.feature.core.export.ExportOptions
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.export.ExportViewModel
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.audit_and_repair_local_links_and_sync_metadata
import logdate.client.feature.core.generated.resources.automatically_sync_your_data_in_the_background
import logdate.client.feature.core.generated.resources.background_sync
import logdate.client.feature.core.generated.resources.cancel
import logdate.client.feature.core.generated.resources.check
import logdate.client.feature.core.generated.resources.checking
import logdate.client.feature.core.generated.resources.clear
import logdate.client.feature.core.generated.resources.cloud_sync
import logdate.client.feature.core.generated.resources.conflict_entity_with_id
import logdate.client.feature.core.generated.resources.conflicts_need_review
import logdate.client.feature.core.generated.resources.create_account
import logdate.client.feature.core.generated.resources.data_and_storage
import logdate.client.feature.core.generated.resources.data_management
import logdate.client.feature.core.generated.resources.detected_timestamp
import logdate.client.feature.core.generated.resources.export
import logdate.client.feature.core.generated.resources.exported_label
import logdate.client.feature.core.generated.resources.`import`
import logdate.client.feature.core.generated.resources.import_backup
import logdate.client.feature.core.generated.resources.importing
import logdate.client.feature.core.generated.resources.integrity_check
import logdate.client.feature.core.generated.resources.journals_count_with_comma
import logdate.client.feature.core.generated.resources.last_check_issue_count
import logdate.client.feature.core.generated.resources.last_sync_failed
import logdate.client.feature.core.generated.resources.last_synced_time
import logdate.client.feature.core.generated.resources.loading
import logdate.client.feature.core.generated.resources.loading_conflicts
import logdate.client.feature.core.generated.resources.media_count
import logdate.client.feature.core.generated.resources.never_synced
import logdate.client.feature.core.generated.resources.no_conflicts_waiting
import logdate.client.feature.core.generated.resources.notes_count_with_comma
import logdate.client.feature.core.generated.resources.queued_conflicts
import logdate.client.feature.core.generated.resources.refresh
import logdate.client.feature.core.generated.resources.repair
import logdate.client.feature.core.generated.resources.repairing
import logdate.client.feature.core.generated.resources.restore_entries_from_a_logdate_export_archive
import logdate.client.feature.core.generated.resources.restoring_backup
import logdate.client.feature.core.generated.resources.separator_pipe
import logdate.client.feature.core.generated.resources.settings_export_entries_description
import logdate.client.feature.core.generated.resources.settings_export_entries_label
import logdate.client.feature.core.generated.resources.showing_three_of_conflicts
import logdate.client.feature.core.generated.resources.sign_in
import logdate.client.feature.core.generated.resources.sign_in_to_your_logdate_cloud_account_to_enable_sync
import logdate.client.feature.core.generated.resources.sync_conflicts
import logdate.client.feature.core.generated.resources.sync_feature_access
import logdate.client.feature.core.generated.resources.sync_feature_backup
import logdate.client.feature.core.generated.resources.sync_feature_sync
import logdate.client.feature.core.generated.resources.sync_now
import logdate.client.feature.core.generated.resources.sync_status
import logdate.client.feature.core.generated.resources.syncing
import logdate.client.feature.core.generated.resources.waiting_for_backup_selection
import logdate.client.feature.core.generated.resources.warnings_count
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

/**
 * Data and storage settings screen.
 *
 * This screen automatically adapts to different screen sizes:
 * - Large screens: Acts as a detail pane with minimal header (when in two-pane layout)
 * - Small screens: Standard screen with back navigation
 *
 * @param onBack Callback for when the user presses the back button
 * @param onNavigateToSignIn Callback for navigating to sign-in when user taps sign-in CTA
 * @param viewModel ViewModel for the settings
 */
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
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

    DataSettingsContent(
        onBack = onBack,
        quotaUsage = uiState.quotaState.toStorageQuotaUi(),
        isQuotaAvailable = uiState.isQuotaAvailable,
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
        conflictsState = uiState.conflictsState,
        onClearConflicts = viewModel::clearConflicts,
        onRefreshConflicts = { viewModel.refreshConflicts(force = true) },
        snackbarHostState = snackbarHostState,
        syncStatus = uiState.syncStatus,
        isAuthenticated = uiState.isAuthenticated,
        onSyncNow = viewModel::syncNow,
        isBackgroundSyncEnabled = uiState.isBackgroundSyncEnabled,
        onBackgroundSyncEnabledChange = viewModel::setBackgroundSyncEnabled,
        onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
        onNavigateToSignIn = onNavigateToSignIn,
    )
}

@Composable
fun DataSettingsContent(
    onBack: () -> Unit,
    quotaUsage: StorageQuotaUi,
    isQuotaAvailable: Boolean,
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
    conflictsState: ConflictsState,
    onClearConflicts: () -> Unit,
    onRefreshConflicts: () -> Unit,
    snackbarHostState: SnackbarHostState,
    syncStatus: app.logdate.client.sync.SyncStatus? = null,
    isAuthenticated: Boolean = false,
    onSyncNow: () -> Unit = {},
    isBackgroundSyncEnabled: Boolean = true,
    onBackgroundSyncEnabledChange: (Boolean) -> Unit = {},
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
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
        title = stringResource(Res.string.data_and_storage),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) {
        if (isAuthenticated && isQuotaAvailable) {
            item {
                QuotaUsageBlock(
                    quotaUsage = quotaUsage,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }

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

        if (isAuthenticated) {
            item {
                SyncConflictsSection(
                    conflictsState = conflictsState,
                    onClearConflicts = onClearConflicts,
                    onRefreshConflicts = onRefreshConflicts,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }

        item {
            SyncSettingsSection(
                syncStatus = syncStatus,
                isAuthenticated = isAuthenticated,
                onSyncNow = onSyncNow,
                isBackgroundSyncEnabled = isBackgroundSyncEnabled,
                onBackgroundSyncEnabledChange = onBackgroundSyncEnabledChange,
                onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
                onNavigateToSignIn = onNavigateToSignIn,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
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
                IntegrityReportText(report, issueCount)
                IntegrityErrorText(integrityState.errorMessage)
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

@Composable
private fun IntegrityReportText(
    report: app.logdate.client.data.maintenance.IntegrityReport?,
    issueCount: Int,
) {
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
}

@Composable
private fun IntegrityErrorText(errorMessage: String?) {
    errorMessage?.let { message ->
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SyncConflictsSection(
    conflictsState: ConflictsState,
    onClearConflicts: () -> Unit,
    onRefreshConflicts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val conflictCount = conflictsState.conflicts.size
    SettingsSection(
        title = stringResource(Res.string.sync_conflicts),
        modifier = modifier,
    ) {
        Column {
            ConflictsSummaryItem(
                conflictsState = conflictsState,
                conflictCount = conflictCount,
                onClearConflicts = onClearConflicts,
                onRefreshConflicts = onRefreshConflicts,
            )
            conflictsState.conflicts.take(3).forEach { conflict ->
                ConflictDetailItem(conflict)
            }
            if (conflictCount > 3) {
                Text(
                    text =
                        stringResource(
                            Res.string.showing_three_of_conflicts,
                            conflictCount,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun ConflictsSummaryItem(
    conflictsState: ConflictsState,
    conflictCount: Int,
    onClearConflicts: () -> Unit,
    onRefreshConflicts: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.queued_conflicts)) },
        supportingContent = {
            Column {
                Text(
                    text =
                        if (conflictsState.isLoading) {
                            stringResource(Res.string.loading_conflicts)
                        } else if (conflictCount == 0) {
                            stringResource(Res.string.no_conflicts_waiting)
                        } else {
                            stringResource(
                                Res.string.conflicts_need_review,
                                conflictCount,
                                if (conflictCount == 1) "" else "s",
                            )
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                conflictsState.errorMessage?.let { message ->
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
            Row {
                TextButton(onClick = onRefreshConflicts) {
                    Text(stringResource(Res.string.refresh))
                }
                TextButton(
                    onClick = onClearConflicts,
                    enabled = conflictCount > 0,
                ) {
                    Text(stringResource(Res.string.clear))
                }
            }
        },
    )
}

@Composable
private fun ConflictDetailItem(conflict: app.logdate.client.sync.conflict.SyncConflictRecord) {
    ListItem(
        headlineContent = {
            Text(
                stringResource(
                    Res.string.conflict_entity_with_id,
                    conflict.entityType,
                    conflict.entityId,
                ),
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = conflict.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val timestamp = Instant.fromEpochMilliseconds(conflict.detectedAt)
                Text(
                    text =
                        stringResource(
                            Res.string.detected_timestamp,
                            timestamp.toReadableDateTimeShort(),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

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
private fun SyncSettingsSection(
    syncStatus: app.logdate.client.sync.SyncStatus?,
    isAuthenticated: Boolean,
    onSyncNow: () -> Unit,
    isBackgroundSyncEnabled: Boolean,
    onBackgroundSyncEnabledChange: (Boolean) -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.cloud_sync),
        modifier = modifier,
    ) {
        if (!isAuthenticated) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.sign_in_to_your_logdate_cloud_account_to_enable_sync),
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
                ToggleSettingsItem(
                    title = stringResource(Res.string.background_sync),
                    description = stringResource(Res.string.automatically_sync_your_data_in_the_background),
                    checked = isBackgroundSyncEnabled,
                    onCheckedChange = onBackgroundSyncEnabledChange,
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
        headlineContent = { Text(stringResource(Res.string.sync_status)) },
        supportingContent = {
            SyncStatusText(syncStatus)
        },
        trailingContent = {
            Button(
                onClick = onSyncNow,
                enabled = syncStatus?.isSyncing != true,
            ) {
                Text(stringResource(Res.string.sync_now))
            }
        },
    )
}

@Composable
private fun SyncStatusText(syncStatus: app.logdate.client.sync.SyncStatus?) {
    syncStatus?.let { status ->
        if (status.isSyncing) {
            Text(stringResource(Res.string.syncing), color = MaterialTheme.colorScheme.primary)
        } else {
            val statusText =
                if (status.hasErrors) {
                    stringResource(Res.string.last_sync_failed)
                } else {
                    status.lastSyncTime?.let {
                        stringResource(Res.string.last_synced_time, it.toReadableDateTimeShort())
                    } ?: stringResource(Res.string.never_synced)
                }
            Text(
                text = statusText,
                color =
                    if (status.hasErrors) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    } ?: Text(stringResource(Res.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Preview
@Composable
private fun DataSettingsScreenPreview() {
    DataSettingsContent(
        onBack = {},
        quotaUsage =
            StorageQuotaUi(
                totalBytes = 100_000_000_000L,
                usedBytes = 0L,
                usagePercentage = 0f,
                formattedTotal = "100 GB",
                formattedUsed = "0 B",
                categories = emptyList(),
            ),
        isQuotaAvailable = true,
        exportState = ExportState.Idle,
        onShowExportOptions = {},
        onUpdateExportOptions = {},
        onConfirmExport = {},
        onCancelExport = {},
        onRetryExport = {},
        onDismissExport = {},
        onBrowseExport = {},
        onRestoreContent = {},
        onCancelRestore = {},
        restoreState = RestoreState.Idle,
        integrityState = IntegrityState(),
        onRunIntegrityCheck = {},
        onRepairIntegrity = {},
        conflictsState = ConflictsState(),
        onClearConflicts = {},
        onRefreshConflicts = {},
        snackbarHostState = remember { SnackbarHostState() },
        isBackgroundSyncEnabled = true,
    )
}
