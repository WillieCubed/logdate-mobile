@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.ui.common.LinkedToggleSettingsItem
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
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
import logdate.client.feature.core.generated.resources.widget_content_type_photos
import logdate.client.feature.core.generated.resources.widget_content_type_text
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
        item {
            SettingsSection(
                title = stringResource(Res.string.recommendations),
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
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

        item {
            SettingsSection(
                title = stringResource(Res.string.widget_settings),
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                Column {
                    Text(
                        text = stringResource(Res.string.recall_mode),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                    RecallModeOption(
                        title = stringResource(Res.string.recall_mode_on_this_day),
                        description = stringResource(Res.string.recall_mode_on_this_day_description),
                        selected = recallMode == RecallMode.ON_THIS_DAY,
                        onClick = { onSetRecallMode(RecallMode.ON_THIS_DAY) },
                    )
                    RecallModeOption(
                        title = stringResource(Res.string.recall_mode_rediscover),
                        description = stringResource(Res.string.recall_mode_rediscover_description),
                        selected = recallMode == RecallMode.REDISCOVER,
                        onClick = { onSetRecallMode(RecallMode.REDISCOVER) },
                    )
                }
            }
        }

        item {
            SettingsSection(
                title = stringResource(Res.string.widget_content_types),
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                Column {
                    ContentTypeCheckbox(
                        title = stringResource(Res.string.widget_content_type_text),
                        checked = WidgetContentType.TEXT in widgetContentTypes,
                        onCheckedChange = { onToggleContentType(WidgetContentType.TEXT, it) },
                    )
                    ContentTypeCheckbox(
                        title = stringResource(Res.string.widget_content_type_photos),
                        checked = WidgetContentType.PHOTOS in widgetContentTypes,
                        onCheckedChange = { onToggleContentType(WidgetContentType.PHOTOS, it) },
                    )
                    ContentTypeCheckbox(
                        title = stringResource(Res.string.widget_content_type_audio),
                        checked = WidgetContentType.AUDIO in widgetContentTypes,
                        onCheckedChange = { onToggleContentType(WidgetContentType.AUDIO, it) },
                    )
                }
            }
        }

        // Privacy note
        item {
            Surface(
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                Text(
                    text = stringResource(Res.string.recommendations_privacy_note),
                    modifier = Modifier.padding(Spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun RecallModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
            )
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ContentTypeCheckbox(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
            )
        },
        modifier = modifier.clickable { onCheckedChange(!checked) },
    )
}
