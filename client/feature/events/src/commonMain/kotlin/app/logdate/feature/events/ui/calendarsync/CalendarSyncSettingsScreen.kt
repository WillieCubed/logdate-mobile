@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui.calendarsync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.client.domain.events.CalendarImportFailure
import app.logdate.client.permissions.rememberCalendarPermissionState
import app.logdate.feature.events.ui.settings.RelativeAge
import app.logdate.ui.common.PrimaryTogglePill
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import logdate.client.feature.events.generated.resources.Res
import logdate.client.feature.events.generated.resources.calendar_sync_failure_calendars_unavailable
import logdate.client.feature.events.generated.resources.calendar_sync_failure_permission_denied
import logdate.client.feature.events.generated.resources.calendar_sync_failure_persistence_failed
import logdate.client.feature.events.generated.resources.calendar_sync_failure_unknown
import logdate.client.feature.events.generated.resources.calendar_sync_settings_activity_row_subtitle
import logdate.client.feature.events.generated.resources.calendar_sync_settings_activity_row_title
import logdate.client.feature.events.generated.resources.calendar_sync_settings_calendars_row_subtitle_none
import logdate.client.feature.events.generated.resources.calendar_sync_settings_calendars_row_subtitle_some
import logdate.client.feature.events.generated.resources.calendar_sync_settings_calendars_row_title
import logdate.client.feature.events.generated.resources.calendar_sync_settings_description
import logdate.client.feature.events.generated.resources.calendar_sync_settings_grant_action
import logdate.client.feature.events.generated.resources.calendar_sync_settings_grant_rationale
import logdate.client.feature.events.generated.resources.calendar_sync_settings_grant_title
import logdate.client.feature.events.generated.resources.calendar_sync_settings_master_toggle
import logdate.client.feature.events.generated.resources.calendar_sync_settings_run_in_progress
import logdate.client.feature.events.generated.resources.calendar_sync_settings_run_now
import logdate.client.feature.events.generated.resources.calendar_sync_settings_status_created
import logdate.client.feature.events.generated.resources.calendar_sync_settings_status_last_run
import logdate.client.feature.events.generated.resources.calendar_sync_settings_status_never
import logdate.client.feature.events.generated.resources.calendar_sync_settings_status_updated
import logdate.client.feature.events.generated.resources.calendar_sync_settings_title
import logdate.client.feature.events.generated.resources.events_settings_relative_days_ago
import logdate.client.feature.events.generated.resources.events_settings_relative_hours_ago
import logdate.client.feature.events.generated.resources.events_settings_relative_just_now
import logdate.client.feature.events.generated.resources.events_settings_relative_minutes_ago
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Top-level settings screen for device calendar sync.
 *
 * Renders a permission state machine first (rationale → grant → granted), then the
 * master toggle, the most recent worker run, a "Sync now" button, and two ListItem rows
 * that navigate to the calendars picker and the recent imports list. The runtime
 * permission flow lives at the Composable layer because it needs
 * `rememberLauncherForActivityResult`; the result is fed back into the ViewModel via
 * [CalendarSyncOverviewViewModel.setPermissionState].
 */
@Composable
fun CalendarSyncSettingsScreen(
    onBack: () -> Unit,
    onNavigateToCalendars: () -> Unit,
    onNavigateToActivity: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarSyncOverviewViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val permissionState = rememberCalendarPermissionState()

    LaunchedEffect(permissionState.hasPermission, permissionState.permissionRequested) {
        viewModel.setPermissionState(
            when {
                permissionState.hasPermission -> PermissionState.Granted
                permissionState.permissionRequested -> PermissionState.Denied
                else -> PermissionState.Unknown
            },
        )
    }

    CalendarSyncSettingsContent(
        uiState = uiState,
        onBack = onBack,
        onRequestPermission = permissionState.requestPermission,
        onSyncEnabledToggled = viewModel::setSyncEnabled,
        onRunNow = viewModel::runNow,
        onNavigateToCalendars = onNavigateToCalendars,
        onNavigateToActivity = onNavigateToActivity,
        modifier = modifier,
    )
}

@Composable
fun CalendarSyncSettingsContent(
    uiState: CalendarSyncOverviewUiState,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onSyncEnabledToggled: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onNavigateToCalendars: () -> Unit,
    onNavigateToActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(Res.string.calendar_sync_settings_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            Text(
                text = stringResource(Res.string.calendar_sync_settings_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        when (uiState.permissionState) {
            PermissionState.Granted -> {
                item {
                    PrimaryTogglePill(
                        label = stringResource(Res.string.calendar_sync_settings_master_toggle),
                        checked = uiState.isSyncEnabled,
                        onCheckedChange = onSyncEnabledToggled,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
                if (uiState.isSyncEnabled) {
                    item {
                        StatusCard(
                            uiState = uiState,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }
                    item {
                        FilledTonalButton(
                            onClick = onRunNow,
                            enabled = !uiState.isRunInFlight && uiState.selectedCalendarCount > 0,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        ) {
                            Text(
                                stringResource(
                                    if (uiState.isRunInFlight) {
                                        Res.string.calendar_sync_settings_run_in_progress
                                    } else {
                                        Res.string.calendar_sync_settings_run_now
                                    },
                                ),
                            )
                        }
                    }
                    item {
                        ListItem(
                            headlineContent = {
                                Text(stringResource(Res.string.calendar_sync_settings_calendars_row_title))
                            },
                            supportingContent = {
                                Text(
                                    if (uiState.selectedCalendarCount == 0) {
                                        stringResource(Res.string.calendar_sync_settings_calendars_row_subtitle_none)
                                    } else {
                                        stringResource(
                                            Res.string.calendar_sync_settings_calendars_row_subtitle_some,
                                            uiState.selectedCalendarCount,
                                            uiState.totalCalendarCount,
                                        )
                                    },
                                )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onNavigateToCalendars)
                                    .padding(horizontal = Spacing.lg),
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = {
                                Text(stringResource(Res.string.calendar_sync_settings_activity_row_title))
                            },
                            supportingContent = {
                                Text(stringResource(Res.string.calendar_sync_settings_activity_row_subtitle))
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onNavigateToActivity)
                                    .padding(horizontal = Spacing.lg),
                        )
                    }
                }
            }
            PermissionState.Denied,
            PermissionState.Unknown,
            -> {
                item {
                    PermissionRequestCard(
                        onRequestPermission = onRequestPermission,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Spacing.md),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.calendar_sync_settings_grant_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.calendar_sync_settings_grant_rationale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onRequestPermission) {
                Text(stringResource(Res.string.calendar_sync_settings_grant_action))
            }
        }
    }
}

@Composable
private fun StatusCard(
    uiState: CalendarSyncOverviewUiState,
    modifier: Modifier = Modifier,
) {
    val ageLabel =
        uiState.lastRunAge?.let { age -> stringResource(age.bucketResource(), *age.formatArguments()) }
            ?: stringResource(Res.string.calendar_sync_settings_status_never)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Spacing.md),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(Res.string.calendar_sync_settings_status_last_run, ageLabel),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(Res.string.calendar_sync_settings_status_created, uiState.lastCreatedCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(Res.string.calendar_sync_settings_status_updated, uiState.lastUpdatedCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (uiState.lastFailure != null) {
                Text(
                    text = stringResource(uiState.lastFailure.messageResource()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun CalendarImportFailure.messageResource() =
    when (this) {
        CalendarImportFailure.Unknown -> Res.string.calendar_sync_failure_unknown
        CalendarImportFailure.PermissionDenied -> Res.string.calendar_sync_failure_permission_denied
        CalendarImportFailure.CalendarsUnavailable -> Res.string.calendar_sync_failure_calendars_unavailable
        CalendarImportFailure.PersistenceFailed -> Res.string.calendar_sync_failure_persistence_failed
    }

private fun RelativeAge.bucketResource() =
    when (this) {
        RelativeAge.JustNow -> Res.string.events_settings_relative_just_now
        is RelativeAge.Minutes -> Res.string.events_settings_relative_minutes_ago
        is RelativeAge.Hours -> Res.string.events_settings_relative_hours_ago
        is RelativeAge.Days -> Res.string.events_settings_relative_days_ago
    }

private fun RelativeAge.formatArguments(): Array<Any> =
    when (this) {
        RelativeAge.JustNow -> emptyArray()
        is RelativeAge.Minutes -> arrayOf(count)
        is RelativeAge.Hours -> arrayOf(count)
        is RelativeAge.Days -> arrayOf(count)
    }
