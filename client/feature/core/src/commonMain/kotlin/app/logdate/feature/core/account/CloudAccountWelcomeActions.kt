package app.logdate.feature.core.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.create_new_account
import logdate.client.feature.core.generated.resources.passkey_not_supported_banner
import logdate.client.feature.core.generated.resources.sign_in
import logdate.client.ui.generated.resources.common_skip
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

@Composable
internal fun CloudAccountWelcomeActionPane(
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    isPasskeySupported: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        CloudAccountWelcomeActionCard(
            onContinue = onContinue,
            onSignIn = onSignIn,
            onSkip = onSkip,
            isPasskeySupported = isPasskeySupported,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
        )
    }
}

@Composable
internal fun CloudAccountWelcomeActionCard(
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    isPasskeySupported: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        CloudAccountWelcomeActions(
            onContinue = onContinue,
            onSignIn = onSignIn,
            onSkip = onSkip,
            isPasskeySupported = isPasskeySupported,
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@Composable
private fun CloudAccountWelcomeActions(
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    isPasskeySupported: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
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
