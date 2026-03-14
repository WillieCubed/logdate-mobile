@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.ui.common.LinkedToggleSettingsItem
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.memories
import logdate.client.feature.core.generated.resources.recommendations
import logdate.client.feature.core.generated.resources.recommendations_privacy_note
import logdate.client.feature.core.generated.resources.recommendations_summary_off
import logdate.client.feature.core.generated.resources.recommendations_summary_on
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Overview screen for memory-related personalization settings.
 *
 * Shows setting groups with their current state and a direct toggle.
 * Tapping the row navigates to the detail screen for sub-options.
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
        modifier = modifier,
    )
}

@Composable
fun MemoriesSettingsContent(
    onBack: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    contextualRecommendationsEnabled: Boolean,
    onToggleContextualRecommendations: (Boolean) -> Unit,
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
