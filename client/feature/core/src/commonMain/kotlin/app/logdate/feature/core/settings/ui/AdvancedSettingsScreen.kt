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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.advanced
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.https_your_server_example_com
import logdate.client.feature.core.generated.resources.localhost_8765
import logdate.client.feature.core.generated.resources.server_address
import logdate.client.feature.core.generated.resources.server_configuration
import logdate.client.feature.core.generated.resources.server_url
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
 * - Server selection (Production, Local, Custom)
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

    AdvancedSettingsContent(
        onBack = onBack,
        serverSelectionState = serverSelectionState,
        onSelectPreset = viewModel::selectServerPreset,
        onUpdateLocalAddress = viewModel::updateLocalServerAddress,
        onUpdateCustomUrl = viewModel::updateCustomServerUrl,
        onValidateAndSave = viewModel::validateAndSaveServer,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsContent(
    onBack: () -> Unit,
    serverSelectionState: ServerSelectionState,
    onSelectPreset: (ServerPreset) -> Unit,
    onUpdateLocalAddress: (String) -> Unit,
    onUpdateCustomUrl: (String) -> Unit,
    onValidateAndSave: () -> Unit,
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
                    ServerSelectionSection(
                        serverSelectionState = serverSelectionState,
                        onSelectPreset = onSelectPreset,
                        onUpdateLocalAddress = onUpdateLocalAddress,
                        onUpdateCustomUrl = onUpdateCustomUrl,
                        onValidateAndSave = onValidateAndSave,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerSelectionSection(
    serverSelectionState: ServerSelectionState,
    onSelectPreset: (ServerPreset) -> Unit,
    onUpdateLocalAddress: (String) -> Unit,
    onUpdateCustomUrl: (String) -> Unit,
    onValidateAndSave: () -> Unit,
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

        MaterialContainer {
            Column(modifier = Modifier.selectableGroup()) {
                ServerPresetOption(
                    title = "Production",
                    description = "Official LogDate Cloud server (cloud.logdate.app)",
                    selected = serverSelectionState.selectedPreset == ServerPreset.PRODUCTION,
                    onClick = { onSelectPreset(ServerPreset.PRODUCTION) },
                )

                ServerPresetOption(
                    title = "Local",
                    description = "Local development server",
                    selected = serverSelectionState.selectedPreset == ServerPreset.LOCAL,
                    onClick = { onSelectPreset(ServerPreset.LOCAL) },
                )

                AnimatedVisibility(visible = serverSelectionState.selectedPreset == ServerPreset.LOCAL) {
                    OutlinedTextField(
                        value = serverSelectionState.localServerAddress,
                        onValueChange = onUpdateLocalAddress,
                        label = { Text(stringResource(Res.string.server_address)) },
                        placeholder = { Text(stringResource(Res.string.localhost_8765)) },
                        singleLine = true,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                }

                ServerPresetOption(
                    title = "Custom",
                    description = "Connect to a self-hosted server",
                    selected = serverSelectionState.selectedPreset == ServerPreset.CUSTOM,
                    onClick = { onSelectPreset(ServerPreset.CUSTOM) },
                )

                AnimatedVisibility(visible = serverSelectionState.selectedPreset == ServerPreset.CUSTOM) {
                    OutlinedTextField(
                        value = serverSelectionState.customServerUrl,
                        onValueChange = onUpdateCustomUrl,
                        label = { Text(stringResource(Res.string.server_url)) },
                        placeholder = { Text(stringResource(Res.string.https_your_server_example_com)) },
                        singleLine = true,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                }
            }
        }

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
private fun ServerPresetOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                ).padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        onSelectPreset = {},
        onUpdateLocalAddress = {},
        onUpdateCustomUrl = {},
        onValidateAndSave = {},
    )
}

@Preview
@Composable
private fun AdvancedSettingsScreenLocalSelectedPreview() {
    AdvancedSettingsContent(
        onBack = {},
        serverSelectionState =
            ServerSelectionState(
                selectedPreset = ServerPreset.LOCAL,
                localServerAddress = "192.168.1.100:8765",
            ),
        onSelectPreset = {},
        onUpdateLocalAddress = {},
        onUpdateCustomUrl = {},
        onValidateAndSave = {},
    )
}

@Preview
@Composable
private fun AdvancedSettingsScreenValidatingPreview() {
    AdvancedSettingsContent(
        onBack = {},
        serverSelectionState =
            ServerSelectionState(
                selectedPreset = ServerPreset.LOCAL,
                validationState = ServerValidationState.Validating,
            ),
        onSelectPreset = {},
        onUpdateLocalAddress = {},
        onUpdateCustomUrl = {},
        onValidateAndSave = {},
    )
}
