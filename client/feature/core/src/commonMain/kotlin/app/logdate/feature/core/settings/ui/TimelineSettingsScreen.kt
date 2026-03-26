@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.ui.common.LinkedToggleSettingsItem
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.day_schedule
import logdate.client.feature.core.generated.resources.day_schedule_summary_off
import logdate.client.feature.core.generated.resources.day_schedule_summary_on
import logdate.client.feature.core.generated.resources.timeline_settings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Hub screen for timeline-related settings.
 *
 * Navigable from: Settings Overview > Personal > Timeline.
 */
@Composable
fun TimelineSettingsScreen(
    onBack: () -> Unit,
    onNavigateToDayBoundary: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimelineSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    TimelineSettingsContent(
        onBack = onBack,
        onNavigateToDayBoundary = onNavigateToDayBoundary,
        sleepBasedBoundariesEnabled = uiState.dayBoundarySettings.sleepBasedBoundariesEnabled,
        onToggleSleepBasedBoundaries = viewModel::toggleSleepBasedBoundaries,
        modifier = modifier,
    )
}

@Composable
fun TimelineSettingsContent(
    onBack: () -> Unit,
    onNavigateToDayBoundary: () -> Unit,
    sleepBasedBoundariesEnabled: Boolean,
    onToggleSleepBasedBoundaries: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(Res.string.timeline_settings),
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            MaterialContainer(
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                LinkedToggleSettingsItem(
                    title = stringResource(Res.string.day_schedule),
                    description =
                        stringResource(
                            if (sleepBasedBoundariesEnabled) {
                                Res.string.day_schedule_summary_on
                            } else {
                                Res.string.day_schedule_summary_off
                            },
                        ),
                    checked = sleepBasedBoundariesEnabled,
                    onCheckedChange = onToggleSleepBasedBoundaries,
                    onNavigate = onNavigateToDayBoundary,
                )
            }
        }
    }
}
