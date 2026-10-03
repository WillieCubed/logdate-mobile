@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.labs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.labs_empty
import logdate.client.feature.core.generated.resources.labs_intro
import logdate.client.feature.core.generated.resources.labs_section_unreleased
import logdate.client.feature.core.generated.resources.labs_settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Labs: switches for features that have not launched, for testing them in a release build.
 *
 * Settings has no row for it; tapping the version on the About page seven times opens it.
 */
@Composable
fun LabsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LabsSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LabsSettingsContent(
        uiState = uiState,
        onBack = onBack,
        onSetEnabled = viewModel::setEnabled,
        modifier = modifier,
    )
}

@Composable
fun LabsSettingsContent(
    uiState: LabsUiState,
    onBack: () -> Unit,
    onSetEnabled: (FeatureFlag, Boolean) -> Unit,
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
                    text = stringResource(Res.string.labs_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
                LabsIntro(modifier = Modifier.padding(horizontal = Spacing.lg))
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
                LabsFlagsSection(
                    uiState = uiState,
                    onSetEnabled = onSetEnabled,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.labs_settings_title),
                onBack = onBack,
            ) {
                item {
                    LabsIntro(modifier = Modifier.padding(horizontal = Spacing.lg))
                }
                item {
                    LabsFlagsSection(
                        uiState = uiState,
                        onSetEnabled = onSetEnabled,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
        },
    )
}

@Composable
private fun LabsIntro(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.labs_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun LabsFlagsSection(
    uiState: LabsUiState,
    onSetEnabled: (FeatureFlag, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState !is LabsUiState.Ready) return
    if (uiState.flags.isEmpty()) {
        Text(
            text = stringResource(Res.string.labs_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
        return
    }
    SettingsSection(
        title = stringResource(Res.string.labs_section_unreleased),
        modifier = modifier,
    ) {
        uiState.flags.forEach { row ->
            val copy = row.flag.labsCopy() ?: return@forEach
            ToggleSettingsItem(
                title = stringResource(copy.title),
                description = stringResource(copy.description),
                checked = row.enabled,
                onCheckedChange = { enabled -> onSetEnabled(row.flag, enabled) },
            )
        }
    }
}
