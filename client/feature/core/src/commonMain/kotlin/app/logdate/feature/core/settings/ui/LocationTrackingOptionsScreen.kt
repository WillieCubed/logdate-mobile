@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.location_services
import logdate.client.feature.core.generated.resources.location_track_journal_entries
import logdate.client.feature.core.generated.resources.location_track_journal_entries_description
import logdate.client.feature.core.generated.resources.location_track_timeline_review
import logdate.client.feature.core.generated.resources.location_track_timeline_review_description
import logdate.client.feature.core.generated.resources.location_tracking_options
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LocationTrackingOptionsScreen(
    onBack: () -> Unit,
    viewModel: LocationSettingsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LocationTrackingOptionsContent(
        settings = uiState.settings,
        onBack = onBack,
        onToggleJournalTracking = viewModel::toggleJournalTracking,
        onToggleTimelineTracking = viewModel::toggleTimelineTracking,
        modifier = modifier,
    )
}

@Composable
fun LocationTrackingOptionsContent(
    settings: LocationTrackingSettings,
    onBack: () -> Unit,
    onToggleJournalTracking: (Boolean) -> Unit,
    onToggleTimelineTracking: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(Res.string.location_tracking_options),
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            SettingsSection(
                title = stringResource(Res.string.location_services),
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
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
}
