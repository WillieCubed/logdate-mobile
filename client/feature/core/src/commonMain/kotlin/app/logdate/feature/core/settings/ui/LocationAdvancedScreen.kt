@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.location_advanced
import logdate.client.feature.core.generated.resources.location_capture_experiment
import logdate.client.feature.core.generated.resources.location_capture_experiment_description
import logdate.client.feature.core.generated.resources.location_server_assist
import logdate.client.feature.core.generated.resources.location_server_assist_description
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LocationAdvancedScreen(
    onBack: () -> Unit,
    viewModel: LocationSettingsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LocationAdvancedContent(
        settings = uiState.settings,
        onBack = onBack,
        onToggleMirroredExperiment = viewModel::toggleMirroredExperiment,
        onToggleServerAssist = viewModel::toggleServerAssist,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationAdvancedContent(
    settings: LocationTrackingSettings,
    onBack: () -> Unit,
    onToggleMirroredExperiment: (Boolean) -> Unit,
    onToggleServerAssist: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val mirroredExperimentEnabled = settings.captureMode == LocationCaptureMode.EXPERIMENT_MIRRORED

    Scaffold(
        modifier =
            modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.location_advanced)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
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
                    SettingsSection(
                        title = stringResource(Res.string.location_advanced),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        ToggleSettingsItem(
                            title = stringResource(Res.string.location_capture_experiment),
                            description = stringResource(Res.string.location_capture_experiment_description),
                            checked = mirroredExperimentEnabled,
                            onCheckedChange = onToggleMirroredExperiment,
                        )
                        ToggleSettingsItem(
                            title = stringResource(Res.string.location_server_assist),
                            description = stringResource(Res.string.location_server_assist_description),
                            checked = settings.serverAssistEnabled,
                            onCheckedChange = onToggleServerAssist,
                        )
                    }
                }
            }
        }
    }
}
