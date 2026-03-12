@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.advanced
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.location_capture_experiment
import logdate.client.feature.core.generated.resources.location_capture_experiment_description
import logdate.client.feature.core.generated.resources.location_data_privacy_note
import logdate.client.feature.core.generated.resources.location_data_stored_on_device
import logdate.client.feature.core.generated.resources.location_enable_background_tracking
import logdate.client.feature.core.generated.resources.location_enable_background_tracking_description
import logdate.client.feature.core.generated.resources.location_server_assist
import logdate.client.feature.core.generated.resources.location_server_assist_description
import logdate.client.feature.core.generated.resources.location_services
import logdate.client.feature.core.generated.resources.location_settings
import logdate.client.feature.core.generated.resources.location_timeline
import logdate.client.feature.core.generated.resources.location_timeline_description
import logdate.client.feature.core.generated.resources.location_track_journal_entries
import logdate.client.feature.core.generated.resources.location_track_journal_entries_description
import logdate.client.feature.core.generated.resources.location_track_timeline_review
import logdate.client.feature.core.generated.resources.location_track_timeline_review_description
import logdate.client.feature.core.generated.resources.location_tracking_battery_note
import logdate.client.feature.core.generated.resources.location_update_frequency
import logdate.client.feature.core.generated.resources.tracking_interval
import logdate.client.feature.core.generated.resources.view_location_timeline
import logdate.client.feature.core.generated.resources.view_timeline
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen for managing location tracking settings.
 *
 * This screen automatically adapts to different screen sizes:
 * - Large screens: Acts as a detail pane with minimal header (when in two-pane layout)
 * - Small screens: Standard screen with back navigation
 *
 * @param onBack Callback for when the user presses the back button
 * @param viewModel The view model that manages location settings
 */
@Composable
fun LocationSettingsScreen(
    onBack: () -> Unit,
    onOpenLocationTimeline: () -> Unit,
    onShowLocationTimeline: () -> Unit = onOpenLocationTimeline,
    viewModel: LocationSettingsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LocationSettingsContent(
        settings = uiState.settings,
        onBack = onBack,
        onToggleBackgroundTracking = viewModel::toggleBackgroundTracking,
        onToggleJournalTracking = viewModel::toggleJournalTracking,
        onToggleTimelineTracking = viewModel::toggleTimelineTracking,
        onUpdateTrackingInterval = viewModel::updateTrackingInterval,
        onToggleMirroredExperiment = viewModel::toggleMirroredExperiment,
        onToggleServerAssist = viewModel::toggleServerAssist,
        onShowLocationTimeline = onShowLocationTimeline,
        modifier = modifier.applyScreenStyles(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSettingsContent(
    settings: LocationTrackingSettings,
    onBack: () -> Unit,
    onToggleBackgroundTracking: (Boolean) -> Unit,
    onToggleJournalTracking: (Boolean) -> Unit,
    onToggleTimelineTracking: (Boolean) -> Unit,
    onUpdateTrackingInterval: (Long) -> Unit,
    onToggleMirroredExperiment: (Boolean) -> Unit = {},
    onToggleServerAssist: (Boolean) -> Unit = {},
    onShowLocationTimeline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val mirroredExperimentEnabled = settings.captureMode == LocationCaptureMode.EXPERIMENT_MIRRORED
    val intervalOptions = listOf(2L, 5L, 10L, 15L, 30L, 60L, 120L)
    val selectedIntervalIndex =
        intervalOptions.indexOfLast { option -> option <= settings.minimumPersistIntervalMinutes }.coerceAtLeast(0)

    Scaffold(
        modifier =
            modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.location_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                // Location Services Section
                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = stringResource(Res.string.location_services),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = Spacing.sm),
                        )

                        MaterialContainer {
                            ToggleSettingsItem(
                                title = stringResource(Res.string.location_enable_background_tracking),
                                description = stringResource(Res.string.location_enable_background_tracking_description),
                                checked = settings.backgroundTrackingEnabled,
                                onCheckedChange = onToggleBackgroundTracking,
                            )
                            ToggleSettingsItem(
                                title = stringResource(Res.string.location_track_journal_entries),
                                description = stringResource(Res.string.location_track_journal_entries_description),
                                checked = settings.autoTrackForJournalEntries,
                                onCheckedChange = onToggleJournalTracking,
                            )
                            ToggleSettingsItem(
                                title = stringResource(Res.string.location_track_timeline_review),
                                description = stringResource(Res.string.location_track_timeline_review_description),
                                checked = settings.autoTrackForTimelineReview,
                                onCheckedChange = onToggleTimelineTracking,
                            )
                        }
                    }
                }

                // Tracking Interval Section (only visible if background tracking is enabled)
                if (settings.backgroundTrackingEnabled) {
                    item {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = stringResource(Res.string.tracking_interval),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = Spacing.sm),
                            )

                            MaterialContainer {
                                SurfaceItem {
                                    Column(
                                        modifier = Modifier.padding(Spacing.md),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                                    ) {
                                        Text(
                                            text =
                                                stringResource(
                                                    Res.string.location_update_frequency,
                                                    settings.minimumPersistIntervalMinutes,
                                                ),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )

                                        Slider(
                                            value = selectedIntervalIndex.toFloat(),
                                            onValueChange = { newValue ->
                                                val step = newValue.toInt().coerceIn(0, intervalOptions.lastIndex)
                                                val minutes = intervalOptions[step]
                                                onUpdateTrackingInterval(minutes)
                                            },
                                            valueRange = 0f..intervalOptions.lastIndex.toFloat(),
                                            steps = intervalOptions.size - 2,
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        Text(
                                            stringResource(Res.string.location_tracking_battery_note),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (settings.backgroundTrackingEnabled) {
                    item {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = stringResource(Res.string.advanced),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = Spacing.sm),
                            )

                            MaterialContainer {
                                ToggleSettingsItem(
                                    title = stringResource(Res.string.location_capture_experiment),
                                    description = stringResource(Res.string.location_capture_experiment_description),
                                    checked = mirroredExperimentEnabled,
                                    onCheckedChange = onToggleMirroredExperiment,
                                )
                                ToggleSettingsItem(
                                    title = stringResource(Res.string.location_server_assist),
                                    description = stringResource(Res.string.location_server_assist_description),
                                    checked = settings.serverAssistEnabled,
                                    onCheckedChange = onToggleServerAssist,
                                )
                            }
                        }
                    }
                }

                // Location Timeline Section
                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = stringResource(Res.string.location_timeline),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = Spacing.sm),
                        )
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onShowLocationTimeline,
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                Icon(
                                    Icons.Default.Timeline,
                                    contentDescription = stringResource(Res.string.view_timeline),
                                    tint = MaterialTheme.colorScheme.primary,
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(Res.string.view_location_timeline),
                                        style = MaterialTheme.typography.titleSmall,
                                    )

                                    Text(
                                        stringResource(Res.string.location_timeline_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Location Privacy Notes
                item {
                    LocationSettingsNotes()
                }
            }
        }
    }
}

@Composable
private fun LocationSettingsNotes() {
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
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )

                Text(
                    stringResource(Res.string.location_data_stored_on_device),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            Text(
                stringResource(Res.string.location_data_privacy_note),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
