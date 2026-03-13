@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_actions
import logdate.client.feature.core.generated.resources.account_and_sign_in
import logdate.client.feature.core.generated.resources.account_information
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.cancel
import logdate.client.feature.core.generated.resources.create_account
import logdate.client.feature.core.generated.resources.not_signed_in_to_logdate_cloud
import logdate.client.feature.core.generated.resources.sign_in
import logdate.client.feature.core.generated.resources.sign_in_to_logdate_cloud_settings_summary
import logdate.client.feature.core.generated.resources.sign_out
import logdate.client.feature.core.generated.resources.sign_out_2
import logdate.client.feature.core.generated.resources.sign_out_failed
import logdate.client.feature.core.generated.resources.sign_out_of_your_logdate_cloud_account_on_this_device
import logdate.client.feature.core.generated.resources.username_handle
import logdate.client.feature.core.generated.resources.youll_need_to_sign_in_again_to_sync_data_on_this_device
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Account and sign-in settings screen.
 *
 * Focused on cloud account management: sign-in, server configuration,
 * passkeys, AT Protocol identity, and sign-out.
 * Profile editing (name, birthday) has moved to ProfileScreen.
 */
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit,
    onNavigateToSignIn: () -> Unit = {},
    accountViewModel: AccountSettingsViewModel = koinViewModel(),
    privacyViewModel: PrivacySettingsViewModel = koinViewModel(),
    advancedViewModel: AdvancedSettingsViewModel = koinViewModel(),
) {
    val accountState by accountViewModel.state.collectAsState()
    val identityState by accountViewModel.identityState.collectAsState()
    val privacyState by privacyViewModel.state.collectAsState()
    val serverSelectionState by advancedViewModel.serverSelectionState.collectAsState()
    val isAuthenticated = accountState.isAuthenticated
    val onCreatePasskey =
        if (isAuthenticated) {
            privacyViewModel::createPasskey
        } else {
            onNavigateToCloudAccountCreation
        }

    AccountSettingsContent(
        onBack = onBack,
        onCreatePasskey = onCreatePasskey,
        userProfile = accountState.currentAccount.toUserProfile(),
        passkeys = privacyState.passkeys,
        isAuthenticated = isAuthenticated,
        onRevokePasskey = { passkey -> privacyViewModel.revokePasskey(passkey.id) },
        onSignOut = { onError -> accountViewModel.signOut(onError) },
        identityState = identityState,
        onRefreshIdentity = accountViewModel::refreshIdentityState,
        onExportSigningKey = accountViewModel::exportSigningKey,
        onRotateSigningKey = accountViewModel::rotateSigningKey,
        onImportSigningKey = accountViewModel::importSigningKey,
        onImportSigningKeyWithRecovery = accountViewModel::importSigningKeyWithRecovery,
        onDerivePlcRecoveryKey = accountViewModel::derivePlcRecoveryKey,
        onRegisterPlcRecoveryKey = accountViewModel::registerPlcRecoveryKey,
        onRegisterDerivedPlcRecoveryKey = accountViewModel::registerDerivedPlcRecoveryKey,
        onClearIdentityActionState = accountViewModel::clearIdentityActionState,
        onClearExportedKeyJson = accountViewModel::clearExportedKeyJson,
        onClearDerivedRecoveryDidKey = accountViewModel::clearDerivedRecoveryDidKey,
        onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
        onNavigateToSignIn = onNavigateToSignIn,
        serverSelectionState = serverSelectionState,
        onSelectServerPreset = advancedViewModel::selectServerPreset,
        onUpdateCustomServerUrl = advancedViewModel::updateCustomServerUrl,
        onValidateAndSaveServer = advancedViewModel::validateAndSaveServer,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsContent(
    onBack: () -> Unit,
    onCreatePasskey: () -> Unit,
    userProfile: UserProfile,
    passkeys: List<PasskeyInfo>,
    isAuthenticated: Boolean,
    onRevokePasskey: (PasskeyInfo) -> Unit,
    onSignOut: (onError: (String) -> Unit) -> Unit,
    identityState: AccountIdentityState,
    onRefreshIdentity: () -> Unit,
    onExportSigningKey: (String) -> Unit,
    onRotateSigningKey: (String) -> Unit,
    onImportSigningKey: (String, String) -> Unit,
    onImportSigningKeyWithRecovery: (String, String, String) -> Unit,
    onDerivePlcRecoveryKey: (String) -> Unit,
    onRegisterPlcRecoveryKey: (String) -> Unit,
    onRegisterDerivedPlcRecoveryKey: () -> Unit,
    onClearIdentityActionState: () -> Unit,
    onClearExportedKeyJson: () -> Unit,
    onClearDerivedRecoveryDidKey: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    serverSelectionState: ServerSelectionState = ServerSelectionState(),
    onSelectServerPreset: (ServerPreset) -> Unit = {},
    onUpdateCustomServerUrl: (String) -> Unit = {},
    onValidateAndSaveServer: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    var showSignOutDialog by remember { mutableStateOf(false) }
    val showCustomServerInfo = remember { mutableStateOf(false) }

    if (showCustomServerInfo.value) {
        CustomServerInfoBottomSheet(
            onDismiss = { showCustomServerInfo.value = false },
            onUseCustomServer = {
                onSelectServerPreset(ServerPreset.CUSTOM)
                showCustomServerInfo.value = false
            },
        )
    }

    Scaffold(
        modifier =
            Modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.account_and_sign_in)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                if (!isAuthenticated) {
                    // Sign-in CTA
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = stringResource(Res.string.account_information),
                                style = MaterialTheme.typography.titleMedium,
                            )

                            MaterialContainer {
                                Column(
                                    modifier = Modifier.padding(Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.not_signed_in_to_logdate_cloud),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = stringResource(Res.string.sign_in_to_logdate_cloud_settings_summary),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Button(
                                            onClick = onNavigateToCloudAccountCreation,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(stringResource(Res.string.create_account))
                                        }
                                        OutlinedButton(
                                            onClick = onNavigateToSignIn,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(stringResource(Res.string.sign_in))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Account info summary
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = stringResource(Res.string.account_information),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            MaterialContainer {
                                Column(
                                    modifier = Modifier.padding(Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                                ) {
                                    Text(
                                        text = userProfile.name.ifEmpty { userProfile.username },
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    if (userProfile.username.isNotEmpty()) {
                                        Text(
                                            text = stringResource(Res.string.username_handle, userProfile.username),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Server configuration section
                item {
                    ServerSelectionSection(
                        serverSelectionState = serverSelectionState,
                        onSelectPreset = onSelectServerPreset,
                        onUpdateCustomUrl = onUpdateCustomServerUrl,
                        onValidateAndSave = onValidateAndSaveServer,
                        onShowCustomServerInfo = { showCustomServerInfo.value = true },
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }

                // Passkeys section
                item {
                    PasskeysInfoSection(
                        passkeys = passkeys,
                        onCreatePasskey = onCreatePasskey,
                        onRevokePasskey = onRevokePasskey,
                        showCreatePasskeyAction = !isAuthenticated,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }

                // AT Protocol identity (authenticated only)
                if (isAuthenticated) {
                    item {
                        AtprotoIdentitySection(
                            identityState = identityState,
                            onRefresh = onRefreshIdentity,
                            onExportSigningKey = onExportSigningKey,
                            onRotateSigningKey = onRotateSigningKey,
                            onImportSigningKey = onImportSigningKey,
                            onImportSigningKeyWithRecovery = onImportSigningKeyWithRecovery,
                            onDerivePlcRecoveryKey = onDerivePlcRecoveryKey,
                            onRegisterPlcRecoveryKey = onRegisterPlcRecoveryKey,
                            onRegisterDerivedPlcRecoveryKey = onRegisterDerivedPlcRecoveryKey,
                            onClearIdentityActionState = onClearIdentityActionState,
                            onClearExportedKeyJson = onClearExportedKeyJson,
                            onClearDerivedRecoveryDidKey = onClearDerivedRecoveryDidKey,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }
                }

                // Account actions (sign out)
                if (isAuthenticated) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = stringResource(Res.string.account_actions),
                                style = MaterialTheme.typography.titleMedium,
                            )

                            MaterialContainer {
                                SurfaceItem {
                                    androidx.compose.material3.ListItem(
                                        headlineContent = { Text(stringResource(Res.string.sign_out)) },
                                        supportingContent = {
                                            Text(stringResource(Res.string.sign_out_of_your_logdate_cloud_account_on_this_device))
                                        },
                                        trailingContent = {
                                            Button(
                                                onClick = { showSignOutDialog = true },
                                            ) {
                                                Text(stringResource(Res.string.sign_out))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        val scope = rememberCoroutineScope()
        val signOutFailedMessage = stringResource(Res.string.sign_out_failed)
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(Res.string.sign_out_2)) },
            text = { Text(stringResource(Res.string.youll_need_to_sign_in_again_to_sync_data_on_this_device)) },
            confirmButton = {
                Button(
                    onClick = {
                        onSignOut { _ ->
                            scope.launch {
                                snackbarHostState.showSnackbar(signOutFailedMessage)
                            }
                        }
                        showSignOutDialog = false
                    },
                ) {
                    Text(stringResource(Res.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Preview
@Composable
private fun AccountSettingsScreenPreview() {
    AccountSettingsContent(
        onBack = {},
        onCreatePasskey = {},
        userProfile =
            UserProfile(
                name = "John Doe",
                username = "johndoe",
                isAuthenticated = true,
            ),
        passkeys = emptyList(),
        isAuthenticated = true,
        onRevokePasskey = {},
        onSignOut = { _ -> },
        identityState = AccountIdentityState(),
        onRefreshIdentity = {},
        onExportSigningKey = {},
        onRotateSigningKey = {},
        onImportSigningKey = { _, _ -> },
        onImportSigningKeyWithRecovery = { _, _, _ -> },
        onDerivePlcRecoveryKey = {},
        onRegisterPlcRecoveryKey = {},
        onRegisterDerivedPlcRecoveryKey = {},
        onClearIdentityActionState = {},
        onClearExportedKeyJson = {},
        onClearDerivedRecoveryDidKey = {},
    )
}
