package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
// CloudStorageQuota import is not needed, removed
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
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
    viewModel: SettingsViewModel = koinViewModel(),
) {
    // Detect if we're in a large screen layout where this might be a detail pane
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isPotentialDetailPane = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    val uiState by viewModel.uiState.collectAsState()
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
    
    DataSettingsContent(
        onBack = onBack,
        quotaUsage = uiState.quotaState.toStorageQuotaUi(),
        onExportContent = viewModel::exportContent,
        exportState = uiState.exportState,
        snackbarHostState = snackbarHostState,
        isPotentialDetailPane = isPotentialDetailPane,
        syncStatus = uiState.syncStatus,
        isAuthenticated = uiState.isAuthenticated,
        onSyncNow = viewModel::syncNow
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataSettingsContent(
    onBack: () -> Unit,
    quotaUsage: StorageQuotaUi,
    onExportContent: () -> Unit,
    exportState: ExportState,
    snackbarHostState: SnackbarHostState,
    isPotentialDetailPane: Boolean = false,
    syncStatus: app.logdate.client.sync.SyncStatus? = null,
    isAuthenticated: Boolean = false,
    onSyncNow: () -> Unit = {}
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

                        ListItem(
                            headlineContent = { Text("Import Data") },
                            supportingContent = { Text("Import data from a backup file") },
                            trailingContent = {
                                Button(onClick = { /* TODO: Implement */ }) {
                                    Text("Import")
                                }
                            }
                        )
                    }
                }
            }
            
            // Sync settings
            item {
                SyncSettingsSection(
                    syncStatus = syncStatus,
                    isAuthenticated = isAuthenticated,
                    onSyncNow = onSyncNow,
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
                            checked = true,
                            onCheckedChange = { /* TODO: Implement */ }
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
        snackbarHostState = remember { SnackbarHostState() }
    )
}