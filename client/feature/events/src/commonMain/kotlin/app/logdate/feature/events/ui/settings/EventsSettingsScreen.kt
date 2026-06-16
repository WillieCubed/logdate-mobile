@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.MasterFeatureToggle
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsFeatureGroup
import app.logdate.ui.common.SettingsNavigationItem
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.events.generated.resources.Res
import logdate.client.feature.events.generated.resources.events_calendar_title
import logdate.client.feature.events.generated.resources.events_hub_calendar_row_subtitle
import logdate.client.feature.events.generated.resources.events_hub_calendar_row_title
import logdate.client.feature.events.generated.resources.events_hub_description
import logdate.client.feature.events.generated.resources.events_hub_master_toggle
import logdate.client.feature.events.generated.resources.events_hub_smart_names_off
import logdate.client.feature.events.generated.resources.events_hub_smart_names_on
import logdate.client.feature.events.generated.resources.events_hub_smart_names_title
import logdate.client.feature.events.generated.resources.events_hub_sync_row_subtitle
import logdate.client.feature.events.generated.resources.events_hub_sync_row_title
import logdate.client.feature.events.generated.resources.events_hub_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Top-level events hub screen.
 *
 * Single entry point for everything event-related: turn the inference on or off,
 * toggle smart naming, browse the month grid, and drill into device calendar sync.
 * Replaces the three separate top-level rows that used to live in the settings hub
 * (auto-events / calendar / calendar sync) so the user only sees one feature, not
 * three.
 */
@Composable
fun EventsSettingsScreen(
    onBack: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToCalendarSync: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventsSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    EventsSettingsContent(
        uiState = uiState,
        onBack = onBack,
        onAutoEventsToggled = viewModel::setAutoEventsEnabled,
        onSmartNamingToggled = viewModel::setSmartNamingEnabled,
        onNavigateToCalendar = onNavigateToCalendar,
        onNavigateToCalendarSync = onNavigateToCalendarSync,
        modifier = modifier,
    )
}

@Composable
fun EventsSettingsContent(
    uiState: EventsSettingsUiState,
    onBack: () -> Unit,
    onAutoEventsToggled: (Boolean) -> Unit,
    onSmartNamingToggled: (Boolean) -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToCalendarSync: () -> Unit,
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
                Text(
                    text = stringResource(Res.string.events_hub_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )

                MasterFeatureToggle(
                    label = stringResource(Res.string.events_hub_master_toggle),
                    checked = uiState.isAutoEventsEnabled,
                    onCheckedChange = onAutoEventsToggled,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )

                SettingsFeatureGroup(enabled = uiState.isAutoEventsEnabled) {
                    MaterialContainer(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        ToggleSettingsItem(
                            title = stringResource(Res.string.events_hub_smart_names_title),
                            description =
                                if (uiState.isSmartNamingEnabled) {
                                    stringResource(Res.string.events_hub_smart_names_on)
                                } else {
                                    stringResource(Res.string.events_hub_smart_names_off)
                                },
                            checked = uiState.isSmartNamingEnabled,
                            onCheckedChange = onSmartNamingToggled,
                        )
                    }
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
                SettingsSection(
                    title = stringResource(Res.string.events_calendar_title),
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    SettingsNavigationItem(
                        title = stringResource(Res.string.events_hub_calendar_row_title),
                        description = stringResource(Res.string.events_hub_calendar_row_subtitle),
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                        onClick = onNavigateToCalendar,
                    )
                    SettingsNavigationItem(
                        title = stringResource(Res.string.events_hub_sync_row_title),
                        description = stringResource(Res.string.events_hub_sync_row_subtitle),
                        icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                        onClick = onNavigateToCalendarSync,
                    )
                }
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.events_hub_title),
                onBack = onBack,
                modifier = modifier,
            ) {
                item {
                    Text(
                        text = stringResource(Res.string.events_hub_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
                item {
                    MasterFeatureToggle(
                        label = stringResource(Res.string.events_hub_master_toggle),
                        checked = uiState.isAutoEventsEnabled,
                        onCheckedChange = onAutoEventsToggled,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
                item {
                    SettingsFeatureGroup(enabled = uiState.isAutoEventsEnabled) {
                        MaterialContainer(
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        ) {
                            ToggleSettingsItem(
                                title = stringResource(Res.string.events_hub_smart_names_title),
                                description =
                                    if (uiState.isSmartNamingEnabled) {
                                        stringResource(Res.string.events_hub_smart_names_on)
                                    } else {
                                        stringResource(Res.string.events_hub_smart_names_off)
                                    },
                                checked = uiState.isSmartNamingEnabled,
                                onCheckedChange = onSmartNamingToggled,
                            )
                        }
                    }
                }
                item {
                    SettingsSection(
                        title = stringResource(Res.string.events_calendar_title),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        SettingsNavigationItem(
                            title = stringResource(Res.string.events_hub_calendar_row_title),
                            description = stringResource(Res.string.events_hub_calendar_row_subtitle),
                            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                            onClick = onNavigateToCalendar,
                        )
                        SettingsNavigationItem(
                            title = stringResource(Res.string.events_hub_sync_row_title),
                            description = stringResource(Res.string.events_hub_sync_row_subtitle),
                            icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                            onClick = onNavigateToCalendarSync,
                        )
                    }
                }
            }
        },
    )
}
