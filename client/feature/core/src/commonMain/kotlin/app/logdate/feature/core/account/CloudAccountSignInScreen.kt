@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.core.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_recovery
import logdate.client.feature.core.generated.resources.at
import logdate.client.feature.core.generated.resources.coming_soon_account_recovery_features_are_being_developed
import logdate.client.feature.core.generated.resources.double_click_to_use_a_custom_server
import logdate.client.feature.core.generated.resources.got_it
import logdate.client.feature.core.generated.resources.logdate_cloud
import logdate.client.feature.core.generated.resources.privacy_policy
import logdate.client.feature.core.generated.resources.sign_in_to_logdate_cloud
import logdate.client.feature.core.generated.resources.sign_in_with_passkey
import logdate.client.feature.core.generated.resources.signing_in
import logdate.client.feature.core.generated.resources.terms_of_service
import logdate.client.feature.core.generated.resources.text_4
import logdate.client.feature.core.generated.resources.username
import logdate.client.feature.core.generated.resources.your_username_2
import org.jetbrains.compose.resources.stringResource

@Composable
fun CloudAccountSignInScreen(
    onSignIn: (username: String, serverUrl: String) -> Unit,
    onAccountRecovery: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onTermsOfService: () -> Unit,
    onBack: () -> Unit,
    isSigningIn: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var username by remember { mutableStateOf("") }
    var serverDomain by remember { mutableStateOf("logdate.app") }
    var isServerEditable by remember { mutableStateOf(false) }
    var showRecoveryPopup by remember { mutableStateOf(false) }
    var hasTriggeredAutoPasskey by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val serverFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isServerEditable) {
        if (isServerEditable) {
            serverFocusRequester.requestFocus()
        }
    }

    // Auto-trigger passkey dialog when screen loads with a default username
    LaunchedEffect(Unit) {
        if (!hasTriggeredAutoPasskey) {
            hasTriggeredAutoPasskey = true
            // Trigger with a temporary username or empty to show universal passkey picker
            onSignIn("", serverDomain)
        }
    }

    CloudAccountSignInContent(
        username = username,
        onUsernameChange = { username = it },
        serverDomain = serverDomain,
        onServerDomainChange = { serverDomain = it },
        isServerEditable = isServerEditable,
        onServerDomainDoubleClick = { isServerEditable = true },
        onServerFocusLost = { isServerEditable = false },
        serverFocusRequester = serverFocusRequester,
        onSignIn = { onSignIn(username, serverDomain) },
        onAccountRecovery = { showRecoveryPopup = true },
        onPrivacyPolicy = onPrivacyPolicy,
        onTermsOfService = onTermsOfService,
        showRecoveryPopup = showRecoveryPopup,
        onDismissRecoveryPopup = { showRecoveryPopup = false },
        isSigningIn = isSigningIn,
        modifier = modifier,
    )
}

@Composable
fun CloudAccountSignInContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    serverDomain: String,
    onServerDomainChange: (String) -> Unit,
    isServerEditable: Boolean,
    onServerDomainDoubleClick: () -> Unit,
    onServerFocusLost: () -> Unit,
    serverFocusRequester: FocusRequester,
    onSignIn: () -> Unit,
    onAccountRecovery: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onTermsOfService: () -> Unit,
    showRecoveryPopup: Boolean,
    onDismissRecoveryPopup: () -> Unit,
    isSigningIn: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                Spacer(modifier = Modifier.height(Spacing.xxl))

                // Logo
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    border = null,
                ) {
                    Box(
                        modifier = Modifier.padding(Spacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = stringResource(Res.string.logdate_cloud),
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(Res.string.sign_in_to_logdate_cloud),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                // Sign-in form
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    UsernameWithServerField(
                        username = username,
                        onUsernameChange = onUsernameChange,
                        serverDomain = serverDomain,
                        onServerDomainChange = onServerDomainChange,
                        isServerEditable = isServerEditable,
                        onServerDomainDoubleClick = onServerDomainDoubleClick,
                        onServerFocusLost = onServerFocusLost,
                        serverFocusRequester = serverFocusRequester,
                    )

                    Button(
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSigningIn,
                    ) {
                        if (isSigningIn) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                                Text(stringResource(Res.string.signing_in))
                            }
                        } else {
                            Text(stringResource(Res.string.sign_in_with_passkey))
                        }
                    }

                    TextButton(
                        onClick = onAccountRecovery,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.account_recovery))
                    }
                }
            }

            // Footer with privacy policy and terms
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(
                        text = stringResource(Res.string.privacy_policy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { onPrivacyPolicy() },
                    )

                    Text(
                        text = stringResource(Res.string.text_4),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        text = stringResource(Res.string.terms_of_service),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { onTermsOfService() },
                    )
                }
            }
        }

        // Recovery popup
        if (showRecoveryPopup) {
            RecoveryPopup(
                onDismiss = onDismissRecoveryPopup,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun UsernameWithServerField(
    username: String,
    onUsernameChange: (String) -> Unit,
    serverDomain: String,
    onServerDomainChange: (String) -> Unit,
    isServerEditable: Boolean,
    onServerDomainDoubleClick: () -> Unit,
    onServerFocusLost: () -> Unit,
    serverFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val serverInteractionSource = remember { MutableInteractionSource() }
    val isHovered by serverInteractionSource.collectIsHoveredAsState()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(Res.string.username),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )

        Box {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(Res.string.your_username_2)) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                )

                Text(
                    text = stringResource(Res.string.at),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isServerEditable) {
                    OutlinedTextField(
                        value = serverDomain,
                        onValueChange = onServerDomainChange,
                        modifier =
                            Modifier
                                .weight(1f)
                                .focusRequester(serverFocusRequester),
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done,
                            ),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .hoverable(serverInteractionSource)
                                .clickable(
                                    interactionSource = serverInteractionSource,
                                    indication = null,
                                ) { onServerDomainDoubleClick() }
                                .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = serverDomain,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Tooltip that appears on hover
            if (isHovered && !isServerEditable) {
                Popup(
                    alignment = Alignment.TopCenter,
                    offset =
                        androidx.compose.ui.unit
                            .IntOffset(0, -8),
                    properties = PopupProperties(focusable = false),
                ) {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.inverseSurface,
                            ),
                        border = null,
                    ) {
                        Text(
                            text = stringResource(Res.string.double_click_to_use_a_custom_server),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryPopup(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .padding(Spacing.lg)
                .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = null,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.account_recovery),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = stringResource(Res.string.coming_soon_account_recovery_features_are_being_developed),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.got_it))
            }
        }
    }
}

@Preview
@Composable
private fun CloudAccountSignInScreenPreview() {
    MaterialTheme {
        Surface {
            CloudAccountSignInContent(
                username = "john-doe",
                onUsernameChange = {},
                serverDomain = "logdate.app",
                onServerDomainChange = {},
                isServerEditable = false,
                onServerDomainDoubleClick = {},
                onServerFocusLost = {},
                serverFocusRequester = FocusRequester(),
                onSignIn = {},
                onAccountRecovery = {},
                onPrivacyPolicy = {},
                onTermsOfService = {},
                showRecoveryPopup = false,
                onDismissRecoveryPopup = {},
                isSigningIn = false,
            )
        }
    }
}
