@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_passkey_create_success_title
import logdate.client.feature.core.generated.resources.app_security
import logdate.client.feature.core.generated.resources.disable_biometric_lock_message
import logdate.client.feature.core.generated.resources.disable_biometric_lock_title
import logdate.client.feature.core.generated.resources.location_privacy
import logdate.client.feature.core.generated.resources.location_settings
import logdate.client.feature.core.generated.resources.manage_location_tracking_and_privacy_preferences
import logdate.client.feature.core.generated.resources.navigate_to_location_settings
import logdate.client.feature.core.generated.resources.operation_passkey
import logdate.client.feature.core.generated.resources.passkey_operation_creating
import logdate.client.feature.core.generated.resources.passkey_operation_creating_message
import logdate.client.feature.core.generated.resources.passkey_operation_removing
import logdate.client.feature.core.generated.resources.passkey_operation_removing_message
import logdate.client.feature.core.generated.resources.passkey_removed_successfully
import logdate.client.feature.core.generated.resources.privacy_and_security
import logdate.client.feature.core.generated.resources.privacy_security_description
import logdate.client.feature.core.generated.resources.recovery_phrase_missing
import logdate.client.feature.core.generated.resources.recovery_phrase_settings_description
import logdate.client.feature.core.generated.resources.recovery_phrase_settings_title
import logdate.client.feature.core.generated.resources.recovery_phrase_warning
import logdate.client.feature.core.generated.resources.remove_passkey
import logdate.client.feature.core.generated.resources.remove_passkey_from_device
import logdate.client.feature.core.generated.resources.settings_biometric_description
import logdate.client.feature.core.generated.resources.settings_biometric_label
import logdate.client.feature.core.generated.resources.system_search_visibility_description
import logdate.client.feature.core.generated.resources.system_search_visibility_label
import logdate.client.feature.core.generated.resources.system_search_visibility_section
import logdate.client.ui.generated.resources.common_cancel
import logdate.client.ui.generated.resources.common_confirm
import logdate.client.ui.generated.resources.common_remove
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import logdate.client.ui.generated.resources.Res as UiRes

/**
 * Privacy and security settings screen.
 *
 * This screen automatically adapts to different screen sizes:
 * - Large screens: Acts as a detail pane with minimal header (when in two-pane layout)
 * - Small screens: Standard screen with back navigation
 *
 * @param onBack Callback for when the user presses the back button
 * @param onNavigateToLocationSettings Callback for navigating to location settings
 * @param viewModel ViewModel for the settings
 */
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    onNavigateToLocationSettings: () -> Unit = {},
    viewModel: PrivacySettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val revocationState by viewModel.passkeyRevocationState.collectAsState()
    val creationState by viewModel.passkeyCreationState.collectAsState()
    val recoveryPhraseRevealState by viewModel.recoveryPhraseRevealState.collectAsState()

    PrivacySettingsContent(
        onBack = onBack,
        onSetBiometricsEnabled = viewModel::setBiometricEnabled,
        onSetSystemSearchVisibilityEnabled = viewModel::setSystemSearchVisibilityEnabled,
        isBiometricsEnabled = state.isBiometricsEnabled,
        isAuthenticated = state.isAuthenticated,
        isSystemSearchVisibilityEnabled = state.isSystemSearchVisibilityEnabled,
        showSystemSearchVisibilityToggle = state.showSystemSearchVisibilityToggle,
        passkeys = state.passkeys,
        onCreatePasskey = { viewModel.createPasskey() },
        onRevokePasskey = { passkey -> viewModel.revokePasskey(passkey.id) },
        recoveryPhraseRevealState = recoveryPhraseRevealState,
        onRevealRecoveryPhrase = viewModel::revealRecoveryPhrase,
        onHideRecoveryPhrase = viewModel::hideRecoveryPhrase,
        onNavigateToLocationSettings = onNavigateToLocationSettings,
        revocationState = revocationState,
        creationState = creationState,
    )
}

/**
 * Composable for confirming passkey deletion.
 *
 * @param passkey The passkey to delete, or null if no confirmation is needed
 * @param onConfirm Callback for when the user confirms deletion
 * @param onDismiss Callback for when the user dismisses the dialog
 */
@Composable
private fun PasskeyDeletionConfirmationDialog(
    passkey: PasskeyInfo?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (passkey != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.remove_passkey)) },
            text = {
                Text(
                    stringResource(
                        Res.string.remove_passkey_from_device,
                        passkey.device,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(UiRes.string.common_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(UiRes.string.common_cancel))
                }
            },
        )
    }
}

/**
 * Composable for showing a loading dialog during passkey operations.
 *
 * @param operation The type of operation ("Removing" or "Creating")
 * @param message The message to display
 * @param isLoading Whether the loading dialog should be shown
 */
@Composable
private fun PasskeyOperationLoadingDialog(
    operation: String = "Removing",
    message: String = "Please wait while we remove your passkey",
    isLoading: Boolean,
) {
    if (isLoading) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissal during operation */ },
            title = {
                Text(
                    stringResource(
                        Res.string.operation_passkey,
                        operation,
                    ),
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            },
            confirmButton = { },
        )
    }
}

@Composable
fun PrivacySettingsContent(
    onBack: () -> Unit,
    onSetBiometricsEnabled: (enabled: Boolean) -> Unit,
    onSetSystemSearchVisibilityEnabled: (enabled: Boolean) -> Unit = {},
    isBiometricsEnabled: Boolean,
    isAuthenticated: Boolean,
    isSystemSearchVisibilityEnabled: Boolean = false,
    showSystemSearchVisibilityToggle: Boolean = false,
    passkeys: List<PasskeyInfo> = emptyList(),
    onCreatePasskey: () -> Unit = {},
    onRevokePasskey: (PasskeyInfo) -> Unit = {},
    recoveryPhraseRevealState: RecoveryPhraseRevealState = RecoveryPhraseRevealState.Hidden,
    onRevealRecoveryPhrase: () -> Unit = {},
    onHideRecoveryPhrase: () -> Unit = {},
    onNavigateToLocationSettings: () -> Unit = {},
    revocationState: PasskeyRevocationState = PasskeyRevocationState.Idle,
    creationState: PasskeyCreationState = PasskeyCreationState.Idle,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var passkeyToDelete by remember { mutableStateOf<PasskeyInfo?>(null) }
    var showDisableBiometricsDialog by remember { mutableStateOf(false) }

    val passkeyRemovedMessage = stringResource(Res.string.passkey_removed_successfully)
    val passkeyCreatedMessage = stringResource(Res.string.account_passkey_create_success_title)

    // Show feedback for passkey operations
    LaunchedEffect(revocationState) {
        when (revocationState) {
            is PasskeyRevocationState.Success -> {
                snackbarHostState.showSnackbar(passkeyRemovedMessage)
            }
            is PasskeyRevocationState.Error -> {
                snackbarHostState.showSnackbar(revocationState.message)
            }
            else -> { /* No action needed */ }
        }
    }

    LaunchedEffect(creationState) {
        when (creationState) {
            is PasskeyCreationState.Success -> {
                snackbarHostState.showSnackbar(passkeyCreatedMessage)
            }
            is PasskeyCreationState.Error -> {
                snackbarHostState.showSnackbar(creationState.message)
            }
            else -> { /* No action needed */ }
        }
    }

    // Confirmation dialog for passkey deletion
    PasskeyDeletionConfirmationDialog(
        passkey = passkeyToDelete,
        onConfirm = {
            passkeyToDelete?.let { onRevokePasskey(it) }
            passkeyToDelete = null
        },
        onDismiss = { passkeyToDelete = null },
    )

    // Loading dialogs for passkey operations
    PasskeyOperationLoadingDialog(
        operation = stringResource(Res.string.passkey_operation_removing),
        message = stringResource(Res.string.passkey_operation_removing_message),
        isLoading = revocationState == PasskeyRevocationState.Revoking,
    )

    PasskeyOperationLoadingDialog(
        operation = stringResource(Res.string.passkey_operation_creating),
        message = stringResource(Res.string.passkey_operation_creating_message),
        isLoading = creationState == PasskeyCreationState.Creating,
    )

    RecoveryPhraseDialog(
        state = recoveryPhraseRevealState,
        onDismiss = onHideRecoveryPhrase,
    )

    FoldableBookLayout(
        modifier = Modifier.fillMaxSize(),
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
                Text(
                    text = stringResource(Res.string.privacy_security_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )

                SettingsSection(
                    title = stringResource(Res.string.app_security),
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    ToggleSettingsItem(
                        title = stringResource(Res.string.settings_biometric_label),
                        description = stringResource(Res.string.settings_biometric_description),
                        checked = isBiometricsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                onSetBiometricsEnabled(true)
                            } else {
                                showDisableBiometricsDialog = true
                            }
                        },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.recovery_phrase_settings_title)) },
                        supportingContent = { Text(stringResource(Res.string.recovery_phrase_settings_description)) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable(onClick = onRevealRecoveryPhrase),
                    )
                }

                if (showSystemSearchVisibilityToggle) {
                    SettingsSection(
                        title = stringResource(Res.string.system_search_visibility_section),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        ToggleSettingsItem(
                            title = stringResource(Res.string.system_search_visibility_label),
                            description = stringResource(Res.string.system_search_visibility_description),
                            checked = isSystemSearchVisibilityEnabled,
                            onCheckedChange = onSetSystemSearchVisibilityEnabled,
                        )
                    }
                }
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
                SettingsSection(
                    title = stringResource(Res.string.location_privacy),
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.location_settings)) },
                        supportingContent = {
                            Text(
                                stringResource(Res.string.manage_location_tracking_and_privacy_preferences),
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = stringResource(Res.string.navigate_to_location_settings),
                            )
                        },
                        modifier = Modifier.clickable(onClick = onNavigateToLocationSettings),
                    )
                }

                if (isAuthenticated) {
                    PasskeysInfoSection(
                        passkeys = passkeys,
                        onCreatePasskey = onCreatePasskey,
                        onRevokePasskey = { passkey -> passkeyToDelete = passkey },
                        showCreatePasskeyAction = false,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.privacy_and_security),
                onBack = onBack,
                snackbarHostState = snackbarHostState,
            ) {
                item {
                    Text(
                        text = stringResource(Res.string.privacy_security_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }

                item {
                    SettingsSection(
                        title = stringResource(Res.string.app_security),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        ToggleSettingsItem(
                            title = stringResource(Res.string.settings_biometric_label),
                            description = stringResource(Res.string.settings_biometric_description),
                            checked = isBiometricsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    onSetBiometricsEnabled(true)
                                } else {
                                    showDisableBiometricsDialog = true
                                }
                            },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.recovery_phrase_settings_title)) },
                            supportingContent = { Text(stringResource(Res.string.recovery_phrase_settings_description)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier = Modifier.clickable(onClick = onRevealRecoveryPhrase),
                        )
                    }
                }

                if (showSystemSearchVisibilityToggle) {
                    item {
                        SettingsSection(
                            title = stringResource(Res.string.system_search_visibility_section),
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        ) {
                            ToggleSettingsItem(
                                title = stringResource(Res.string.system_search_visibility_label),
                                description = stringResource(Res.string.system_search_visibility_description),
                                checked = isSystemSearchVisibilityEnabled,
                                onCheckedChange = onSetSystemSearchVisibilityEnabled,
                            )
                        }
                    }
                }

                item {
                    SettingsSection(
                        title = stringResource(Res.string.location_privacy),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.location_settings)) },
                            supportingContent = {
                                Text(
                                    stringResource(Res.string.manage_location_tracking_and_privacy_preferences),
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = stringResource(Res.string.navigate_to_location_settings),
                                )
                            },
                            modifier = Modifier.clickable(onClick = onNavigateToLocationSettings),
                        )
                    }
                }

                if (isAuthenticated) {
                    item {
                        PasskeysInfoSection(
                            passkeys = passkeys,
                            onCreatePasskey = onCreatePasskey,
                            onRevokePasskey = { passkey -> passkeyToDelete = passkey },
                            showCreatePasskeyAction = false,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }
                }
            }
        },
    )

    if (showDisableBiometricsDialog) {
        AlertDialog(
            onDismissRequest = { showDisableBiometricsDialog = false },
            title = { Text(stringResource(Res.string.disable_biometric_lock_title)) },
            text = { Text(stringResource(Res.string.disable_biometric_lock_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetBiometricsEnabled(false)
                        showDisableBiometricsDialog = false
                    },
                ) {
                    Text(stringResource(UiRes.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableBiometricsDialog = false }) {
                    Text(stringResource(UiRes.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun RecoveryPhraseDialog(
    state: RecoveryPhraseRevealState,
    onDismiss: () -> Unit,
) {
    when (state) {
        RecoveryPhraseRevealState.Hidden -> Unit
        RecoveryPhraseRevealState.Loading -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(Res.string.recovery_phrase_settings_title)) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator()
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(UiRes.string.common_cancel))
                    }
                },
            )
        }
        RecoveryPhraseRevealState.Missing -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(Res.string.recovery_phrase_settings_title)) },
                text = { Text(stringResource(Res.string.recovery_phrase_missing)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(UiRes.string.common_confirm))
                    }
                },
            )
        }
        is RecoveryPhraseRevealState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(Res.string.recovery_phrase_settings_title)) },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(UiRes.string.common_confirm))
                    }
                },
            )
        }
        is RecoveryPhraseRevealState.Revealed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(Res.string.recovery_phrase_settings_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            text = stringResource(Res.string.recovery_phrase_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        state.words.forEachIndexed { index, word ->
                            Text("${index + 1}. $word")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(UiRes.string.common_confirm))
                    }
                },
            )
        }
    }
}

@Preview
@Composable
private fun PrivacySettingsScreenPreview() {
    PrivacySettingsContent(
        onBack = {},
        onSetBiometricsEnabled = {},
        onSetSystemSearchVisibilityEnabled = {},
        isBiometricsEnabled = true,
        isAuthenticated = true,
        isSystemSearchVisibilityEnabled = true,
        showSystemSearchVisibilityToggle = true,
        passkeys =
            listOf(
                PasskeyInfo(
                    id = "1",
                    name = "Passkey #1",
                    device = "Pixel 7",
                    createdAt = "2024-03-13",
                ),
            ),
        revocationState = PasskeyRevocationState.Idle,
        creationState = PasskeyCreationState.Idle,
    )
}

@Preview
@Composable
private fun PrivacySettingsScreenEmptyPasskeysPreview() {
    PrivacySettingsContent(
        onBack = {},
        onSetBiometricsEnabled = {},
        isBiometricsEnabled = false,
        isAuthenticated = false,
        passkeys = emptyList(),
    )
}

@Preview
@Composable
private fun PrivacySettingsScreenLoadingRevocationPreview() {
    PrivacySettingsContent(
        onBack = {},
        onSetBiometricsEnabled = {},
        isBiometricsEnabled = true,
        isAuthenticated = true,
        passkeys =
            listOf(
                PasskeyInfo(
                    id = "1",
                    name = "Passkey #1",
                    device = "Pixel 7",
                    createdAt = "2024-03-13",
                ),
            ),
        revocationState = PasskeyRevocationState.Revoking,
    )
}

@Preview
@Composable
private fun PrivacySettingsScreenLoadingCreationPreview() {
    PrivacySettingsContent(
        onBack = {},
        onSetBiometricsEnabled = {},
        isBiometricsEnabled = true,
        isAuthenticated = true,
        passkeys =
            listOf(
                PasskeyInfo(
                    id = "1",
                    name = "Passkey #1",
                    device = "Pixel 7",
                    createdAt = "2024-03-13",
                ),
            ),
        creationState = PasskeyCreationState.Creating,
    )
}
