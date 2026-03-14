@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.automatically_sync_your_data_in_the_background
import logdate.client.feature.core.generated.resources.background_sync
import logdate.client.feature.core.generated.resources.clear
import logdate.client.feature.core.generated.resources.cloud_sync
import logdate.client.feature.core.generated.resources.conflict_entity_with_id
import logdate.client.feature.core.generated.resources.conflicts_need_review
import logdate.client.feature.core.generated.resources.create_account
import logdate.client.feature.core.generated.resources.detected_timestamp
import logdate.client.feature.core.generated.resources.last_sync_failed
import logdate.client.feature.core.generated.resources.last_synced_time
import logdate.client.feature.core.generated.resources.loading
import logdate.client.feature.core.generated.resources.loading_conflicts
import logdate.client.feature.core.generated.resources.never_synced
import logdate.client.feature.core.generated.resources.no_conflicts_waiting
import logdate.client.feature.core.generated.resources.queued_conflicts
import logdate.client.feature.core.generated.resources.refresh
import logdate.client.feature.core.generated.resources.securely_sync_your_journals_notes_and_memories_across_all_your_devices
import logdate.client.feature.core.generated.resources.showing_three_of_conflicts
import logdate.client.feature.core.generated.resources.sign_in
import logdate.client.feature.core.generated.resources.sync_and_backup
import logdate.client.feature.core.generated.resources.sync_conflicts
import logdate.client.feature.core.generated.resources.sync_feature_access
import logdate.client.feature.core.generated.resources.sync_feature_backup
import logdate.client.feature.core.generated.resources.sync_feature_sync
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
    onNavigateToCloudAccountCreation: () -> Unit = {},
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
        onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
        onNavigateToSignIn = onNavigateToSignIn,
        conflictsState = uiState.conflictsState,
        onClearConflicts = viewModel::clearConflicts,
        onRefreshConflicts = { viewModel.refreshConflicts(force = true) },
        quotaUsage = uiState.quotaState.toStorageQuotaUi(),
        isQuotaAvailable = uiState.isQuotaAvailable,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun SyncSettingsContent(
    onBack: () -> Unit,
    syncStatus: app.logdate.client.sync.SyncStatus?,
    isAuthenticated: Boolean,
    onSyncNow: () -> Unit,
    isBackgroundSyncEnabled: Boolean,
    onBackgroundSyncEnabledChange: (Boolean) -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit,
    conflictsState: ConflictsState,
    onClearConflicts: () -> Unit,
    onRefreshConflicts: () -> Unit,
    quotaUsage: StorageQuotaUi,
    isQuotaAvailable: Boolean,
    snackbarHostState: SnackbarHostState,
) {
    SettingsScaffold(
        title = stringResource(Res.string.sync_and_backup),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) {
        if (!isAuthenticated) {
            item {
                SyncPromoContent(
                    onCreateAccount = onNavigateToCloudAccountCreation,
                    onSignIn = onNavigateToSignIn,
                    modifier = Modifier.fillParentMaxHeight(),
                )
            }
        } else {
            if (isQuotaAvailable) {
                item {
                    QuotaUsageBlock(
                        quotaUsage = quotaUsage,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }

            item {
                CloudSyncSection(
                    syncStatus = syncStatus,
                    onSyncNow = onSyncNow,
                    isBackgroundSyncEnabled = isBackgroundSyncEnabled,
                    onBackgroundSyncEnabledChange = onBackgroundSyncEnabledChange,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }

            item {
                SyncConflictsSection(
                    conflictsState = conflictsState,
                    onClearConflicts = onClearConflicts,
                    onRefreshConflicts = onRefreshConflicts,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun SyncPromoContent(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Cloud,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Text(
            text = stringResource(Res.string.securely_sync_your_journals_notes_and_memories_across_all_your_devices),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        SyncFeatureRow(
            icon = Icons.Rounded.CloudDone,
            text = stringResource(Res.string.sync_feature_backup),
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        SyncFeatureRow(
            icon = Icons.Rounded.Devices,
            text = stringResource(Res.string.sync_feature_access),
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        SyncFeatureRow(
            icon = Icons.Rounded.Sync,
            text = stringResource(Res.string.sync_feature_sync),
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Button(
                onClick = onCreateAccount,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.create_account))
            }
            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.sign_in))
            }
        }
    }
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
private fun CloudSyncSection(
    syncStatus: app.logdate.client.sync.SyncStatus?,
    onSyncNow: () -> Unit,
    isBackgroundSyncEnabled: Boolean,
    onBackgroundSyncEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.cloud_sync),
        modifier = modifier,
    ) {
        Column {
            SyncStatusItem(syncStatus = syncStatus, onSyncNow = onSyncNow)
            ToggleSettingsItem(
                title = stringResource(Res.string.background_sync),
                description = stringResource(Res.string.automatically_sync_your_data_in_the_background),
                checked = isBackgroundSyncEnabled,
                onCheckedChange = onBackgroundSyncEnabledChange,
            )
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
        supportingContent = { SyncStatusText(syncStatus) },
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
