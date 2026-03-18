@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.watch_app_not_installed
import logdate.client.feature.core.generated.resources.watch_app_not_installed_description
import logdate.client.feature.core.generated.resources.watch_connected
import logdate.client.feature.core.generated.resources.watch_install_on_watch
import logdate.client.feature.core.generated.resources.watch_loading
import logdate.client.feature.core.generated.resources.watch_no_paired
import logdate.client.feature.core.generated.resources.watch_no_paired_description
import logdate.client.feature.core.generated.resources.watch_notifications
import logdate.client.feature.core.generated.resources.watch_notifications_description
import logdate.client.feature.core.generated.resources.watch_notifications_title
import logdate.client.feature.core.generated.resources.watch_out_of_range
import logdate.client.feature.core.generated.resources.watch_privacy_description
import logdate.client.feature.core.generated.resources.watch_privacy_title
import logdate.client.feature.core.generated.resources.watch_settings
import logdate.client.feature.core.generated.resources.watch_sync
import logdate.client.feature.core.generated.resources.watch_sync_last_connected
import logdate.client.feature.core.generated.resources.watch_sync_not_yet
import logdate.client.feature.core.generated.resources.watch_sync_now
import logdate.client.feature.core.generated.resources.watch_sync_pending
import logdate.client.feature.core.generated.resources.watch_sync_settings
import logdate.client.feature.core.generated.resources.watch_sync_settings_description
import logdate.client.feature.core.generated.resources.watch_sync_up_to_date
import logdate.client.feature.core.generated.resources.watch_troubleshooting
import logdate.client.feature.core.generated.resources.watch_troubleshooting_description
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WatchSettingsScreen(
    onBack: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToTroubleshooting: () -> Unit,
    viewModel: WatchSettingsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val connectionState by viewModel.connectionState.collectAsState()

    WatchSettingsContent(
        connectionState = connectionState,
        onBack = onBack,
        onRequestSync = viewModel::requestSync,
        onInstallOnWatch = viewModel::installAppOnWatch,
        onNavigateToSync = onNavigateToSync,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToTroubleshooting = onNavigateToTroubleshooting,
        modifier = modifier,
    )
}

@Composable
fun WatchSettingsContent(
    connectionState: WatchConnectionState,
    onBack: () -> Unit,
    onRequestSync: () -> Unit,
    onInstallOnWatch: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToTroubleshooting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(Res.string.watch_settings),
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            ConnectionStatusCard(
                connectionState = connectionState,
                onInstallOnWatch = onInstallOnWatch,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        when (connectionState) {
            is WatchConnectionState.Connected,
            is WatchConnectionState.OutOfRange,
            -> {
                item {
                    SettingsSection(
                        title = stringResource(Res.string.watch_sync),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        SyncStatusRow(connectionState)

                        OutlinedButton(
                            onClick = onRequestSync,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.sm),
                            enabled = connectionState is WatchConnectionState.Connected,
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(stringResource(Res.string.watch_sync_now))
                        }

                        SettingsNavItem(
                            title = stringResource(Res.string.watch_sync_settings),
                            description = stringResource(Res.string.watch_sync_settings_description),
                            onClick = onNavigateToSync,
                        )
                    }
                }

                item {
                    SettingsSection(
                        title = stringResource(Res.string.watch_notifications),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        SettingsNavItem(
                            title = stringResource(Res.string.watch_notifications_title),
                            description = stringResource(Res.string.watch_notifications_description),
                            onClick = onNavigateToNotifications,
                        )
                    }
                }
            }

            is WatchConnectionState.AppNotInstalled,
            is WatchConnectionState.NoPairedWatch,
            is WatchConnectionState.Loading,
            -> {
                // No sync/notification sections when watch isn't set up
            }
        }

        item {
            SettingsSection(
                title = "",
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                SettingsNavItem(
                    title = stringResource(Res.string.watch_troubleshooting),
                    description = stringResource(Res.string.watch_troubleshooting_description),
                    onClick = onNavigateToTroubleshooting,
                )
            }
        }

        item {
            WatchPrivacyNote()
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: WatchConnectionState,
    onInstallOnWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MaterialContainer(modifier = modifier) {
        when (connectionState) {
            is WatchConnectionState.Loading -> {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = stringResource(Res.string.watch_loading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            is WatchConnectionState.NoPairedWatch -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Icon(
                        Icons.Outlined.Watch,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(Res.string.watch_no_paired),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(Res.string.watch_no_paired_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is WatchConnectionState.AppNotInstalled -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(Res.string.watch_app_not_installed),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(Res.string.watch_app_not_installed_description, connectionState.watchName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onInstallOnWatch) {
                        Text(stringResource(Res.string.watch_install_on_watch))
                    }
                }
            }

            is WatchConnectionState.Connected -> {
                ListItem(
                    headlineContent = { Text(connectionState.watchName) },
                    supportingContent = {
                        Text(
                            text = stringResource(Res.string.watch_connected),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Watch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }

            is WatchConnectionState.OutOfRange -> {
                ListItem(
                    headlineContent = { Text(connectionState.watchName) },
                    supportingContent = {
                        Text(
                            text = stringResource(Res.string.watch_out_of_range),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Watch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SyncStatusRow(connectionState: WatchConnectionState) {
    val statusText =
        when (connectionState) {
            is WatchConnectionState.Connected -> {
                when {
                    connectionState.pendingCount > 0 -> stringResource(Res.string.watch_sync_pending, connectionState.pendingCount)
                    connectionState.lastSynced != null -> stringResource(Res.string.watch_sync_up_to_date)
                    else -> stringResource(Res.string.watch_sync_not_yet)
                }
            }
            is WatchConnectionState.OutOfRange -> {
                if (connectionState.lastSynced != null) {
                    stringResource(Res.string.watch_sync_last_connected)
                } else {
                    stringResource(Res.string.watch_sync_not_yet)
                }
            }
            else -> null
        }

    if (statusText != null) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )
    }
}

@Composable
private fun SettingsNavItem(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun WatchPrivacyNote() {
    Surface {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    Icons.Default.Watch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.watch_privacy_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                text = stringResource(Res.string.watch_privacy_description),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
