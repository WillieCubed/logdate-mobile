@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.location_advanced
import logdate.client.feature.core.generated.resources.location_advanced_description
import logdate.client.feature.core.generated.resources.location_background_tracking_description
import logdate.client.feature.core.generated.resources.location_capture_mode
import logdate.client.feature.core.generated.resources.location_capture_mode_active
import logdate.client.feature.core.generated.resources.location_capture_mode_active_description
import logdate.client.feature.core.generated.resources.location_capture_mode_passive
import logdate.client.feature.core.generated.resources.location_capture_mode_passive_description
import logdate.client.feature.core.generated.resources.location_data_privacy_note
import logdate.client.feature.core.generated.resources.location_data_stored_on_device
import logdate.client.feature.core.generated.resources.location_enable_background_tracking
import logdate.client.feature.core.generated.resources.location_services
import logdate.client.feature.core.generated.resources.location_settings
import logdate.client.feature.core.generated.resources.location_timeline
import logdate.client.feature.core.generated.resources.location_timeline_description
import logdate.client.feature.core.generated.resources.location_tracking_options
import logdate.client.feature.core.generated.resources.location_tracking_options_description
import logdate.client.feature.core.generated.resources.location_update_interval
import logdate.client.feature.core.generated.resources.location_update_interval_description
import logdate.client.feature.core.generated.resources.navigate_to_title
import logdate.client.feature.core.generated.resources.view_location_timeline
import logdate.client.feature.core.generated.resources.view_timeline
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Location settings overview screen with navigation to detail screens.
 *
 * Shows the primary background tracking toggle, a tracking mode selector,
 * and navigation items to detail screens for tracking options and interval.
 */
@Composable
fun LocationSettingsScreen(
    onBack: () -> Unit,
    onOpenLocationTimeline: () -> Unit,
    onShowLocationTimeline: () -> Unit = onOpenLocationTimeline,
    onNavigateToTrackingOptions: () -> Unit = {},
    onNavigateToInterval: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER")
    onNavigateToAdvanced: () -> Unit = {},
    viewModel: LocationSettingsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LocationSettingsContent(
        settings = uiState.settings,
        onBack = onBack,
        onToggleBackgroundTracking = viewModel::toggleBackgroundTracking,
        onSetCaptureMode = viewModel::setCaptureMode,
        onShowLocationTimeline = onShowLocationTimeline,
        onNavigateToTrackingOptions = onNavigateToTrackingOptions,
        onNavigateToInterval = onNavigateToInterval,
        onNavigateToAdvanced = onNavigateToAdvanced,
        modifier = modifier,
    )
}

@Composable
fun LocationSettingsContent(
    settings: LocationTrackingSettings,
    onBack: () -> Unit,
    onToggleBackgroundTracking: (Boolean) -> Unit,
    onSetCaptureMode: (LocationCaptureMode) -> Unit,
    onShowLocationTimeline: () -> Unit,
    onNavigateToTrackingOptions: () -> Unit,
    onNavigateToInterval: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FoldableBookLayout(
        modifier = modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                SettingsSection(
                    title = stringResource(Res.string.location_services),
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    ToggleSettingsItem(
                        title = stringResource(Res.string.location_enable_background_tracking),
                        description = stringResource(Res.string.location_background_tracking_description),
                        checked = settings.backgroundTrackingEnabled,
                        onCheckedChange = onToggleBackgroundTracking,
                    )

                    LocationSettingsNavItem(
                        title = stringResource(Res.string.location_tracking_options),
                        description = stringResource(Res.string.location_tracking_options_description),
                        onClick = onNavigateToTrackingOptions,
                    )
                    LocationSettingsNavItem(
                        title = stringResource(Res.string.location_advanced),
                        description = stringResource(Res.string.location_advanced_description),
                        onClick = onNavigateToAdvanced,
                    )
                }

                if (settings.backgroundTrackingEnabled) {
                    CaptureModeSelector(
                        currentMode = settings.captureMode,
                        onModeSelected = onSetCaptureMode,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
        },
        endPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                if (settings.backgroundTrackingEnabled && settings.captureMode == LocationCaptureMode.PASSIVE) {
                    SettingsSection(
                        title = "",
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        LocationSettingsNavItem(
                            title = stringResource(Res.string.location_update_interval),
                            description = stringResource(Res.string.location_update_interval_description),
                            onClick = onNavigateToInterval,
                        )
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
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

                LocationSettingsNotes()
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.location_settings),
                onBack = onBack,
                modifier = modifier,
            ) {
                item {
                    SettingsSection(
                        title = stringResource(Res.string.location_services),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        ToggleSettingsItem(
                            title = stringResource(Res.string.location_enable_background_tracking),
                            description = stringResource(Res.string.location_background_tracking_description),
                            checked = settings.backgroundTrackingEnabled,
                            onCheckedChange = onToggleBackgroundTracking,
                        )

                        LocationSettingsNavItem(
                            title = stringResource(Res.string.location_tracking_options),
                            description = stringResource(Res.string.location_tracking_options_description),
                            onClick = onNavigateToTrackingOptions,
                        )
                        LocationSettingsNavItem(
                            title = stringResource(Res.string.location_advanced),
                            description = stringResource(Res.string.location_advanced_description),
                            onClick = onNavigateToAdvanced,
                        )
                    }
                }

                if (settings.backgroundTrackingEnabled) {
                    item {
                        CaptureModeSelector(
                            currentMode = settings.captureMode,
                            onModeSelected = onSetCaptureMode,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }

                    if (settings.captureMode == LocationCaptureMode.PASSIVE) {
                        item {
                            SettingsSection(
                                title = "",
                                modifier = Modifier.padding(horizontal = Spacing.lg),
                            ) {
                                LocationSettingsNavItem(
                                    title = stringResource(Res.string.location_update_interval),
                                    description = stringResource(Res.string.location_update_interval_description),
                                    onClick = onNavigateToInterval,
                                )
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
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

                item {
                    LocationSettingsNotes()
                }
            }
        },
    )
}

@Composable
private fun CaptureModeSelector(
    currentMode: LocationCaptureMode,
    onModeSelected: (LocationCaptureMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(Res.string.location_capture_mode),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = currentMode == LocationCaptureMode.PASSIVE,
                onClick = { onModeSelected(LocationCaptureMode.PASSIVE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(stringResource(Res.string.location_capture_mode_passive))
            }

            SegmentedButton(
                selected = currentMode == LocationCaptureMode.ACTIVE,
                onClick = { onModeSelected(LocationCaptureMode.ACTIVE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(stringResource(Res.string.location_capture_mode_active))
            }
        }

        Text(
            text =
                if (currentMode == LocationCaptureMode.ACTIVE) {
                    stringResource(Res.string.location_capture_mode_active_description)
                } else {
                    stringResource(Res.string.location_capture_mode_passive_description)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LocationSettingsNavItem(
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
                contentDescription =
                    stringResource(
                        Res.string.navigate_to_title,
                        title,
                    ),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
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
