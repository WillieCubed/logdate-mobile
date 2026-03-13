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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.automatically_sync_your_data_in_the_background
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.background_sync
import logdate.client.feature.core.generated.resources.clear
import logdate.client.feature.core.generated.resources.cloud_sync
import logdate.client.feature.core.generated.resources.conflict_entity_with_id
import logdate.client.feature.core.generated.resources.conflicts_need_review
import logdate.client.feature.core.generated.resources.detected_timestamp
import logdate.client.feature.core.generated.resources.last_sync_failed
import logdate.client.feature.core.generated.resources.last_synced_time
import logdate.client.feature.core.generated.resources.loading
import logdate.client.feature.core.generated.resources.loading_conflicts
import logdate.client.feature.core.generated.resources.never_synced
import logdate.client.feature.core.generated.resources.no_conflicts_waiting
import logdate.client.feature.core.generated.resources.queued_conflicts
import logdate.client.feature.core.generated.resources.refresh
import logdate.client.feature.core.generated.resources.showing_three_of_conflicts
import logdate.client.feature.core.generated.resources.sign_in
import logdate.client.feature.core.generated.resources.sign_in_required
import logdate.client.feature.core.generated.resources.sign_in_to_your_logdate_cloud_account_to_enable_sync
import logdate.client.feature.core.generated.resources.sync_and_backup
import logdate.client.feature.core.generated.resources.sync_conflicts
import logdate.client.feature.core.generated.resources.sync_now
import logdate.client.feature.core.generated.resources.sync_status
import logdate.client.feature.core.generated.resources.syncing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

/**
 * Sync & Backup settings screen.
 *
 * This screen handles cloud sync status, background sync, storage quota,
 * and sync conflict resolution.
 *
 * @param onBack Callback for when the user presses the back button
 * @param onNavigateToSignIn Callback for navigating to sign-in when user taps sign-in CTA
 * @param viewModel ViewModel for the settings
 */
@Composable
fun SyncSettingsScreen(
    onBack: () -> Unit,
    onNavigateToSignIn: () -> Unit = {},
    viewModel: DataSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    SyncSettingsContent(
        onBack = onBack,
        syncStatus = uiState.syncStatus,
        isAuthenticated = uiState.isAuthenticated,
        onSyncNow = viewModel::syncNow,
        isBackgroundSyncEnabled = uiState.isBackgroundSyncEnabled,
        onBackgroundSyncEnabledChange = viewModel::setBackgroundSyncEnabled,
        onNavigateToSignIn = onNavigateToSignIn,
        conflictsState = uiState.conflictsState,
        onClearConflicts = viewModel::clearConflicts,
        onRefreshConflicts = { viewModel.refreshConflicts(force = true) },
        quotaUsage = uiState.quotaState.toStorageQuotaUi(),
        isQuotaAvailable = uiState.isQuotaAvailable,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsContent(
    onBack: () -> Unit,
    syncStatus: app.logdate.client.sync.SyncStatus?,
    isAuthenticated: Boolean,
    onSyncNow: () -> Unit,
    isBackgroundSyncEnabled: Boolean,
    onBackgroundSyncEnabledChange: (Boolean) -> Unit,
    onNavigateToSignIn: () -> Unit,
    conflictsState: ConflictsState,
    onClearConflicts: () -> Unit,
    onRefreshConflicts: () -> Unit,
    quotaUsage: StorageQuotaUi,
    isQuotaAvailable: Boolean,
    snackbarHostState: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier =
            Modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.sync_and_backup)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                // Storage quota section (only show when authenticated)
                if (isAuthenticated && isQuotaAvailable) {
                    item {
                        QuotaUsageBlock(
                            quotaUsage = quotaUsage,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }
                }

                // Sync settings
                item {
                    SettingsSection(
                        title = stringResource(Res.string.cloud_sync),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        if (!isAuthenticated) {
                            ListItem(
                                headlineContent = { Text(stringResource(Res.string.sign_in_required)) },
                                supportingContent = {
                                    Text(
                                        text = stringResource(Res.string.sign_in_to_your_logdate_cloud_account_to_enable_sync),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = {
                                    Button(onClick = onNavigateToSignIn) {
                                        Text(stringResource(Res.string.sign_in))
                                    }
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
                                                        stringResource(Res.string.last_sync_failed)
                                                    } else {
                                                        status.lastSyncTime?.let {
                                                            stringResource(
                                                                Res.string.last_synced_time,
                                                                it.toReadableDateTimeShort(),
                                                            )
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
                            ToggleSettingsItem(
                                title = stringResource(Res.string.background_sync),
                                description = stringResource(Res.string.automatically_sync_your_data_in_the_background),
                                checked = isBackgroundSyncEnabled,
                                onCheckedChange = onBackgroundSyncEnabledChange,
                            )
                        }
                    }
                }

                // Sync conflict queue (only show when authenticated)
                if (isAuthenticated) {
                    item {
                        SettingsSection(
                            title = stringResource(Res.string.sync_conflicts),
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        ) {
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
            }
        }
    }
}
