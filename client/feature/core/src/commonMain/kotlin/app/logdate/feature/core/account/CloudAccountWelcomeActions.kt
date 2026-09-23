@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.create_new_account
import logdate.client.feature.core.generated.resources.passkey_not_supported_banner
import logdate.client.feature.core.generated.resources.sign_in
import logdate.client.ui.generated.resources.common_skip
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

/** Actions on the cloud account welcome step. */
const val CLOUD_ACCOUNT_WELCOME_CREATE_TAG = "cloud_account_welcome_create"
const val CLOUD_ACCOUNT_WELCOME_SIGN_IN_TAG = "cloud_account_welcome_sign_in"
const val CLOUD_ACCOUNT_WELCOME_SKIP_TAG = "cloud_account_welcome_skip"

@Composable
internal fun CloudAccountWelcomeActions(
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    isPasskeySupported: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().testTag(CLOUD_ACCOUNT_WELCOME_CREATE_TAG),
            enabled = isPasskeySupported,
        ) {
            Text(stringResource(Res.string.create_new_account))
        }

        OutlinedButton(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth().testTag(CLOUD_ACCOUNT_WELCOME_SIGN_IN_TAG),
        ) {
            Text(stringResource(Res.string.sign_in))
        }

        TextButton(
            onClick = onSkip,
            modifier = Modifier.testTag(CLOUD_ACCOUNT_WELCOME_SKIP_TAG),
        ) {
            Text(stringResource(UiRes.string.common_skip))
        }
    }
}

@Composable
internal fun PasskeyUnsupportedBanner() {
    Text(
        text = stringResource(Res.string.passkey_not_supported_banner),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.large)
                .padding(Spacing.lg),
    )
}
