@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.intelligence.curation.CurationConfig
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.MasterFeatureToggle
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsFeatureGroup
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.common.disabledAlpha
import app.logdate.ui.theme.Spacing
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.rewind_settings_auto_generation_helper
import logdate.client.feature.rewind.generated.resources.rewind_settings_auto_generation_label
import logdate.client.feature.rewind.generated.resources.rewind_settings_include_screenshots_helper
import logdate.client.feature.rewind.generated.resources.rewind_settings_include_screenshots_label
import logdate.client.feature.rewind.generated.resources.rewind_settings_notifications_helper
import logdate.client.feature.rewind.generated.resources.rewind_settings_notifications_label
import logdate.client.feature.rewind.generated.resources.rewind_settings_replies_helper
import logdate.client.feature.rewind.generated.resources.rewind_settings_replies_label
import logdate.client.feature.rewind.generated.resources.rewind_settings_strictness_helper
import logdate.client.feature.rewind.generated.resources.rewind_settings_strictness_label
import logdate.client.feature.rewind.generated.resources.rewind_settings_strictness_lenient
import logdate.client.feature.rewind.generated.resources.rewind_settings_strictness_standard
import logdate.client.feature.rewind.generated.resources.rewind_settings_strictness_strict
import logdate.client.feature.rewind.generated.resources.rewind_settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings screen for the Rewind feature. Lets the user opt out of automatic
 * weekly generation and the "Rewind ready" notification, choose how aggressively
 * borderline photos get filtered, and decide whether screenshots can appear.
 */
@Composable
fun RewindSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RewindSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    RewindSettingsContent(
        autoGenerationEnabled = uiState.autoGenerationEnabled,
        notificationsEnabled = uiState.notificationsEnabled,
        reflectionRepliesEnabled = uiState.reflectionRepliesEnabled,
        curationStrictness = uiState.curationStrictness,
        includeScreenshots = uiState.includeScreenshots,
        onAutoGenerationToggled = viewModel::setAutoGenerationEnabled,
        onNotificationsToggled = viewModel::setNotificationsEnabled,
        onReflectionRepliesToggled = viewModel::setReflectionRepliesEnabled,
        onCurationStrictnessChanged = viewModel::setCurationStrictness,
        onIncludeScreenshotsToggled = viewModel::setIncludeScreenshots,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewindSettingsContent(
    autoGenerationEnabled: Boolean,
    notificationsEnabled: Boolean,
    reflectionRepliesEnabled: Boolean,
    onAutoGenerationToggled: (Boolean) -> Unit,
    onNotificationsToggled: (Boolean) -> Unit,
    onReflectionRepliesToggled: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    curationStrictness: CurationConfig.Strictness = CurationConfig.Strictness.STANDARD,
    includeScreenshots: Boolean = false,
    onCurationStrictnessChanged: (CurationConfig.Strictness) -> Unit = {},
    onIncludeScreenshotsToggled: (Boolean) -> Unit = {},
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
                RewindDescription()
                RewindGenerationSection(
                    autoGenerationEnabled = autoGenerationEnabled,
                    notificationsEnabled = notificationsEnabled,
                    reflectionRepliesEnabled = reflectionRepliesEnabled,
                    onAutoGenerationToggled = onAutoGenerationToggled,
                    onNotificationsToggled = onNotificationsToggled,
                    onReflectionRepliesToggled = onReflectionRepliesToggled,
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
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                RewindCurationSection(
                    curationStrictness = curationStrictness,
                    includeScreenshots = includeScreenshots,
                    onCurationStrictnessChanged = onCurationStrictnessChanged,
                    onIncludeScreenshotsToggled = onIncludeScreenshotsToggled,
                    enabled = autoGenerationEnabled,
                )
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.rewind_settings_title),
                onBack = onBack,
                modifier = modifier,
            ) {
                item {
                    RewindDescription()
                }

                item {
                    RewindGenerationSection(
                        autoGenerationEnabled = autoGenerationEnabled,
                        notificationsEnabled = notificationsEnabled,
                        reflectionRepliesEnabled = reflectionRepliesEnabled,
                        onAutoGenerationToggled = onAutoGenerationToggled,
                        onNotificationsToggled = onNotificationsToggled,
                        onReflectionRepliesToggled = onReflectionRepliesToggled,
                    )
                }

                item {
                    RewindCurationSection(
                        curationStrictness = curationStrictness,
                        includeScreenshots = includeScreenshots,
                        onCurationStrictnessChanged = onCurationStrictnessChanged,
                        onIncludeScreenshotsToggled = onIncludeScreenshotsToggled,
                        enabled = autoGenerationEnabled,
                    )
                }
            }
        },
    )
}

@Composable
private fun RewindDescription() {
    Text(
        text = stringResource(Res.string.rewind_settings_auto_generation_helper),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg),
    )
}

@Composable
private fun RewindGenerationSection(
    autoGenerationEnabled: Boolean,
    notificationsEnabled: Boolean,
    reflectionRepliesEnabled: Boolean,
    onAutoGenerationToggled: (Boolean) -> Unit,
    onNotificationsToggled: (Boolean) -> Unit,
    onReflectionRepliesToggled: (Boolean) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        MasterFeatureToggle(
            label = stringResource(Res.string.rewind_settings_auto_generation_label),
            checked = autoGenerationEnabled,
            onCheckedChange = onAutoGenerationToggled,
        )

        SettingsFeatureGroup(enabled = autoGenerationEnabled) {
            MaterialContainer {
                ToggleSettingsItem(
                    title = stringResource(Res.string.rewind_settings_notifications_label),
                    description = stringResource(Res.string.rewind_settings_notifications_helper),
                    checked = notificationsEnabled,
                    onCheckedChange = onNotificationsToggled,
                )
                ToggleSettingsItem(
                    title = stringResource(Res.string.rewind_settings_replies_label),
                    description = stringResource(Res.string.rewind_settings_replies_helper),
                    checked = reflectionRepliesEnabled,
                    onCheckedChange = onReflectionRepliesToggled,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RewindCurationSection(
    curationStrictness: CurationConfig.Strictness,
    includeScreenshots: Boolean,
    onCurationStrictnessChanged: (CurationConfig.Strictness) -> Unit,
    onIncludeScreenshotsToggled: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SettingsFeatureGroup(enabled = enabled) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.rewind_settings_strictness_label),
                    style = MaterialTheme.typography.titleSmall,
                    modifier =
                        Modifier
                            .disabledAlpha(enabled)
                            .padding(horizontal = Spacing.lg),
                )
                SingleChoiceSegmentedButtonRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg),
                ) {
                    val options =
                        listOf(
                            CurationConfig.Strictness.LENIENT to Res.string.rewind_settings_strictness_lenient,
                            CurationConfig.Strictness.STANDARD to Res.string.rewind_settings_strictness_standard,
                            CurationConfig.Strictness.STRICT to Res.string.rewind_settings_strictness_strict,
                        )
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = curationStrictness == value,
                            onClick = { onCurationStrictnessChanged(value) },
                            enabled = enabled,
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        ) {
                            Text(stringResource(label))
                        }
                    }
                }
                Text(
                    text = stringResource(Res.string.rewind_settings_strictness_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .disabledAlpha(enabled)
                            .padding(horizontal = Spacing.lg),
                )
            }

            MaterialContainer {
                ToggleSettingsItem(
                    title = stringResource(Res.string.rewind_settings_include_screenshots_label),
                    description = stringResource(Res.string.rewind_settings_include_screenshots_helper),
                    checked = includeScreenshots,
                    onCheckedChange = onIncludeScreenshotsToggled,
                )
            }
        }
    }
}
