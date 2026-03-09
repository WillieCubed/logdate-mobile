@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:max-line-length")

package app.logdate.feature.core.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import logdate.client.feature.core.generated.resources.server_configuration
import logdate.client.feature.core.generated.resources.switching_servers_keeps_your_local_data_intact_data_is_stored_separately_per_server_and_will_not_automatically_sync_between_different_servers
import logdate.client.feature.core.generated.resources.test_connection_before_saving
import logdate.client.feature.core.generated.resources.testing_connection
import logdate.client.feature.core.generated.resources.you_are_using_a_non_production_server_your_data_will_not_sync_with_logdate_cloud
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Advanced settings screen for developer and power-user options.
 *
 * This screen provides:
 * - Manual app update controls and status
 * - Server selection (LogDate Cloud or Custom)
 * - Connection validation
 *
 * @param onBack Callback for when the user presses the back button
 * @param viewModel ViewModel for the settings
 */
@Composable
fun AdvancedSettingsScreen(
    onBack: () -> Unit,
    viewModel: AdvancedSettingsViewModel = koinViewModel(),
) {
    val serverSelectionState by viewModel.serverSelectionState.collectAsState()
    val appUpdateUiState by viewModel.appUpdateUiState.collectAsState()
    val showCustomServerInfo = remember { mutableStateOf(false) }

    if (showCustomServerInfo.value) {
        CustomServerInfoBottomSheet(
            onDismiss = { showCustomServerInfo.value = false },
            onUseCustomServer = {
                viewModel.selectServerPreset(ServerPreset.CUSTOM)
                showCustomServerInfo.value = false
            },
        )
    }

    AdvancedSettingsContent(
        onBack = onBack,
        serverSelectionState = serverSelectionState,
        appUpdateUiState = appUpdateUiState,
        onSelectPreset = viewModel::selectServerPreset,
        onUpdateCustomUrl = viewModel::updateCustomServerUrl,
        onValidateAndSave = viewModel::validateAndSaveServer,
        onCheckForAppUpdates = viewModel::checkForAppUpdates,
        onCompleteAppUpdate = viewModel::completeAppUpdate,
        onShowCustomServerInfo = { showCustomServerInfo.value = true },
    )
}

/**
 * Stateless advanced settings content used by the real screen and screenshot previews.
 *
 * The layout intentionally groups app-update controls above server configuration so support,
 * QA, and power users can verify Play update behavior quickly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsContent(
    onBack: () -> Unit,
    serverSelectionState: ServerSelectionState,
    appUpdateUiState: AppUpdateUiState,
    onSelectPreset: (ServerPreset) -> Unit,
    onUpdateCustomUrl: (String) -> Unit,
    onValidateAndSave: () -> Unit,
    onCheckForAppUpdates: () -> Unit,
    onCompleteAppUpdate: () -> Unit,
    onShowCustomServerInfo: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier =
            Modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
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

                item {
                    ServerSelectionSection(
                        serverSelectionState = serverSelectionState,
                        onSelectPreset = onSelectPreset,
                        onUpdateCustomUrl = onUpdateCustomUrl,
                        onValidateAndSave = onValidateAndSave,
                        onShowCustomServerInfo = onShowCustomServerInfo,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
        }
    }
}

/**
 * App-update status and actions shown in `Settings > Advanced`.
 *
 * This surface mirrors the Android root restart prompt so the user still has a visible
 * completion path if they dismiss the global snackbar after a flexible download finishes.
 */
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

@Composable
private fun ServerSelectionSection(
    serverSelectionState: ServerSelectionState,
    onSelectPreset: (ServerPreset) -> Unit,
    onUpdateCustomUrl: (String) -> Unit,
    onValidateAndSave: () -> Unit,
    onShowCustomServerInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(Res.string.server_configuration),
            style = MaterialTheme.typography.titleMedium,
        )

        // Non-production warning
        AnimatedVisibility(visible = serverSelectionState.selectedPreset != ServerPreset.PRODUCTION) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(Res.string.you_are_using_a_non_production_server_your_data_will_not_sync_with_logdate_cloud),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xs))

        ServerSelectionCard(
            serverSelectionState = serverSelectionState,
            onSelectPreset = onSelectPreset,
            onUpdateCustomUrl = onUpdateCustomUrl,
            onShowCustomServerInfo = onShowCustomServerInfo,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Validation status and button
        ValidationStatusSection(
            validationState = serverSelectionState.validationState,
            onValidate = onValidateAndSave,
            isValidationNeeded = serverSelectionState.selectedPreset != ServerPreset.PRODUCTION,
        )

        // Info card
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                val switchingServersText =
                    stringResource(
                        Res.string
                            .switching_servers_keeps_your_local_data_intact_data_is_stored_separately_per_server_and_will_not_automatically_sync_between_different_servers,
                    )
                Text(
                    text = switchingServersText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ValidationStatusSection(
    validationState: ServerValidationState,
    onValidate: () -> Unit,
    isValidationNeeded: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (validationState) {
                is ServerValidationState.Idle -> {
                    if (isValidationNeeded) {
                        Text(
                            text = stringResource(Res.string.test_connection_before_saving),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                is ServerValidationState.Validating -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = stringResource(Res.string.testing_connection),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is ServerValidationState.Success -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text =
                                buildString {
                                    append("Connected")
                                    validationState.serverVersion?.let { append(" (v$it)") }
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                is ServerValidationState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = validationState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Button(
                onClick = onValidate,
                enabled = validationState !is ServerValidationState.Validating,
            ) {
                Text(
                    text =
                        when (validationState) {
                            is ServerValidationState.Success -> "Save"
                            else -> "Test Connection"
                        },
                )
            }
        }
    }
}

@Preview
@Composable
private fun AdvancedSettingsScreenPreview() {
    AdvancedSettingsContent(
        onBack = {},
        serverSelectionState = ServerSelectionState(),
        appUpdateUiState = AppUpdateUiState(currentVersionName = "0.1.0"),
        onSelectPreset = {},
        onUpdateCustomUrl = {},
        onValidateAndSave = {},
        onCheckForAppUpdates = {},
        onCompleteAppUpdate = {},
        onShowCustomServerInfo = {},
    )
}

@Preview
@Composable
private fun AdvancedSettingsScreenCustomSelectedPreview() {
    AdvancedSettingsContent(
        onBack = {},
        serverSelectionState =
            ServerSelectionState(
                selectedPreset = ServerPreset.CUSTOM,
                customServerUrl = "https://journal.example.com",
            ),
        appUpdateUiState =
            AppUpdateUiState(
                currentVersionName = "0.1.0",
                status = AppUpdateStatus.Available,
                flowType = AppUpdateFlowType.Flexible,
            ),
        onSelectPreset = {},
        onUpdateCustomUrl = {},
        onValidateAndSave = {},
        onCheckForAppUpdates = {},
        onCompleteAppUpdate = {},
        onShowCustomServerInfo = {},
    )
}

@Preview
@Composable
private fun AdvancedSettingsScreenValidatingPreview() {
    AdvancedSettingsContent(
        onBack = {},
        serverSelectionState =
            ServerSelectionState(
                selectedPreset = ServerPreset.CUSTOM,
                validationState = ServerValidationState.Validating,
            ),
        appUpdateUiState =
            AppUpdateUiState(
                currentVersionName = "0.1.0",
                status = AppUpdateStatus.Downloaded,
            ),
        onSelectPreset = {},
        onUpdateCustomUrl = {},
        onValidateAndSave = {},
        onCheckForAppUpdates = {},
        onCompleteAppUpdate = {},
        onShowCustomServerInfo = {},
    )
}
