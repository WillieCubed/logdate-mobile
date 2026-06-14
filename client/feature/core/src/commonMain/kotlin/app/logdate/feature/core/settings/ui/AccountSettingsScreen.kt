@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_actions
import logdate.client.feature.core.generated.resources.account_and_sign_in
import logdate.client.feature.core.generated.resources.account_information
import logdate.client.feature.core.generated.resources.account_sign_out_action
import logdate.client.feature.core.generated.resources.account_sign_out_description
import logdate.client.feature.core.generated.resources.account_sign_out_dialog_title
import logdate.client.feature.core.generated.resources.account_sign_out_sync_warning
import logdate.client.feature.core.generated.resources.email_verification_settings_row_subtitle
import logdate.client.feature.core.generated.resources.email_verification_settings_row_unverified
import logdate.client.feature.core.generated.resources.email_verification_settings_row_verified
import logdate.client.feature.core.generated.resources.email_verification_settings_row_verified_subtitle
import logdate.client.feature.core.generated.resources.sign_out_failed
import logdate.client.feature.core.generated.resources.username_handle
import logdate.client.ui.generated.resources.common_cancel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import logdate.client.ui.generated.resources.Res as UiRes

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
    accountViewModel: AccountSettingsViewModel = koinViewModel(),
    privacyViewModel: PrivacySettingsViewModel = koinViewModel(),
    advancedViewModel: AdvancedSettingsViewModel = koinViewModel(),
) {
    val accountState by accountViewModel.state.collectAsState()
    val identityState by accountViewModel.identityState.collectAsState()
    val privacyState by privacyViewModel.state.collectAsState()
    val serverSelectionState by advancedViewModel.serverSelectionState.collectAsState()
    AccountSettingsContent(
        onBack = onBack,
        onCreatePasskey = privacyViewModel::createPasskey,
        userProfile = accountState.currentAccount.toUserProfile(),
        isEmailVerificationAvailable = accountState.isEmailVerificationAvailable,
        isVerifyingEmail = accountState.isVerifyingEmail,
        emailVerificationOutcome = accountState.emailVerificationOutcome,
        onVerifyEmailClicked = accountViewModel::onVerifyEmailClicked,
        onDismissEmailVerificationSheet = accountViewModel::dismissEmailVerificationSheet,
        passkeys = privacyState.passkeys,
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
        serverSelectionState = serverSelectionState,
        onSelectServerPreset = advancedViewModel::selectServerPreset,
        onUpdateCustomServerUrl = advancedViewModel::updateCustomServerUrl,
        onValidateAndSaveServer = advancedViewModel::validateAndSaveServer,
    )
}

@Composable
fun AccountSettingsContent(
    onBack: () -> Unit,
    onCreatePasskey: () -> Unit,
    userProfile: UserProfile,
    passkeys: List<PasskeyInfo>,
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
    isEmailVerificationAvailable: Boolean = false,
    isVerifyingEmail: Boolean = false,
    emailVerificationOutcome: app.logdate.client.permissions.EmailVerificationOutcome? = null,
    onVerifyEmailClicked: () -> Unit = {},
    onDismissEmailVerificationSheet: () -> Unit = {},
    serverSelectionState: ServerSelectionState = ServerSelectionState(),
    onSelectServerPreset: (ServerPreset) -> Unit = {},
    onUpdateCustomServerUrl: (String) -> Unit = {},
    onValidateAndSaveServer: () -> Unit = {},
) {
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

    val showEmailVerificationSheet = remember { mutableStateOf(false) }
    if (showEmailVerificationSheet.value) {
        EmailVerificationBottomSheet(
            isVerifying = isVerifyingEmail,
            outcome = emailVerificationOutcome,
            onVerifyClick = onVerifyEmailClicked,
            onDismiss = {
                showEmailVerificationSheet.value = false
                onDismissEmailVerificationSheet()
            },
        )
    }

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
                SettingsSection(
                    title = stringResource(Res.string.account_information),
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        headlineContent = {
                            Text(userProfile.name.ifEmpty { userProfile.username })
                        },
                        supportingContent = {
                            if (userProfile.username.isNotEmpty()) {
                                Text(stringResource(Res.string.username_handle, userProfile.username))
                            }
                        },
                    )

                    if (isEmailVerificationAvailable) {
                        if (userProfile.emailVerified && userProfile.email != null) {
                            ListItem(
                                leadingContent = {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        stringResource(
                                            Res.string.email_verification_settings_row_verified,
                                            userProfile.email,
                                        ),
                                    )
                                },
                                supportingContent = {
                                    Text(stringResource(Res.string.email_verification_settings_row_verified_subtitle))
                                },
                            )
                        } else {
                            ListItem(
                                leadingContent = {
                                    Icon(Icons.Default.MailOutline, contentDescription = null)
                                },
                                headlineContent = {
                                    Text(stringResource(Res.string.email_verification_settings_row_unverified))
                                },
                                supportingContent = {
                                    Text(stringResource(Res.string.email_verification_settings_row_subtitle))
                                },
                                modifier =
                                    Modifier.clickable {
                                        showEmailVerificationSheet.value = true
                                    },
                            )
                        }
                    }
                }

                ServerSelectionSection(
                    serverSelectionState = serverSelectionState,
                    onSelectPreset = onSelectServerPreset,
                    onUpdateCustomUrl = onUpdateCustomServerUrl,
                    onValidateAndSave = onValidateAndSaveServer,
                    onShowCustomServerInfo = { showCustomServerInfo.value = true },
                    modifier = Modifier.padding(horizontal = Spacing.lg),
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
                PasskeysInfoSection(
                    passkeys = passkeys,
                    onCreatePasskey = onCreatePasskey,
                    onRevokePasskey = onRevokePasskey,
                    showCreatePasskeyAction = true,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )

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

                SettingsSection(
                    title = stringResource(Res.string.account_actions),
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.account_sign_out_action)) },
                        supportingContent = {
                            Text(stringResource(Res.string.account_sign_out_description))
                        },
                        trailingContent = {
                            OutlinedButton(
                                onClick = { showSignOutDialog = true },
                            ) {
                                Text(stringResource(Res.string.account_sign_out_action))
                            }
                        },
                    )
                }
            }
        },
        singlePaneContent = {
            SettingsScaffold(
                title = stringResource(Res.string.account_and_sign_in),
                onBack = onBack,
                snackbarHostState = snackbarHostState,
            ) {
                item {
                    SettingsSection(
                        title = stringResource(Res.string.account_information),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        ListItem(
                            leadingContent = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            },
                            headlineContent = {
                                Text(userProfile.name.ifEmpty { userProfile.username })
                            },
                            supportingContent = {
                                if (userProfile.username.isNotEmpty()) {
                                    Text(stringResource(Res.string.username_handle, userProfile.username))
                                }
                            },
                        )

                        if (isEmailVerificationAvailable) {
                            if (userProfile.emailVerified && userProfile.email != null) {
                                ListItem(
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    headlineContent = {
                                        Text(
                                            stringResource(
                                                Res.string.email_verification_settings_row_verified,
                                                userProfile.email,
                                            ),
                                        )
                                    },
                                    supportingContent = {
                                        Text(stringResource(Res.string.email_verification_settings_row_verified_subtitle))
                                    },
                                )
                            } else {
                                ListItem(
                                    leadingContent = {
                                        Icon(Icons.Default.MailOutline, contentDescription = null)
                                    },
                                    headlineContent = {
                                        Text(stringResource(Res.string.email_verification_settings_row_unverified))
                                    },
                                    supportingContent = {
                                        Text(stringResource(Res.string.email_verification_settings_row_subtitle))
                                    },
                                    modifier =
                                        Modifier.clickable {
                                            showEmailVerificationSheet.value = true
                                        },
                                )
                            }
                        }
                    }
                }

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

                item {
                    PasskeysInfoSection(
                        passkeys = passkeys,
                        onCreatePasskey = onCreatePasskey,
                        onRevokePasskey = onRevokePasskey,
                        showCreatePasskeyAction = true,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }

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

                item {
                    SettingsSection(
                        title = stringResource(Res.string.account_actions),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.account_sign_out_action)) },
                            supportingContent = {
                                Text(stringResource(Res.string.account_sign_out_description))
                            },
                            trailingContent = {
                                OutlinedButton(
                                    onClick = { showSignOutDialog = true },
                                ) {
                                    Text(stringResource(Res.string.account_sign_out_action))
                                }
                            },
                        )
                    }
                }
            }
        },
    )

    if (showSignOutDialog) {
        val scope = rememberCoroutineScope()
        val signOutFailedMessage = stringResource(Res.string.sign_out_failed)
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(Res.string.account_sign_out_dialog_title)) },
            text = { Text(stringResource(Res.string.account_sign_out_sync_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSignOut { _ ->
                            scope.launch {
                                snackbarHostState.showSnackbar(signOutFailedMessage)
                            }
                        }
                        showSignOutDialog = false
                    },
                ) {
                    Text(
                        stringResource(Res.string.account_sign_out_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(UiRes.string.common_cancel))
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
