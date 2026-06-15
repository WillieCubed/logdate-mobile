@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.adaptive.FoldableTabletopLayout
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_recovery
import logdate.client.feature.core.generated.resources.at
import logdate.client.feature.core.generated.resources.continue_with_google
import logdate.client.feature.core.generated.resources.privacy_policy
import logdate.client.feature.core.generated.resources.server_domain_sign_in_hint
import logdate.client.feature.core.generated.resources.server_sign_in_title
import logdate.client.feature.core.generated.resources.sign_in_with_passkey
import logdate.client.feature.core.generated.resources.signing_in
import logdate.client.feature.core.generated.resources.terms_of_service
import logdate.client.feature.core.generated.resources.text_4
import logdate.client.feature.core.generated.resources.username
import logdate.client.feature.core.generated.resources.your_username_2
import logdate.client.ui.generated.resources.common_go_back
import logdate.client.ui.generated.resources.common_try_again
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

@Composable
fun CloudAccountSignInScreen(
    onSignIn: (username: String) -> Unit,
    onAccountRecovery: () -> Unit,
    onPrivacyPolicy: (() -> Unit)?,
    onTermsOfService: (() -> Unit)?,
    onBack: () -> Unit,
    serverDisplayName: String,
    serverHandleDomain: String,
    isSigningIn: Boolean = false,
    errorMessage: String? = null,
    onClearError: () -> Unit = {},
    onSignInWithGoogle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var username by remember { mutableStateOf("") }

    CloudAccountSignInContent(
        username = username,
        onUsernameChange = { username = it },
        onSignIn = {
            onClearError()
            onSignIn(username)
        },
        onAccountRecovery = onAccountRecovery,
        onPrivacyPolicy = onPrivacyPolicy,
        onTermsOfService = onTermsOfService,
        onBack = onBack,
        serverDisplayName = serverDisplayName,
        serverHandleDomain = serverHandleDomain,
        isSigningIn = isSigningIn,
        errorMessage = errorMessage,
        onSignInWithGoogle =
            onSignInWithGoogle?.let {
                {
                    onClearError()
                    it()
                }
            },
        modifier = modifier,
    )
}

@Composable
fun CloudAccountSignInContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onAccountRecovery: () -> Unit,
    onPrivacyPolicy: (() -> Unit)?,
    onTermsOfService: (() -> Unit)?,
    onBack: () -> Unit,
    serverDisplayName: String,
    serverHandleDomain: String,
    isSigningIn: Boolean = false,
    errorMessage: String? = null,
    onSignInWithGoogle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    CloudAccountSignInAdaptiveContent(
        username = username,
        onUsernameChange = onUsernameChange,
        onSignIn = onSignIn,
        onAccountRecovery = onAccountRecovery,
        onPrivacyPolicy = onPrivacyPolicy,
        onTermsOfService = onTermsOfService,
        onBack = onBack,
        serverDisplayName = serverDisplayName,
        serverHandleDomain = serverHandleDomain,
        isSigningIn = isSigningIn,
        errorMessage = errorMessage,
        onSignInWithGoogle = onSignInWithGoogle,
        modifier = modifier,
    )
}

@Composable
private fun CloudAccountSignInAdaptiveContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onAccountRecovery: () -> Unit,
    onPrivacyPolicy: (() -> Unit)?,
    onTermsOfService: (() -> Unit)?,
    onBack: () -> Unit,
    serverDisplayName: String,
    serverHandleDomain: String,
    isSigningIn: Boolean,
    errorMessage: String?,
    onSignInWithGoogle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    FoldableTabletopLayout(
        modifier = modifier.fillMaxSize(),
        minPaneHeight = 260.dp,
        topPane = {
            CloudAccountSignInTopPane(
                username = username,
                onUsernameChange = onUsernameChange,
                onBack = onBack,
                serverDisplayName = serverDisplayName,
                serverHandleDomain = serverHandleDomain,
                errorMessage = errorMessage,
                modifier = Modifier.fillMaxSize(),
            )
        },
        bottomPane = {
            CloudAccountSignInActionPane(
                onSignIn = onSignIn,
                onAccountRecovery = onAccountRecovery,
                onPrivacyPolicy = onPrivacyPolicy,
                onTermsOfService = onTermsOfService,
                isSigningIn = isSigningIn,
                errorMessage = errorMessage,
                onSignInWithGoogle = onSignInWithGoogle,
                modifier = Modifier.fillMaxSize(),
            )
        },
        standardContent = {
            FoldableBookLayout(
                modifier = Modifier.fillMaxSize(),
                minPaneWidth = 320.dp,
                startPane = {
                    CloudAccountSignInTopPane(
                        username = username,
                        onUsernameChange = onUsernameChange,
                        onBack = onBack,
                        serverDisplayName = serverDisplayName,
                        serverHandleDomain = serverHandleDomain,
                        errorMessage = errorMessage,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                endPane = {
                    CloudAccountSignInActionPane(
                        onSignIn = onSignIn,
                        onAccountRecovery = onAccountRecovery,
                        onPrivacyPolicy = onPrivacyPolicy,
                        onTermsOfService = onTermsOfService,
                        isSigningIn = isSigningIn,
                        errorMessage = errorMessage,
                        onSignInWithGoogle = onSignInWithGoogle,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                standardContent = {
                    CloudAccountSignInCompactContent(
                        username = username,
                        onUsernameChange = onUsernameChange,
                        onSignIn = onSignIn,
                        onAccountRecovery = onAccountRecovery,
                        onPrivacyPolicy = onPrivacyPolicy,
                        onTermsOfService = onTermsOfService,
                        onBack = onBack,
                        serverDisplayName = serverDisplayName,
                        serverHandleDomain = serverHandleDomain,
                        isSigningIn = isSigningIn,
                        errorMessage = errorMessage,
                        onSignInWithGoogle = onSignInWithGoogle,
                    )
                },
            )
        },
    )
}

@Composable
private fun CloudAccountSignInTopPane(
    username: String,
    onUsernameChange: (String) -> Unit,
    onBack: () -> Unit,
    serverDisplayName: String,
    serverHandleDomain: String,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(Spacing.lg),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            CloudAccountSignInHeader(
                onBack = onBack,
                serverDisplayName = serverDisplayName,
                serverHandleDomain = serverHandleDomain,
                errorMessage = errorMessage,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.username)) },
                placeholder = { Text(stringResource(Res.string.your_username_2)) },
                prefix = { Text(stringResource(Res.string.at)) },
                suffix = { Text("@$serverHandleDomain") },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
            )
        }
    }
}

@Composable
private fun CloudAccountSignInActionPane(
    onSignIn: () -> Unit,
    onAccountRecovery: () -> Unit,
    onPrivacyPolicy: (() -> Unit)?,
    onTermsOfService: (() -> Unit)?,
    isSigningIn: Boolean,
    errorMessage: String?,
    onSignInWithGoogle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
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
            } else if (errorMessage != null) {
                Text(stringResource(UiRes.string.common_try_again))
            } else {
                Text(stringResource(Res.string.sign_in_with_passkey))
            }
        }

        if (onSignInWithGoogle != null) {
            OutlinedButton(
                onClick = onSignInWithGoogle,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSigningIn,
            ) {
                Text(stringResource(Res.string.continue_with_google))
            }
        }

        TextButton(
            onClick = onAccountRecovery,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.account_recovery))
        }

        if (onPrivacyPolicy != null || onTermsOfService != null) {
            LegalLinksRow(
                onPrivacyPolicy = onPrivacyPolicy,
                onTermsOfService = onTermsOfService,
            )
        }
    }
}

@Composable
private fun CloudAccountSignInHeader(
    onBack: () -> Unit,
    serverDisplayName: String,
    serverHandleDomain: String,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(UiRes.string.common_go_back),
                )
            }
        }

        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
        ) {
            Box(
                modifier = Modifier.padding(Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.server_sign_in_title, serverDisplayName),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.server_domain_sign_in_hint, serverHandleDomain),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        ServerIdentityCard(
            serverDisplayName = serverDisplayName,
            serverHandleDomain = serverHandleDomain,
        )

        errorMessage?.let { message ->
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun CloudAccountSignInCompactContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onAccountRecovery: () -> Unit,
    onPrivacyPolicy: (() -> Unit)?,
    onTermsOfService: (() -> Unit)?,
    onBack: () -> Unit,
    serverDisplayName: String,
    serverHandleDomain: String,
    isSigningIn: Boolean,
    errorMessage: String?,
    onSignInWithGoogle: (() -> Unit)?,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.lg),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                CloudAccountSignInHeader(
                    onBack = onBack,
                    serverDisplayName = serverDisplayName,
                    serverHandleDomain = serverHandleDomain,
                    errorMessage = errorMessage,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.username)) },
                        placeholder = { Text(stringResource(Res.string.your_username_2)) },
                        prefix = { Text(stringResource(Res.string.at)) },
                        suffix = { Text("@$serverHandleDomain") },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done,
                            ),
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (onPrivacyPolicy != null || onTermsOfService != null) {
                    LegalLinksRow(
                        onPrivacyPolicy = onPrivacyPolicy,
                        onTermsOfService = onTermsOfService,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerIdentityCard(
    serverDisplayName: String,
    serverHandleDomain: String,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = serverDisplayName,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = serverHandleDomain,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegalLinksRow(
    onPrivacyPolicy: (() -> Unit)?,
    onTermsOfService: (() -> Unit)?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (onPrivacyPolicy != null) {
            Text(
                text = stringResource(Res.string.privacy_policy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onPrivacyPolicy),
            )
        }
        if (onPrivacyPolicy != null && onTermsOfService != null) {
            Text(
                text = stringResource(Res.string.text_4),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onTermsOfService != null) {
            Text(
                text = stringResource(Res.string.terms_of_service),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onTermsOfService),
            )
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
                onSignIn = {},
                onAccountRecovery = {},
                onPrivacyPolicy = {},
                onTermsOfService = {},
                onBack = {},
                serverDisplayName = "LogDate Cloud",
                serverHandleDomain = "logdate.app",
            )
        }
    }
}
