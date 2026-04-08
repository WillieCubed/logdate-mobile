@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.client.domain.events.EventInferenceSensitivity
import app.logdate.ui.common.PrimaryTogglePill
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SimpleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.events.generated.resources.Res
import logdate.client.feature.events.generated.resources.events_settings_description
import logdate.client.feature.events.generated.resources.events_settings_master_toggle
import logdate.client.feature.events.generated.resources.events_settings_relative_days_ago
import logdate.client.feature.events.generated.resources.events_settings_relative_hours_ago
import logdate.client.feature.events.generated.resources.events_settings_relative_just_now
import logdate.client.feature.events.generated.resources.events_settings_relative_minutes_ago
import logdate.client.feature.events.generated.resources.events_settings_run_in_progress
import logdate.client.feature.events.generated.resources.events_settings_run_now
import logdate.client.feature.events.generated.resources.events_settings_sensitivity_helper_high
import logdate.client.feature.events.generated.resources.events_settings_sensitivity_helper_low
import logdate.client.feature.events.generated.resources.events_settings_sensitivity_helper_medium
import logdate.client.feature.events.generated.resources.events_settings_sensitivity_high
import logdate.client.feature.events.generated.resources.events_settings_sensitivity_label
import logdate.client.feature.events.generated.resources.events_settings_sensitivity_low
import logdate.client.feature.events.generated.resources.events_settings_sensitivity_medium
import logdate.client.feature.events.generated.resources.events_settings_smart_names_off
import logdate.client.feature.events.generated.resources.events_settings_smart_names_on
import logdate.client.feature.events.generated.resources.events_settings_smart_names_title
import logdate.client.feature.events.generated.resources.events_settings_status_created_last_run
import logdate.client.feature.events.generated.resources.events_settings_status_created_recently
import logdate.client.feature.events.generated.resources.events_settings_status_last_error
import logdate.client.feature.events.generated.resources.events_settings_status_last_run
import logdate.client.feature.events.generated.resources.events_settings_status_never
import logdate.client.feature.events.generated.resources.events_settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings screen for the on-device event inference pipeline.
 *
 * Lets the user toggle the master "auto-events" feature, see the most recent worker run, run
 * one immediately, choose how aggressive the clustering should be, and turn smart naming on
 * or off. The screen reads from `LogdatePreferencesDataSource` via [EventsSettingsViewModel],
 * which is the same source the worker uses, so changes show up immediately on the next run.
 */
@Composable
fun EventsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventsSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    EventsSettingsContent(
        uiState = uiState,
        onBack = onBack,
        onAutoEventsToggled = viewModel::setAutoEventsEnabled,
        onSensitivityChosen = viewModel::setSensitivity,
        onSmartNamingToggled = viewModel::setSmartNamingEnabled,
        onRunNow = viewModel::runNow,
        modifier = modifier,
    )
}

@Composable
fun EventsSettingsContent(
    uiState: EventsSettingsUiState,
    onBack: () -> Unit,
    onAutoEventsToggled: (Boolean) -> Unit,
    onSensitivityChosen: (EventInferenceSensitivity) -> Unit,
    onSmartNamingToggled: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(Res.string.events_settings_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            Text(
                text = stringResource(Res.string.events_settings_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        item {
            PrimaryTogglePill(
                label = stringResource(Res.string.events_settings_master_toggle),
                checked = uiState.isAutoEventsEnabled,
                onCheckedChange = onAutoEventsToggled,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        if (uiState.isAutoEventsEnabled) {
            item {
                StatusCard(
                    lastRunAge = uiState.lastRunAge,
                    lastCreatedCount = uiState.lastCreatedCount,
                    recentCreatedCount = uiState.recentCreatedCount,
                    lastError = uiState.lastError,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }

            item {
                FilledTonalButton(
                    onClick = onRunNow,
                    enabled = !uiState.isRunInFlight,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    Text(
                        stringResource(
                            if (uiState.isRunInFlight) {
                                Res.string.events_settings_run_in_progress
                            } else {
                                Res.string.events_settings_run_now
                            },
                        ),
                    )
                }
            }

            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = stringResource(Res.string.events_settings_sensitivity_label),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    SensitivitySelector(
                        selected = uiState.sensitivity,
                        onSelected = onSensitivityChosen,
                    )
                    Text(
                        text = stringResource(uiState.sensitivity.helperResource()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SimpleSettingsItem(
                    title = stringResource(Res.string.events_settings_smart_names_title),
                    description =
                        stringResource(
                            if (uiState.isSmartNamingEnabled) {
                                Res.string.events_settings_smart_names_on
                            } else {
                                Res.string.events_settings_smart_names_off
                            },
                        ),
                    onClick = { onSmartNamingToggled(!uiState.isSmartNamingEnabled) },
                    action = {
                        Switch(
                            checked = uiState.isSmartNamingEnabled,
                            onCheckedChange = onSmartNamingToggled,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    lastRunAge: RelativeAge?,
    lastCreatedCount: Int,
    recentCreatedCount: Int,
    lastError: String?,
    modifier: Modifier = Modifier,
) {
    val ageLabel =
        lastRunAge?.let { age -> stringResource(age.bucketResource(), *age.formatArguments()) }
            ?: stringResource(Res.string.events_settings_status_never)
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
                text = stringResource(Res.string.events_settings_status_last_run, ageLabel),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(Res.string.events_settings_status_created_last_run, lastCreatedCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(Res.string.events_settings_status_created_recently, recentCreatedCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (lastError != null) {
                Text(
                    text = stringResource(Res.string.events_settings_status_last_error, lastError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SensitivitySelector(
    selected: EventInferenceSensitivity,
    onSelected: (EventInferenceSensitivity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = EventInferenceSensitivity.entries.toList()
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(option.labelResource()))
            }
        }
    }
}

private fun EventInferenceSensitivity.labelResource() =
    when (this) {
        EventInferenceSensitivity.LOW -> Res.string.events_settings_sensitivity_low
        EventInferenceSensitivity.MEDIUM -> Res.string.events_settings_sensitivity_medium
        EventInferenceSensitivity.HIGH -> Res.string.events_settings_sensitivity_high
    }

private fun EventInferenceSensitivity.helperResource() =
    when (this) {
        EventInferenceSensitivity.LOW -> Res.string.events_settings_sensitivity_helper_low
        EventInferenceSensitivity.MEDIUM -> Res.string.events_settings_sensitivity_helper_medium
        EventInferenceSensitivity.HIGH -> Res.string.events_settings_sensitivity_helper_high
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
