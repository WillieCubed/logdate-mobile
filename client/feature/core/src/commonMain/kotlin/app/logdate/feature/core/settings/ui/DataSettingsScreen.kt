@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.core.settings.ui.LocalSettingsLayoutInfo
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.settings_export_entries_description
import logdate.client.feature.core.generated.resources.settings_export_entries_label
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
 * @param viewModel ViewModel for the settings
 */
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    viewModel: DataSettingsViewModel = koinViewModel(),
    isPotentialDetailPane: Boolean? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val layoutInfo = LocalSettingsLayoutInfo.current
    val resolvedIsDetailPane = isPotentialDetailPane ?: layoutInfo.isDetailPane
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show a snackbar when export is complete
    LaunchedEffect(uiState.exportState) {
        if (uiState.exportState is ExportState.Selected) {
            val state = uiState.exportState as ExportState.Selected
            if (state.showSnackbar) {
                scope.launch {
                    snackbarHostState.showSnackbar("Export completed successfully")
                    // Mark the snackbar as shown to prevent showing it again on recomposition
                    viewModel.markExportSnackbarShown()
                }
            }
        }
    }

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
        onExportContent = viewModel::exportContent,
        exportState = uiState.exportState,
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
        isPotentialDetailPane = resolvedIsDetailPane,
        syncStatus = uiState.syncStatus,
        isAuthenticated = uiState.isAuthenticated,
        onSyncNow = viewModel::syncNow,
        isBackgroundSyncEnabled = uiState.isBackgroundSyncEnabled,
        onBackgroundSyncEnabledChange = viewModel::setBackgroundSyncEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataSettingsContent(
    onBack: () -> Unit,
    quotaUsage: StorageQuotaUi,
    onExportContent: () -> Unit,
    exportState: ExportState,
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
    isPotentialDetailPane: Boolean = false,
    syncStatus: app.logdate.client.sync.SyncStatus? = null,
    isAuthenticated: Boolean = false,
    onSyncNow: () -> Unit = {},
    isBackgroundSyncEnabled: Boolean = true,
    onBackgroundSyncEnabledChange: (Boolean) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier =
            Modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Only show top bar with back button in single-pane mode
            if (!isPotentialDetailPane) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.data_and_storage)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                // Section title for two-pane mode
                if (isPotentialDetailPane) {
                    item {
                        Text(
                            text = stringResource(Res.string.data_and_storage),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        )
                    }
                }
                // Storage quota section
                item {
                    QuotaUsageBlock(
                        quotaUsage = quotaUsage,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }

                // Data export/import section
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = stringResource(Res.string.data_management),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        MaterialContainer {
                            Column {
                                ListItem(
                                    headlineContent = { Text(stringResource(Res.string.settings_export_entries_label)) },
                                    supportingContent = {
                                        Column {
                                            Text(stringResource(Res.string.settings_export_entries_description))

                                            // Show export location if available
                                            if (exportState is ExportState.Selected) {
                                                val path = exportState.path
                                                Spacer(modifier = Modifier.height(Spacing.xs))
                                                Text(
                                                    text =
                                                        stringResource(
                                                            Res.string.last_export_path,
                                                            path,
                                                        ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    },
                                    trailingContent = {
                                        Button(
                                            onClick = onExportContent,
                                            enabled = exportState != ExportState.Selecting,
                                        ) {
                                            Text(stringResource(Res.string.export))
                                        }
                                    },
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = Spacing.md),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                )

                                val restoreInProgress = restoreState is RestoreState.Selecting || restoreState is RestoreState.Restoring
                                ListItem(
                                    headlineContent = { Text(stringResource(Res.string.import_backup)) },
                                    supportingContent = {
                                        Column {
                                            Text(stringResource(Res.string.restore_entries_from_a_logdate_export_archive))
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
                                                    val exportedAt = state.summary.exportDate?.toReadableDateTimeShort()
                                                    val summaryLine =
                                                        buildString {
                                                            if (exportedAt != null) {
                                                                append(
                                                                    stringResource(
                                                                        Res.string.exported_label,
                                                                    ),
                                                                )
                                                                append(exportedAt)
                                                                append(
                                                                    stringResource(
                                                                        Res.string.separator_pipe,
                                                                    ),
                                                                )
                                                            }
                                                            append(
                                                                stringResource(
                                                                    Res.string.journals_count_with_comma,
                                                                    state.summary.journalsImported,
                                                                ),
                                                            )
                                                            append(
                                                                stringResource(
                                                                    Res.string.notes_count_with_comma,
                                                                    state.summary.notesImported,
                                                                ),
                                                            )
                                                            append(
                                                                stringResource(
                                                                    Res.string.media_count,
                                                                    state.summary.mediaImported,
                                                                ),
                                                            )
                                                        }
                                                    Text(
                                                        text = summaryLine,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    if (state.summary.warnings.isNotEmpty()) {
                                                        Text(
                                                            text =
                                                                stringResource(
                                                                    Res.string.warnings_count,
                                                                    state.summary.warnings.size,
                                                                ),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.error,
                                                        )
                                                    }
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

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = Spacing.md),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                )

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
                                                val summary =
                                                    stringResource(
                                                        Res.string.last_check_issue_count,
                                                        it.checkedAt.toReadableDateTimeShort(),
                                                        issueCount,
                                                    )
                                                Text(
                                                    text = summary,
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
                        }
                    }
                }

                // Sync conflict queue
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = stringResource(Res.string.sync_conflicts),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        MaterialContainer {
                            Column {
                                val conflictCount = conflictsState.conflicts.size
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

                                conflictsState.conflicts.take(3).forEach { conflict ->
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = Spacing.md),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    )
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
                                if (conflictCount > 3) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = Spacing.md),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    )
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
                }

                // Sync settings
                item {
                    SyncSettingsSection(
                        syncStatus = syncStatus,
                        isAuthenticated = isAuthenticated,
                        onSyncNow = onSyncNow,
                        isBackgroundSyncEnabled = isBackgroundSyncEnabled,
                        onBackgroundSyncEnabledChange = onBackgroundSyncEnabledChange,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncSettingsSection(
    syncStatus: app.logdate.client.sync.SyncStatus?,
    isAuthenticated: Boolean,
    onSyncNow: () -> Unit,
    isBackgroundSyncEnabled: Boolean,
    onBackgroundSyncEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(Res.string.cloud_sync),
            style = MaterialTheme.typography.titleMedium,
        )

        MaterialContainer {
            if (!isAuthenticated) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.sign_in_required)) },
                    supportingContent = {
                        Text(
                            text = stringResource(Res.string.sign_in_to_your_logdate_cloud_account_to_enable_sync),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            } else {
                // Sync status
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.sync_status)) },
                    supportingContent = {
                        Column {
                            syncStatus?.let { status ->
                                if (status.isSyncing) {
                                    Text(stringResource(Res.string.syncing), color = MaterialTheme.colorScheme.primary)
                                } else {
                                    val statusText =
                                        if (status.hasErrors) {
                                            "Last sync failed: ${status.lastError?.message ?: "Unknown error"}"
                                        } else {
                                            status.lastSyncTime?.let {
                                                "Last synced: ${it.toReadableDateTimeShort()}"
                                            } ?: "Never synced"
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

                // Background sync setting
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.background_sync)) },
                    supportingContent = { Text(stringResource(Res.string.automatically_sync_your_data_in_the_background)) },
                    trailingContent = {
                        Switch(
                            checked = isBackgroundSyncEnabled,
                            onCheckedChange = onBackgroundSyncEnabledChange,
                        )
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun DataSettingsScreenPreview() {
    DataSettingsContent(
        onBack = {},
        quotaUsage =
            StorageQuotaUi(
                totalBytes = 100_000_000_000L, // 100GB default
                usedBytes = 0L,
                usagePercentage = 0f,
                formattedTotal = "100 GB",
                formattedUsed = "0 B",
                categories = emptyList(),
            ),
        onExportContent = {},
        exportState = ExportState.Idle,
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
