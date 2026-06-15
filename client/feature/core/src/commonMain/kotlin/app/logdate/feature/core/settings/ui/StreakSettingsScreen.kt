@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.client.domain.streak.StreakData
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.PrimaryTogglePill
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.streak_day_count
import logdate.client.feature.core.generated.resources.streak_keep_it_up
import logdate.client.feature.core.generated.resources.streak_milestones_info
import logdate.client.feature.core.generated.resources.streaks
import logdate.client.feature.core.generated.resources.streaks_detail_description
import logdate.client.feature.core.generated.resources.track_journaling_streak
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings screen where users can enable or disable daily journaling streak tracking
 * and view their current streak count with milestone information.
 */
@Composable
fun StreakSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StreakSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    StreakSettingsContent(
        streakData = uiState.streakData,
        onBack = onBack,
        onToggleStreakTracking = viewModel::toggleStreakTracking,
        modifier = modifier,
    )
}

@Composable
fun StreakSettingsContent(
    streakData: StreakData,
    onBack: () -> Unit,
    onToggleStreakTracking: (Boolean) -> Unit,
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
                    text = stringResource(Res.string.streaks_detail_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )

                PrimaryTogglePill(
                    label = stringResource(Res.string.track_journaling_streak),
                    checked = streakData.isEnabled,
                    onCheckedChange = onToggleStreakTracking,
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
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (streakData.isEnabled) {
                    Box(
                        modifier =
                            Modifier
                                .size(120.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = streakData.currentStreak.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Text(
                        text = stringResource(Res.string.streak_day_count, streakData.currentStreak),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    MaterialContainer(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        Text(
                            text = stringResource(Res.string.streak_keep_it_up),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.lg),
                        )
                    }

                    Text(
                        text = stringResource(Res.string.streak_milestones_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.streaks),
                onBack = onBack,
                modifier = modifier,
            ) {
                item {
                    Text(
                        text = stringResource(Res.string.streaks_detail_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
                item {
                    PrimaryTogglePill(
                        label = stringResource(Res.string.track_journaling_streak),
                        checked = streakData.isEnabled,
                        onCheckedChange = onToggleStreakTracking,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
                if (streakData.isEnabled) {
                    item {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(120.dp)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = streakData.currentStreak.toString(),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Text(
                                text = stringResource(Res.string.streak_day_count, streakData.currentStreak),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    item {
                        MaterialContainer(
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        ) {
                            Text(
                                text = stringResource(Res.string.streak_keep_it_up),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.lg),
                            )
                        }
                    }
                    item {
                        Text(
                            text = stringResource(Res.string.streak_milestones_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }
                }
            }
        },
    )
}
