@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.client.sync.diagnostics.DiagnosticReportingController
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.developer_tools
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DeveloperToolsScreen(
    onBack: () -> Unit,
    diagnostics: LocalDiagnosticsViewModel = koinViewModel(),
    reporting: DiagnosticReportingController = koinInject(),
    location: LocationSettingsViewModel = koinViewModel(),
) {
    val diagnosticsState by diagnostics.state.collectAsState()
    val locationState by location.uiState.collectAsState()
    LaunchedEffect(diagnostics) { diagnostics.refresh() }
    DeveloperToolsContent(
        onBack = onBack,
        state = diagnosticsState,
        onPreview = diagnostics::refresh,
        onExport = diagnostics::export,
        onClear = diagnostics::clear,
        onSetVerboseEnabled = diagnostics::setVerboseEnabled,
        reportingContent = { AutomaticDiagnosticReportingSection(reporting) },
        locationContent = {
            DeveloperLocationSection(
                settings = locationState.settings,
                onToggleServerAssist = location::toggleServerAssist,
                onSetDefaultLocation = location::setDefaultLocation,
            )
        },
    )
}

@Composable
fun DeveloperToolsContent(
    onBack: () -> Unit,
    state: LocalDiagnosticsState,
    onPreview: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onSetVerboseEnabled: (Boolean) -> Unit,
    reportingContent: @Composable () -> Unit = {},
    locationContent: @Composable () -> Unit = {},
) {
    SettingsScaffold(title = stringResource(Res.string.developer_tools), onBack = onBack) {
        item {
            LocalDiagnosticsSettingsSection(
                state = state,
                onPreview = onPreview,
                onExport = onExport,
                onClear = onClear,
                onSetVerboseEnabled = onSetVerboseEnabled,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        item {
            Box(Modifier.padding(horizontal = Spacing.lg)) { reportingContent() }
        }
        item {
            Box(Modifier.padding(horizontal = Spacing.lg)) { locationContent() }
        }
    }
}
