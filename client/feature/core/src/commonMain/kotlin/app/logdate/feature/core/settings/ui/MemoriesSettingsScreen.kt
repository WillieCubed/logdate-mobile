@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.ui.common.LinkedToggleSettingsItem
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SimpleSettingsItem
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.memories
import logdate.client.feature.core.generated.resources.recall_mode
import logdate.client.feature.core.generated.resources.recall_mode_on_this_day
import logdate.client.feature.core.generated.resources.recall_mode_on_this_day_description
import logdate.client.feature.core.generated.resources.recall_mode_rediscover
import logdate.client.feature.core.generated.resources.recall_mode_rediscover_description
import logdate.client.feature.core.generated.resources.recommendations
import logdate.client.feature.core.generated.resources.recommendations_privacy_note
import logdate.client.feature.core.generated.resources.recommendations_summary_off
import logdate.client.feature.core.generated.resources.recommendations_summary_on
import logdate.client.feature.core.generated.resources.widget_content_type_audio
import logdate.client.feature.core.generated.resources.widget_content_type_audio_description
import logdate.client.feature.core.generated.resources.widget_content_type_photos
import logdate.client.feature.core.generated.resources.widget_content_type_photos_description
import logdate.client.feature.core.generated.resources.widget_content_type_text
import logdate.client.feature.core.generated.resources.widget_content_type_text_description
import logdate.client.feature.core.generated.resources.widget_content_types
import logdate.client.feature.core.generated.resources.widget_settings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Overview screen for memory-related personalization settings.
 */
@Composable
fun MemoriesSettingsScreen(
    onBack: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoriesSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    MemoriesSettingsContent(
        onBack = onBack,
        onNavigateToRecommendations = onNavigateToRecommendations,
        contextualRecommendationsEnabled = uiState.settings.contextualRecommendationsEnabled,
        onToggleContextualRecommendations = viewModel::toggleContextualRecommendations,
        recallMode = uiState.settings.recallMode,
        onSetRecallMode = viewModel::setRecallMode,
        widgetContentTypes = uiState.settings.widgetContentTypes,
        onToggleContentType = viewModel::toggleWidgetContentType,
        modifier = modifier,
    )
}

@Composable
fun MemoriesSettingsContent(
    onBack: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    contextualRecommendationsEnabled: Boolean,
    onToggleContextualRecommendations: (Boolean) -> Unit,
    recallMode: RecallMode,
    onSetRecallMode: (RecallMode) -> Unit,
    widgetContentTypes: Set<WidgetContentType>,
    onToggleContentType: (WidgetContentType, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(Res.string.memories),
        onBack = onBack,
        modifier = modifier,
    ) {
        // Recommendations section
        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.recommendations),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )

                MaterialContainer {
                    LinkedToggleSettingsItem(
                        title = stringResource(Res.string.recommendations),
                        description =
                            stringResource(
                                if (contextualRecommendationsEnabled) {
                                    Res.string.recommendations_summary_on
                                } else {
                                    Res.string.recommendations_summary_off
                                },
                            ),
                        checked = contextualRecommendationsEnabled,
                        onCheckedChange = onToggleContextualRecommendations,
                        onNavigate = onNavigateToRecommendations,
                    )
                }
            }
        }

        // Widget section
        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.widget_settings),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )

                // Recall mode
                Text(
                    text = stringResource(Res.string.recall_mode),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                MaterialContainer {
                    SimpleSettingsItem(
                        title = stringResource(Res.string.recall_mode_on_this_day),
                        description = stringResource(Res.string.recall_mode_on_this_day_description),
                        onClick = { onSetRecallMode(RecallMode.ON_THIS_DAY) },
                    ) {
                        RadioButton(
                            selected = recallMode == RecallMode.ON_THIS_DAY,
                            onClick = { onSetRecallMode(RecallMode.ON_THIS_DAY) },
                        )
                    }

                    SimpleSettingsItem(
                        title = stringResource(Res.string.recall_mode_rediscover),
                        description = stringResource(Res.string.recall_mode_rediscover_description),
                        onClick = { onSetRecallMode(RecallMode.REDISCOVER) },
                    ) {
                        RadioButton(
                            selected = recallMode == RecallMode.REDISCOVER,
                            onClick = { onSetRecallMode(RecallMode.REDISCOVER) },
                        )
                    }
                }

                // Content types
                Text(
                    text = stringResource(Res.string.widget_content_types),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )

                MaterialContainer {
                    ToggleSettingsItem(
                        title = stringResource(Res.string.widget_content_type_text),
                        description = stringResource(Res.string.widget_content_type_text_description),
                        checked = WidgetContentType.TEXT in widgetContentTypes,
                        onCheckedChange = { onToggleContentType(WidgetContentType.TEXT, it) },
                    )
                    ToggleSettingsItem(
                        title = stringResource(Res.string.widget_content_type_photos),
                        description = stringResource(Res.string.widget_content_type_photos_description),
                        checked = WidgetContentType.PHOTOS in widgetContentTypes,
                        onCheckedChange = { onToggleContentType(WidgetContentType.PHOTOS, it) },
                    )
                    ToggleSettingsItem(
                        title = stringResource(Res.string.widget_content_type_audio),
                        description = stringResource(Res.string.widget_content_type_audio_description),
                        checked = WidgetContentType.AUDIO in widgetContentTypes,
                        onCheckedChange = { onToggleContentType(WidgetContentType.AUDIO, it) },
                    )
                }
            }
        }

        // Privacy note
        item {
            Text(
                text = stringResource(Res.string.recommendations_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
        }
    }
}
