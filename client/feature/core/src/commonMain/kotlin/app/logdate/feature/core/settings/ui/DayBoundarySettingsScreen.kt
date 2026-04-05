@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.domain.dayboundary.HealthConnectStatus
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.PrimaryTogglePill
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import app.logdate.util.asTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.cancel
import logdate.client.feature.core.generated.resources.confirm
import logdate.client.feature.core.generated.resources.day_schedule
import logdate.client.feature.core.generated.resources.day_schedule_detail_description
import logdate.client.feature.core.generated.resources.day_schedule_disabled_explanation
import logdate.client.feature.core.generated.resources.day_schedule_enabled_explanation
import logdate.client.feature.core.generated.resources.day_schedule_fallback_time
import logdate.client.feature.core.generated.resources.day_schedule_fallback_time_description
import logdate.client.feature.core.generated.resources.day_schedule_health_checking
import logdate.client.feature.core.generated.resources.day_schedule_health_connected
import logdate.client.feature.core.generated.resources.day_schedule_health_grant_access
import logdate.client.feature.core.generated.resources.day_schedule_health_not_available
import logdate.client.feature.core.generated.resources.day_schedule_health_not_available_detail
import logdate.client.feature.core.generated.resources.day_schedule_health_permissions_needed
import logdate.client.feature.core.generated.resources.use_sleep_schedule
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

@Composable
fun DayBoundarySettingsScreen(
    onBack: () -> Unit,
    onRequestHealthPermissions: () -> Unit = {},
    onEnableSleepBasedWithPermissions: () -> Unit = onRequestHealthPermissions,
    modifier: Modifier = Modifier,
    viewModel: TimelineSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    DayBoundarySettingsContent(
        sleepBasedEnabled = uiState.dayBoundarySettings.sleepBasedBoundariesEnabled,
        fallbackStartHour = uiState.fallbackStartHour,
        healthConnectStatus = uiState.healthConnectStatus,
        onBack = onBack,
        onToggleSleepBased = { enabled ->
            when (resolveDayBoundaryToggleAction(enabled, uiState.healthConnectStatus)) {
                DayBoundaryToggleAction.DISABLE -> viewModel.toggleSleepBasedBoundaries(false)
                DayBoundaryToggleAction.ENABLE_DIRECTLY -> viewModel.toggleSleepBasedBoundaries(true)
                DayBoundaryToggleAction.REQUEST_PERMISSIONS -> onEnableSleepBasedWithPermissions()
                DayBoundaryToggleAction.NO_OP -> Unit
            }
        },
        onSetFallbackHour = viewModel::setFallbackStartHour,
        onRequestPermissions = onRequestHealthPermissions,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayBoundarySettingsContent(
    sleepBasedEnabled: Boolean,
    fallbackStartHour: Int,
    healthConnectStatus: HealthConnectStatus,
    onBack: () -> Unit,
    onToggleSleepBased: (Boolean) -> Unit,
    onSetFallbackHour: (Int) -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTimePicker by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(Res.string.day_schedule),
        onBack = onBack,
        modifier = modifier,
    ) {
        // Description
        item {
            Text(
                text = stringResource(Res.string.day_schedule_detail_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        // Primary toggle
        item {
            PrimaryTogglePill(
                label = stringResource(Res.string.use_sleep_schedule),
                checked = sleepBasedEnabled,
                onCheckedChange = onToggleSleepBased,
                enabled = healthConnectStatus != HealthConnectStatus.NOT_AVAILABLE,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        // Options section
        item {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // Health Connect status
                if (sleepBasedEnabled || healthConnectStatus == HealthConnectStatus.NOT_AVAILABLE) {
                    MaterialContainer {
                        HealthConnectStatusRow(
                            status = healthConnectStatus,
                            onRequestPermissions = onRequestPermissions,
                        )
                    }
                }

                // Explanation text
                Text(
                    text =
                        stringResource(
                            if (sleepBasedEnabled) {
                                Res.string.day_schedule_enabled_explanation
                            } else {
                                Res.string.day_schedule_disabled_explanation
                            },
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Fallback time
                MaterialContainer {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(Res.string.day_schedule_fallback_time))
                        },
                        supportingContent = {
                            Text(stringResource(Res.string.day_schedule_fallback_time_description))
                        },
                        trailingContent = {
                            TextButton(onClick = { showTimePicker = true }) {
                                Text(
                                    text = formatHour(fallbackStartHour),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = fallbackStartHour,
            onConfirm = { hour ->
                onSetFallbackHour(hour)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun HealthConnectStatusRow(
    status: HealthConnectStatus,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (status) {
        HealthConnectStatus.CONNECTED -> {
            ListItem(
                modifier = modifier,
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                },
                headlineContent = {
                    Text(stringResource(Res.string.day_schedule_health_connected))
                },
            )
        }

        HealthConnectStatus.PERMISSIONS_NEEDED -> {
            Column(modifier = modifier) {
                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    headlineContent = {
                        Text(stringResource(Res.string.day_schedule_health_permissions_needed))
                    },
                )
                FilledTonalButton(
                    onClick = onRequestPermissions,
                    modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.md),
                ) {
                    Text(stringResource(Res.string.day_schedule_health_grant_access))
                }
            }
        }

        HealthConnectStatus.NOT_AVAILABLE -> {
            ListItem(
                modifier = modifier,
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                },
                headlineContent = {
                    Text(stringResource(Res.string.day_schedule_health_not_available))
                },
                supportingContent = {
                    Text(stringResource(Res.string.day_schedule_health_not_available_detail))
                },
            )
        }

        HealthConnectStatus.CHECKING -> {
            ListItem(
                modifier = modifier,
                headlineContent = {
                    Text(
                        text = stringResource(Res.string.day_schedule_health_checking),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = 0,
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour) }) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
        text = {
            TimePicker(state = state)
        },
    )
}

private fun formatHour(hour: Int): String {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.todayIn(tz)
    return LocalDateTime(today, LocalTime(hour, 0)).toInstant(tz).asTime
}

internal enum class DayBoundaryToggleAction {
    DISABLE,
    ENABLE_DIRECTLY,
    REQUEST_PERMISSIONS,
    NO_OP,
}

internal fun resolveDayBoundaryToggleAction(
    enabled: Boolean,
    healthConnectStatus: HealthConnectStatus,
): DayBoundaryToggleAction =
    when {
        !enabled -> DayBoundaryToggleAction.DISABLE
        healthConnectStatus == HealthConnectStatus.CONNECTED -> DayBoundaryToggleAction.ENABLE_DIRECTLY
        healthConnectStatus == HealthConnectStatus.PERMISSIONS_NEEDED -> DayBoundaryToggleAction.REQUEST_PERMISSIONS
        healthConnectStatus == HealthConnectStatus.CHECKING -> DayBoundaryToggleAction.REQUEST_PERMISSIONS
        healthConnectStatus == HealthConnectStatus.NOT_AVAILABLE -> DayBoundaryToggleAction.NO_OP
        else -> DayBoundaryToggleAction.NO_OP
    }
