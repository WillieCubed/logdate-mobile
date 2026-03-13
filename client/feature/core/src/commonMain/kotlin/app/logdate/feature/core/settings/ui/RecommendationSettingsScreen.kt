@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.PrimaryTogglePill
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.recall_options
import logdate.client.feature.core.generated.resources.recommendations
import logdate.client.feature.core.generated.resources.recommendations_detail_description
import logdate.client.feature.core.generated.resources.smart_recall
import logdate.client.feature.core.generated.resources.smart_recall_description
import logdate.client.feature.core.generated.resources.use_recommendations
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Detail screen for the Recommendations setting group.
 *
 * Follows the Pixel Settings detail pattern: large title, description,
 * primary toggle in a prominent row, then sub-options grouped by section.
 */
@Composable
fun RecommendationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoriesSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    RecommendationSettingsContent(
        settings = uiState.settings,
        onBack = onBack,
        onToggleContextualRecommendations = viewModel::toggleContextualRecommendations,
        onToggleSmartRecall = viewModel::toggleAiRecall,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationSettingsContent(
    settings: MemoriesSettings,
    onBack: () -> Unit,
    onToggleContextualRecommendations: (Boolean) -> Unit,
    onToggleSmartRecall: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier =
            modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.recommendations)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
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
                // Description
                item {
                    Text(
                        text = stringResource(Res.string.recommendations_detail_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }

                // Primary toggle in a colored pill
                item {
                    PrimaryTogglePill(
                        label = stringResource(Res.string.use_recommendations),
                        checked = settings.contextualRecommendationsEnabled,
                        onCheckedChange = onToggleContextualRecommendations,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }

                // Sub-options (only meaningful when recommendations are enabled)
                if (settings.contextualRecommendationsEnabled) {
                    item {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = stringResource(Res.string.recall_options),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = Spacing.sm),
                            )

                            MaterialContainer {
                                ToggleSettingsItem(
                                    title = stringResource(Res.string.smart_recall),
                                    description =
                                        stringResource(Res.string.smart_recall_description),
                                    checked = settings.aiRecallEnabled,
                                    onCheckedChange = onToggleSmartRecall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
