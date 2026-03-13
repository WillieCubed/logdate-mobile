@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.back
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesSettingsContent(
    onBack: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    contextualRecommendationsEnabled: Boolean,
    onToggleContextualRecommendations: (Boolean) -> Unit,
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
                title = { Text(stringResource(Res.string.memories)) },
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
                item {
                    MaterialContainer(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        SurfaceItem {
                            ListItem(
                                headlineContent = {
                                    Text(stringResource(Res.string.recommendations))
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(
                                            if (contextualRecommendationsEnabled) {
                                                Res.string.recommendations_summary_on
                                            } else {
                                                Res.string.recommendations_summary_off
                                            },
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                leadingContent = {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                },
                                trailingContent = {
                                    Switch(
                                        checked = contextualRecommendationsEnabled,
                                        onCheckedChange = onToggleContextualRecommendations,
                                    )
                                },
                                modifier = Modifier.clickable(onClick = onNavigateToRecommendations),
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.lg),
                        )
                    }
                }
            }
        }
    }
}
