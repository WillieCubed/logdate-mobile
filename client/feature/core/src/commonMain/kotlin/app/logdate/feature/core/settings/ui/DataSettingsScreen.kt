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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
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
// CloudStorageQuota import is not needed, removed
import app.logdate.feature.core.settings.ui.LocalSettingsLayoutInfo
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import kotlin.time.Instant
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.settings_export_entries_description
import logdate.client.feature.core.generated.resources.settings_export_entries_label
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

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
                        val message = if (warningCount > 0) {
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
        onBackgroundSyncEnabledChange = viewModel::setBackgroundSyncEnabled
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
    onBackgroundSyncEnabledChange: (Boolean) -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    Scaffold(
        modifier = Modifier
            .applyScreenStyles()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Only show top bar with back button in single-pane mode
            if (!isPotentialDetailPane) {
                TopAppBar(
                    title = { Text("Data & Storage") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Section title for two-pane mode
                if (isPotentialDetailPane) {
                    item {
                        Text(
                            text = "Data & Storage",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    }
                }
            // Storage quota section
            item {
                QuotaUsageBlock(
                    quotaUsage = quotaUsage,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }
            
            // Data export/import section
            item {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Data Management",
                        style = MaterialTheme.typography.titleMedium
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
                                                text = "Last export: $path",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                trailingContent = {
                                    Button(
                                        onClick = onExportContent,
                                        enabled = exportState != ExportState.Selecting
                                    ) {
                                        Text("Export")
                                    }
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = Spacing.md),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )

                            val restoreInProgress = restoreState is RestoreState.Selecting || restoreState is RestoreState.Restoring
                            ListItem(
                                headlineContent = { Text("Import backup") },
                                supportingContent = {
                                    Column {
                                        Text("Restore entries from a LogDate export archive")
                                        when (val state = restoreState) {
                                            is RestoreState.Selecting -> {
                                                Spacer(modifier = Modifier.height(Spacing.xs))
                                                Text(
                                                    text = "Waiting for backup selection...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            is RestoreState.Restoring -> {
                                                Spacer(modifier = Modifier.height(Spacing.xs))
                                                Text(
                                                    text = "Restoring backup...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            is RestoreState.Completed -> {
                                                Spacer(modifier = Modifier.height(Spacing.xs))
                                                val exportedAt = state.summary.exportDate?.toReadableDateTimeShort()
                                                val summaryLine = buildString {
                                                    if (exportedAt != null) {
                                                        append("Exported: ")
                                                        append(exportedAt)
                                                        append(" | ")
                                                    }
                                                    append("${state.summary.journalsImported} journals, ")
                                                    append("${state.summary.notesImported} notes, ")
                                                    append("${state.summary.mediaImported} media")
                                                }
                                                Text(
                                                    text = summaryLine,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (state.summary.warnings.isNotEmpty()) {
                                                    Text(
                                                        text = "Warnings: ${state.summary.warnings.size}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                            is RestoreState.Failed -> {
                                                Spacer(modifier = Modifier.height(Spacing.xs))
                                                Text(
                                                    text = state.message,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
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
                                            enabled = !restoreInProgress
                                        ) {
                                            Text(if (restoreInProgress) "Importing..." else "Import")
                                        }
                                        if (restoreInProgress) {
                                            TextButton(onClick = onCancelRestore) {
                                                Text("Cancel")
                                            }
                                        }
                                    }
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = Spacing.md),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )

                            val report = integrityState.lastReport
                            val issueCount = report?.let {
                                it.orphanedJournalLinks +
                                    it.orphanedContentLinks +
                                    it.pendingMissingJournals +
                                    it.pendingMissingNotes +
                                    it.pendingAssociationMissingLinks +
                                    it.pendingAssociationMalformed
                            } ?: 0
                            ListItem(
                                headlineContent = { Text("Integrity check") },
                                supportingContent = {
                                    Column {
                                        Text("Audit and repair local links and sync metadata")
                                        report?.let {
                                            Spacer(modifier = Modifier.height(Spacing.xs))
                                            val summary = "Last check: ${it.checkedAt.toReadableDateTimeShort()} | $issueCount issues"
                                            Text(
                                                text = summary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        integrityState.errorMessage?.let { message ->
                                            Spacer(modifier = Modifier.height(Spacing.xs))
                                            Text(
                                                text = message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                },
                                trailingContent = {
                                    Column {
                                        Button(
                                            onClick = onRunIntegrityCheck,
                                            enabled = !integrityState.isChecking
                                        ) {
                                            Text(if (integrityState.isChecking) "Checking..." else "Check")
                                        }
                                        TextButton(
                                            onClick = onRepairIntegrity,
                                            enabled = issueCount > 0 && !integrityState.isRepairing
                                        ) {
                                            Text(if (integrityState.isRepairing) "Repairing..." else "Repair")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // Sync conflict queue
            item {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Sync Conflicts",
                        style = MaterialTheme.typography.titleMedium
                    )
                    MaterialContainer {
                        Column {
                            val conflictCount = conflictsState.conflicts.size
                            ListItem(
                                headlineContent = { Text("Queued conflicts") },
                                supportingContent = {
                                    Column {
                                        Text(
                                            text = if (conflictsState.isLoading) {
                                                "Loading conflicts..."
                                            } else if (conflictCount == 0) {
                                                "No conflicts waiting for review"
                                            } else {
                                                "$conflictCount conflict${if (conflictCount == 1) "" else "s"} need review"
                                            },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        conflictsState.errorMessage?.let { message ->
                                            Spacer(modifier = Modifier.height(Spacing.xs))
                                            Text(
                                                text = message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                },
                                trailingContent = {
                                    Row {
                                        TextButton(onClick = onRefreshConflicts) {
                                            Text("Refresh")
                                        }
                                        TextButton(
                                            onClick = onClearConflicts,
                                            enabled = conflictCount > 0
                                        ) {
                                            Text("Clear")
                                        }
                                    }
                                }
                            )

                            conflictsState.conflicts.take(3).forEach { conflict ->
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = Spacing.md),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                                ListItem(
                                    headlineContent = {
                                        Text("${conflict.entityType} ${conflict.entityId}")
                                    },
                                    supportingContent = {
                                        Column {
                                            Text(
                                                text = conflict.reason,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            val timestamp = Instant.fromEpochMilliseconds(conflict.detectedAt)
                                            Text(
                                                text = "Detected ${timestamp.toReadableDateTimeShort()}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                )
                            }
                            if (conflictCount > 3) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = Spacing.md),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                                Text(
                                    text = "Showing 3 of $conflictCount conflicts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(Spacing.md)
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
                    modifier = Modifier.padding(horizontal = Spacing.lg)
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "Cloud Sync",
            style = MaterialTheme.typography.titleMedium
        )

        MaterialContainer {
            if (!isAuthenticated) {
                ListItem(
                    headlineContent = { Text("Sign in required") },
                    supportingContent = {
                        Text(
                            text = "Sign in to your LogDate Cloud account to enable sync",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            } else {
                // Sync status
                ListItem(
                    headlineContent = { Text("Sync Status") },
                    supportingContent = {
                        Column {
                            syncStatus?.let { status ->
                                if (status.isSyncing) {
                                    Text("Syncing...", color = MaterialTheme.colorScheme.primary)
                                } else {
                                    val statusText = if (status.hasErrors) {
                                        "Last sync failed: ${status.lastError?.message ?: "Unknown error"}"
                                    } else {
                                        status.lastSyncTime?.let {
                                            "Last synced: ${it.toReadableDateTimeShort()}"
                                        } ?: "Never synced"
                                    }
                                    Text(
                                        text = statusText,
                                        color = if (status.hasErrors) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            } ?: Text("Loading...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    trailingContent = {
                        Button(
                            onClick = onSyncNow,
                            enabled = syncStatus?.isSyncing != true
                        ) {
                            Text("Sync Now")
                        }
                    }
                )

                // Background sync setting
                ListItem(
                    headlineContent = { Text("Background Sync") },
                    supportingContent = { Text("Automatically sync your data in the background") },
                    trailingContent = {
                        Switch(
                            checked = isBackgroundSyncEnabled,
                            onCheckedChange = onBackgroundSyncEnabledChange
                        )
                    }
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
        quotaUsage = StorageQuotaUi(
            totalBytes = 100_000_000_000L, // 100GB default
            usedBytes = 0L,
            usagePercentage = 0f,
            formattedTotal = "100 GB",
            formattedUsed = "0 B",
            categories = emptyList()
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
        isBackgroundSyncEnabled = true
    )
}
