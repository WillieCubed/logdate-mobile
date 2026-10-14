@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.settings.ui.watch.WatchLinkLine
import app.logdate.feature.core.settings.ui.watch.WatchRequestFailure
import app.logdate.feature.core.settings.ui.watch.WatchStatus
import app.logdate.feature.core.settings.ui.watch.WatchStatusCard
import app.logdate.ui.common.SettingsNavigationItem
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.watch_connected
import logdate.client.feature.core.generated.resources.watch_link_on_its_own
import logdate.client.feature.core.generated.resources.watch_link_through_phone
import logdate.client.feature.core.generated.resources.watch_notifications
import logdate.client.feature.core.generated.resources.watch_out_of_range
import logdate.client.feature.core.generated.resources.watch_settings
import logdate.client.feature.core.generated.resources.watch_v2_access_section
import logdate.client.feature.core.generated.resources.watch_v2_connection_section
import logdate.client.feature.core.generated.resources.watch_v2_diagnostics
import logdate.client.feature.core.generated.resources.watch_v2_diagnostics_description
import logdate.client.feature.core.generated.resources.watch_v2_microphone
import logdate.client.feature.core.generated.resources.watch_v2_microphone_description
import logdate.client.feature.core.generated.resources.watch_v2_notifications_description
import logdate.client.feature.core.generated.resources.watch_v2_prompt_section
import logdate.client.feature.core.generated.resources.watch_v2_reminders
import logdate.client.feature.core.generated.resources.watch_v2_reminders_description
import logdate.client.feature.core.generated.resources.watch_v2_remove
import logdate.client.feature.core.generated.resources.watch_v2_remove_description
import logdate.client.feature.core.generated.resources.watch_v2_tiles
import logdate.client.feature.core.generated.resources.watch_v2_tiles_description
import org.jetbrains.compose.resources.stringResource

@Composable
fun WatchCompanionPage(
    status: WatchStatus?,
    requestFailure: WatchRequestFailure?,
    onBack: () -> Unit,
    onAssociate: () -> Unit,
    onInstall: () -> Unit,
    onReminders: () -> Unit,
    onTiles: () -> Unit,
    onMicrophone: () -> Unit,
    onNotifications: () -> Unit,
    onDiagnostics: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    preferences: @Composable (captureActions: @Composable () -> Unit) -> Unit = {},
) {
    val installed = status is WatchStatus.Ready || status is WatchStatus.NeedsAttention
    val identity: @Composable () -> Unit = {
        if (status is WatchStatus.Ready) {
            WatchReadyIdentity(status)
        } else {
            WatchStatusCard(status, requestFailure, onAssociate, onInstall)
        }
    }
    val settings: @Composable () -> Unit = {
        if (installed) {
            preferences {
                SettingsNavigationItem(
                    stringResource(Res.string.watch_v2_microphone),
                    stringResource(Res.string.watch_v2_microphone_description),
                    icon = { Icon(Icons.Default.Mic, null) },
                    onClick = onMicrophone,
                )
            }
            SettingsSection(stringResource(Res.string.watch_v2_access_section)) {
                SettingsNavigationItem(
                    stringResource(Res.string.watch_v2_tiles),
                    stringResource(Res.string.watch_v2_tiles_description),
                    icon = { Icon(Icons.Default.Widgets, null) },
                    onClick = onTiles,
                )
            }
            SettingsSection(stringResource(Res.string.watch_v2_prompt_section)) {
                SettingsNavigationItem(
                    stringResource(Res.string.watch_v2_reminders),
                    stringResource(Res.string.watch_v2_reminders_description),
                    icon = { Icon(Icons.Default.Schedule, null) },
                    onClick = onReminders,
                )
                SettingsNavigationItem(
                    stringResource(Res.string.watch_notifications),
                    stringResource(Res.string.watch_v2_notifications_description),
                    icon = { Icon(Icons.Default.Notifications, null) },
                    onClick = onNotifications,
                )
            }
        }
    }
    val connection: @Composable () -> Unit = {
        SettingsSection(stringResource(Res.string.watch_v2_connection_section)) {
            SettingsNavigationItem(
                stringResource(Res.string.watch_v2_diagnostics),
                stringResource(Res.string.watch_v2_diagnostics_description),
                icon = { Icon(Icons.Default.Troubleshoot, null) },
                onClick = onDiagnostics,
            )
            if (installed) {
                SettingsNavigationItem(
                    stringResource(Res.string.watch_v2_remove),
                    stringResource(Res.string.watch_v2_remove_description),
                    icon = { Icon(Icons.Default.DeleteOutline, null) },
                    onClick = onRemove,
                )
            }
        }
    }
    SettingsScaffold(
        title = stringResource(Res.string.watch_settings),
        onBack = onBack,
        modifier = modifier,
        maxContentWidth = 1040.dp,
    ) {
        item {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
                if (maxWidth >= 760.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        Column(
                            modifier = Modifier.weight(2f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                        ) {
                            identity()
                            connection()
                        }
                        Column(
                            modifier = Modifier.weight(3f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                        ) {
                            settings()
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        identity()
                        settings()
                        connection()
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchReadyIdentity(status: WatchStatus.Ready) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(
                        Icons.Default.Watch,
                        contentDescription = null,
                        modifier = Modifier.padding(Spacing.md),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        stringResource(if (status.nearby) Res.string.watch_connected else Res.string.watch_out_of_range),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        status.watchName.replace('_', ' '),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    status.link?.let { link ->
                        Text(
                            stringResource(
                                if (link == WatchLinkLine.THROUGH_PHONE) {
                                    Res.string.watch_link_through_phone
                                } else {
                                    Res.string.watch_link_on_its_own
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}
