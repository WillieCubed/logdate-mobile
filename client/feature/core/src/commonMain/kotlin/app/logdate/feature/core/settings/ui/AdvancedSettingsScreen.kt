@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:max-line-length")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.core.settings.updates.AppUpdateFlowType
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsNavigationItem
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.about_logdate
import logdate.client.feature.core.generated.resources.app_update_available
import logdate.client.feature.core.generated.resources.app_update_check_failed
import logdate.client.feature.core.generated.resources.app_update_checking
import logdate.client.feature.core.generated.resources.app_update_downloaded
import logdate.client.feature.core.generated.resources.app_update_downloading
import logdate.client.feature.core.generated.resources.app_update_immediate_required
import logdate.client.feature.core.generated.resources.app_update_restart_action
import logdate.client.feature.core.generated.resources.app_update_unsupported
import logdate.client.feature.core.generated.resources.app_update_up_to_date
import logdate.client.feature.core.generated.resources.app_updates
import logdate.client.feature.core.generated.resources.app_updates_description
import logdate.client.feature.core.generated.resources.app_version_label
import logdate.client.feature.core.generated.resources.check_for_updates
import logdate.client.feature.core.generated.resources.developer_tools
import logdate.client.feature.core.generated.resources.developer_tools_description
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** App information and updates, with deliberate access to developer tools. */
@Composable
fun AdvancedSettingsScreen(
    onBack: () -> Unit,
    onNavigateToDeveloperTools: () -> Unit = {},
    viewModel: AdvancedSettingsViewModel = koinViewModel(),
) {
    val appUpdateUiState by viewModel.appUpdateUiState.collectAsState()

    AdvancedSettingsContent(
        onBack = onBack,
        onNavigateToDeveloperTools = onNavigateToDeveloperTools,
        appUpdateUiState = appUpdateUiState,
        onCheckForAppUpdates = viewModel::checkForAppUpdates,
        onCompleteAppUpdate = viewModel::completeAppUpdate,
    )
}

@Composable
fun AdvancedSettingsContent(
    onBack: () -> Unit,
    appUpdateUiState: AppUpdateUiState,
    onCheckForAppUpdates: () -> Unit,
    onCompleteAppUpdate: () -> Unit,
    onNavigateToDeveloperTools: () -> Unit = {},
) {
    var versionTaps by rememberSaveable { mutableIntStateOf(0) }
    SettingsScaffold(title = stringResource(Res.string.about_logdate), onBack = onBack) {
        item {
            MaterialContainer(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.app_version_label, appUpdateUiState.currentVersionName)) },
                    modifier = Modifier.clickable { versionTaps = (versionTaps + 1).coerceAtMost(7) },
                )
                if (versionTaps >= 7) {
                    SettingsNavigationItem(
                        title = stringResource(Res.string.developer_tools),
                        description = stringResource(Res.string.developer_tools_description),
                        icon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                        onClick = onNavigateToDeveloperTools,
                    )
                }
            }
        }
        item {
            AppUpdateSection(
                appUpdateUiState = appUpdateUiState,
                onCheckForAppUpdates = onCheckForAppUpdates,
                onCompleteAppUpdate = onCompleteAppUpdate,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
    }
}

@Composable
private fun AppUpdateSection(
    appUpdateUiState: AppUpdateUiState,
    onCheckForAppUpdates: () -> Unit,
    onCompleteAppUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionLabel =
        when (appUpdateUiState.status) {
            AppUpdateStatus.Checking -> stringResource(Res.string.app_update_checking)
            AppUpdateStatus.Downloaded -> stringResource(Res.string.app_update_restart_action)
            else -> stringResource(Res.string.check_for_updates)
        }

    val statusMessage =
        when (appUpdateUiState.status) {
            AppUpdateStatus.Idle -> null
            AppUpdateStatus.Checking -> stringResource(Res.string.app_update_checking)
            AppUpdateStatus.UpToDate ->
                appUpdateUiState.message ?: stringResource(Res.string.app_update_up_to_date)
            AppUpdateStatus.Available ->
                when (appUpdateUiState.flowType) {
                    AppUpdateFlowType.Immediate -> stringResource(Res.string.app_update_immediate_required)
                    else -> stringResource(Res.string.app_update_available)
                }
            AppUpdateStatus.Downloading -> stringResource(Res.string.app_update_downloading)
            AppUpdateStatus.Downloaded -> stringResource(Res.string.app_update_downloaded)
            AppUpdateStatus.Unsupported ->
                appUpdateUiState.message ?: stringResource(Res.string.app_update_unsupported)
            AppUpdateStatus.Error ->
                appUpdateUiState.message ?: stringResource(Res.string.app_update_check_failed)
        }

    val buttonEnabled = appUpdateUiState.status != AppUpdateStatus.Checking

    SettingsSection(title = stringResource(Res.string.app_updates), modifier = modifier) {
        SettingsNavigationItem(
            title = actionLabel,
            description = statusMessage ?: stringResource(Res.string.app_updates_description),
            icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
            onClick = {
                if (appUpdateUiState.status == AppUpdateStatus.Downloaded) onCompleteAppUpdate() else onCheckForAppUpdates()
            },
            enabled = buttonEnabled,
        )
    }
}

@Preview
@Composable
private fun AdvancedSettingsScreenPreview() {
    AdvancedSettingsContent(
        onBack = {},
        appUpdateUiState = AppUpdateUiState(currentVersionName = "0.1.0"),
        onCheckForAppUpdates = {},
        onCompleteAppUpdate = {},
    )
}
