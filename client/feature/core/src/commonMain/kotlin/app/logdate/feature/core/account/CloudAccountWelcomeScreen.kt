@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.settings.ui.ServerPreset
import app.logdate.feature.core.settings.ui.ServerSelectionCard
import app.logdate.feature.core.settings.ui.ServerSelectionState
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_cloud_sync_promotion_description
import logdate.client.feature.core.generated.resources.create_new_account
import logdate.client.feature.core.generated.resources.passkey_not_supported_banner
import logdate.client.feature.core.generated.resources.sign_in
import logdate.client.feature.core.generated.resources.welcome_to_logdate
import logdate.client.ui.generated.resources.common_skip
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

@Composable
fun CloudAccountWelcomeScreen(
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    serverSelectionState: ServerSelectionState,
    onSelectServerPreset: (ServerPreset) -> Unit,
    onCustomServerUrlChange: (String) -> Unit,
    onShowCustomServerInfo: () -> Unit,
    isPasskeySupported: Boolean = true,
    modifier: Modifier = Modifier,
) {
    CloudAccountWelcomeContent(
        onContinue = onContinue,
        onSignIn = onSignIn,
        onSkip = onSkip,
        serverSelectionState = serverSelectionState,
        onSelectServerPreset = onSelectServerPreset,
        onCustomServerUrlChange = onCustomServerUrlChange,
        onShowCustomServerInfo = onShowCustomServerInfo,
        isPasskeySupported = isPasskeySupported,
        modifier = modifier,
    )
}

@Composable
fun CloudAccountWelcomeContent(
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    serverSelectionState: ServerSelectionState,
    onSelectServerPreset: (ServerPreset) -> Unit,
    onCustomServerUrlChange: (String) -> Unit,
    onShowCustomServerInfo: () -> Unit,
    isPasskeySupported: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(top = Spacing.xxl),
            ) {
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
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(Res.string.welcome_to_logdate),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text =
                        stringResource(
                            Res.string
                                .account_cloud_sync_promotion_description,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ServerSelectionCard(
                serverSelectionState = serverSelectionState,
                onSelectPreset = onSelectServerPreset,
                onUpdateCustomUrl = onCustomServerUrlChange,
                onShowCustomServerInfo = onShowCustomServerInfo,
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                FeatureItem(
                    icon = Icons.Default.Sync,
                    title = "Sync across devices",
                    description = "Access your journals from any device that uses the same server.",
                )
                FeatureItem(
                    icon = Icons.Default.Key,
                    title = "Secure with passkeys",
                    description = "Use your device biometrics or screen lock instead of a password.",
                )
                FeatureItem(
                    icon = Icons.Default.Cloud,
                    title = "Server-based backup",
                    description = "Your selected server can keep your journals available across devices.",
                )
                FeatureItem(
                    icon = Icons.Default.Lock,
                    title = "Privacy first",
                    description = "Server policies come from the server you choose.",
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (!isPasskeySupported) {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.passkey_not_supported_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                enabled = isPasskeySupported,
            ) {
                Text(stringResource(Res.string.create_new_account))
            }

            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.sign_in))
            }

            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(UiRes.string.common_skip))
            }
        }
    }
}

@Composable
private fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun CloudAccountWelcomeScreenPreview() {
    MaterialTheme {
        Surface {
            CloudAccountWelcomeContent(
                onContinue = {},
                onSignIn = {},
                onSkip = {},
                serverSelectionState = ServerSelectionState(),
                onSelectServerPreset = {},
                onCustomServerUrlChange = {},
                onShowCustomServerInfo = {},
            )
        }
    }
}
