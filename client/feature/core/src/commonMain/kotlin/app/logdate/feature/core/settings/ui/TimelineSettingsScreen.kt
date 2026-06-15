@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.domain.dayboundary.HealthConnectStatus
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsNavigationItem
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.day_schedule
import logdate.client.feature.core.generated.resources.day_schedule_summary_off
import logdate.client.feature.core.generated.resources.day_schedule_summary_on
import logdate.client.feature.core.generated.resources.day_schedule_summary_paused
import logdate.client.feature.core.generated.resources.timeline_settings
import org.jetbrains.compose.resources.StringResource
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
        healthConnectStatus = uiState.healthConnectStatus,
        modifier = modifier,
    )
}

@Composable
fun TimelineSettingsContent(
    onBack: () -> Unit,
    onNavigateToDayBoundary: () -> Unit,
    sleepBasedBoundariesEnabled: Boolean,
    healthConnectStatus: HealthConnectStatus,
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
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.timeline_settings),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
                Text(
                    text = stringResource(Res.string.day_schedule),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        },
        endPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
            ) {
                MaterialContainer(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    SettingsNavigationItem(
                        title = stringResource(Res.string.day_schedule),
                        description =
                            stringResource(
                                resolveDayBoundarySummaryText(
                                    sleepBasedBoundariesEnabled = sleepBasedBoundariesEnabled,
                                    healthConnectStatus = healthConnectStatus,
                                ),
                            ),
                        icon = { Icon(Icons.Rounded.Bedtime, contentDescription = null) },
                        onClick = onNavigateToDayBoundary,
                    )
                }
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.timeline_settings),
                onBack = onBack,
                modifier = modifier,
            ) {
                item {
                    MaterialContainer(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        SettingsNavigationItem(
                            title = stringResource(Res.string.day_schedule),
                            description =
                                stringResource(
                                    resolveDayBoundarySummaryText(
                                        sleepBasedBoundariesEnabled = sleepBasedBoundariesEnabled,
                                        healthConnectStatus = healthConnectStatus,
                                    ),
                                ),
                            icon = { Icon(Icons.Rounded.Bedtime, contentDescription = null) },
                            onClick = onNavigateToDayBoundary,
                        )
                    }
                }
            }
        },
    )
}

internal fun resolveDayBoundarySummaryText(
    sleepBasedBoundariesEnabled: Boolean,
    healthConnectStatus: HealthConnectStatus,
): StringResource =
    when {
        !sleepBasedBoundariesEnabled -> Res.string.day_schedule_summary_off
        healthConnectStatus == HealthConnectStatus.CONNECTED ||
            healthConnectStatus == HealthConnectStatus.CHECKING
        -> Res.string.day_schedule_summary_on
        else -> Res.string.day_schedule_summary_paused
    }
