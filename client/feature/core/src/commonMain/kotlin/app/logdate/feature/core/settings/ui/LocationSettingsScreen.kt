@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.feature.location.timeline.ui.LocationTimelineBottomSheet
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.location_data_privacy_note
import logdate.client.feature.core.generated.resources.location_data_stored_on_device
import logdate.client.feature.core.generated.resources.location_enable_background_tracking
import logdate.client.feature.core.generated.resources.location_enable_background_tracking_description
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
import logdate.client.feature.core.generated.resources.text_120_min
import logdate.client.feature.core.generated.resources.text_15_min
import logdate.client.feature.core.generated.resources.text_30_min
import logdate.client.feature.core.generated.resources.text_60_min
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
    viewModel: LocationSettingsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLocationQuickPeek by rememberSaveable { mutableStateOf(false) }

    LocationSettingsContent(
        settings = uiState.settings,
        onBack = onBack,
        onToggleBackgroundTracking = viewModel::toggleBackgroundTracking,
        onToggleJournalTracking = viewModel::toggleJournalTracking,
        onToggleTimelineTracking = viewModel::toggleTimelineTracking,
        onUpdateTrackingInterval = viewModel::updateTrackingInterval,
        onShowLocationTimeline = {
            showLocationQuickPeek = true
        },
        modifier = modifier.applyScreenStyles(),
    )

    if (showLocationQuickPeek) {
        LocationTimelineBottomSheet(
            onDismissRequest = {
                showLocationQuickPeek = false
            },
            onOpenFullTimeline = {
                showLocationQuickPeek = false
                onOpenLocationTimeline()
            },
        )
    }
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
    onShowLocationTimeline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier =
            modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
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
                                            text = stringResource(Res.string.location_update_frequency, settings.trackingIntervalMinutes),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )

                                        // Calculate the step value for the current interval
                                        // 15 minutes = step 0, 30 minutes = step 1, etc.
                                        val stepValue =
                                            ((settings.trackingIntervalMinutes - 15) / 15f).coerceIn(
                                                0f,
                                                7f,
                                            )

                                        // Use steps parameter for discrete slider with 15-minute intervals
                                        // From 15 to 120 minutes in 15-minute steps = 8 steps (0 to 7)
                                        Slider(
                                            value = stepValue,
                                            onValueChange = { newValue ->
                                                // Convert from step value to minutes (15-120)
                                                val step = newValue.toInt()
                                                val minutes = 15 + (step * 15)
                                                onUpdateTrackingInterval(minutes.toLong())
                                            },
                                            valueRange = 0f..7f,
                                            steps = 6, // 8 positions (0-7) means 7 spaces, so 6 steps
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(stringResource(Res.string.text_15_min), style = MaterialTheme.typography.labelMedium)
                                            Text(stringResource(Res.string.text_30_min), style = MaterialTheme.typography.labelMedium)
                                            Text(stringResource(Res.string.text_60_min), style = MaterialTheme.typography.labelMedium)
                                            Text(
                                                stringResource(Res.string.text_120_min),
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }

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

/**
 * A block of related settings items used to group options together.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )

        MaterialContainer {
            content()
        }
    }
}

/**
 * A simple settings item with a slot for title and description and optional actions.
 *
 * @param title The title of the settings item
 * @param description The description text for the settings item. This should provide a great amount of context to the user.
 * @param overline Optional overline text for additional context or grouping. Meant to be a brief label.
 * @param onClick Callback when the item is clicked
 */
@Composable
fun SimpleSettingsItem(
    title: String,
    description: String,
    overline: String? = null,
    onClick: () -> Unit = {},
    action: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        overlineContent = overline?.let { { Text(it) } },
        trailingContent = action,
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    )
}

/**
 * A [SimpleSettingsItem] that has a toggleable switch.
 */
@Composable
fun ToggleSettingsItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    overline: String? = null,
    onClick: () -> Unit = {},
) {
    SimpleSettingsItem(
        title = title,
        description = description,
        overline = overline,
        onClick = onClick,
        action = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}
