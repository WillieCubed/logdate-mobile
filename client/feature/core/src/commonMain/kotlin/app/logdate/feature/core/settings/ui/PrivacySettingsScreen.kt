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
import logdate.client.feature.core.generated.resources.app_security
import logdate.client.feature.core.generated.resources.disable_biometric_lock_message
import logdate.client.feature.core.generated.resources.disable_biometric_lock_title
import logdate.client.feature.core.generated.resources.location_privacy
import logdate.client.feature.core.generated.resources.location_settings
import logdate.client.feature.core.generated.resources.manage_location_tracking_and_privacy_preferences
import logdate.client.feature.core.generated.resources.navigate_to_location_settings
import logdate.client.feature.core.generated.resources.privacy_and_security
import logdate.client.feature.core.generated.resources.privacy_security_description
import logdate.client.feature.core.generated.resources.recovery_phrase_missing
import logdate.client.feature.core.generated.resources.recovery_phrase_missing_enter_action
import logdate.client.feature.core.generated.resources.recovery_phrase_settings_description
import logdate.client.feature.core.generated.resources.recovery_phrase_settings_title
import logdate.client.feature.core.generated.resources.recovery_phrase_warning
import logdate.client.feature.core.generated.resources.settings_biometric_description
import logdate.client.feature.core.generated.resources.settings_biometric_label
import logdate.client.feature.core.generated.resources.system_search_visibility_description
import logdate.client.feature.core.generated.resources.system_search_visibility_label
import logdate.client.feature.core.generated.resources.system_search_visibility_section
import logdate.client.ui.generated.resources.common_cancel
import logdate.client.ui.generated.resources.common_confirm
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
    onNavigateToRecoveryPhrase: () -> Unit = {},
    viewModel: PrivacySettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val recoveryPhraseRevealState by viewModel.recoveryPhraseRevealState.collectAsState()

    PrivacySettingsContent(
        onBack = onBack,
        onSetBiometricsEnabled = viewModel::setBiometricEnabled,
        onSetSystemSearchVisibilityEnabled = viewModel::setSystemSearchVisibilityEnabled,
        isBiometricsEnabled = state.isBiometricsEnabled,
        isSystemSearchVisibilityEnabled = state.isSystemSearchVisibilityEnabled,
        showSystemSearchVisibilityToggle = state.showSystemSearchVisibilityToggle,
        recoveryPhraseRevealState = recoveryPhraseRevealState,
        onRevealRecoveryPhrase = viewModel::revealRecoveryPhrase,
        onHideRecoveryPhrase = viewModel::hideRecoveryPhrase,
        onNavigateToLocationSettings = onNavigateToLocationSettings,
        onNavigateToRecoveryPhrase = onNavigateToRecoveryPhrase,
    )
}

@Composable
fun PrivacySettingsContent(
    onBack: () -> Unit,
    onSetBiometricsEnabled: (enabled: Boolean) -> Unit,
    onSetSystemSearchVisibilityEnabled: (enabled: Boolean) -> Unit = {},
    isBiometricsEnabled: Boolean,
    isSystemSearchVisibilityEnabled: Boolean = false,
    showSystemSearchVisibilityToggle: Boolean = false,
    recoveryPhraseRevealState: RecoveryPhraseRevealState = RecoveryPhraseRevealState.Hidden,
    onRevealRecoveryPhrase: () -> Unit = {},
    onHideRecoveryPhrase: () -> Unit = {},
    onNavigateToLocationSettings: () -> Unit = {},
    onNavigateToRecoveryPhrase: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDisableBiometricsDialog by remember { mutableStateOf(false) }

    RecoveryPhraseDialog(
        state = recoveryPhraseRevealState,
        onDismiss = onHideRecoveryPhrase,
        onEnterPhrase = {
            onHideRecoveryPhrase()
            onNavigateToRecoveryPhrase()
        },
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
    onEnterPhrase: () -> Unit,
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
                    TextButton(onClick = onEnterPhrase) {
                        Text(stringResource(Res.string.recovery_phrase_missing_enter_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(UiRes.string.common_cancel))
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
        isSystemSearchVisibilityEnabled = true,
        showSystemSearchVisibilityToggle = true,
    )
}
