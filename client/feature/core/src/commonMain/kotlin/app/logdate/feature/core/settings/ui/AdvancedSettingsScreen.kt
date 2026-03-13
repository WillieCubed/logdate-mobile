@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:max-line-length")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.core.settings.updates.AppUpdateFlowType
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.advanced
import logdate.client.feature.core.generated.resources.app_update_available
import logdate.client.feature.core.generated.resources.app_update_check_failed
import logdate.client.feature.core.generated.resources.app_update_checking
import logdate.client.feature.core.generated.resources.app_update_downloaded
import logdate.client.feature.core.generated.resources.app_update_downloading
import logdate.client.feature.core.generated.resources.app_update_immediate_required
import logdate.client.feature.core.generated.resources.app_update_manual_label
import logdate.client.feature.core.generated.resources.app_update_restart_action
import logdate.client.feature.core.generated.resources.app_update_unsupported
import logdate.client.feature.core.generated.resources.app_update_up_to_date
import logdate.client.feature.core.generated.resources.app_updates
import logdate.client.feature.core.generated.resources.app_version_label
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.check_for_updates
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Advanced settings screen for app updates and version info.
 *
 * Server configuration has moved to Account & Sign-In.
 * This screen is not shown in the main settings overview — it's accessible
 * as a developer/power-user option.
 */
@Composable
fun AdvancedSettingsScreen(
    onBack: () -> Unit,
    viewModel: AdvancedSettingsViewModel = koinViewModel(),
) {
    val appUpdateUiState by viewModel.appUpdateUiState.collectAsState()

    AdvancedSettingsContent(
        onBack = onBack,
        appUpdateUiState = appUpdateUiState,
        onCheckForAppUpdates = viewModel::checkForAppUpdates,
        onCompleteAppUpdate = viewModel::completeAppUpdate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsContent(
    onBack: () -> Unit,
    appUpdateUiState: AppUpdateUiState,
    onCheckForAppUpdates: () -> Unit,
    onCompleteAppUpdate: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier =
            Modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.advanced)) },
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
                    AppUpdateSection(
                        appUpdateUiState = appUpdateUiState,
                        onCheckForAppUpdates = onCheckForAppUpdates,
                        onCompleteAppUpdate = onCompleteAppUpdate,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
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
            AppUpdateStatus.Idle ->
                stringResource(
                    Res.string.app_version_label,
                    appUpdateUiState.currentVersionName,
                )
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

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(Res.string.app_updates),
            style = MaterialTheme.typography.titleMedium,
        )

        MaterialContainer {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.app_update_manual_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        if (appUpdateUiState.status == AppUpdateStatus.Downloaded) {
                            onCompleteAppUpdate()
                        } else {
                            onCheckForAppUpdates()
                        }
                    },
                    enabled = buttonEnabled,
                ) {
                    Text(actionLabel)
                }
            }
        }
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
