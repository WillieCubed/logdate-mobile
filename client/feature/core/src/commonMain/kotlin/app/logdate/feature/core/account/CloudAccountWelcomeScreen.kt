@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.account

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.core.settings.ui.ServerPreset
import app.logdate.feature.core.settings.ui.ServerSelectionCard
import app.logdate.feature.core.settings.ui.ServerSelectionState
import app.logdate.ui.step.StepHeroIcon
import app.logdate.ui.step.StepScaffold
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_cloud_sync_promotion_description
import logdate.client.feature.core.generated.resources.welcome_to_logdate
import org.jetbrains.compose.resources.stringResource

/** Root of the cloud account welcome step. */
const val CLOUD_ACCOUNT_WELCOME_ROOT_TAG = "cloud_account_welcome_root"

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
        // The content puts this modifier on a BoxWithConstraints wrapping both the folded and
        // standard branches, so the tag resolves to exactly one node on every device.
        modifier = modifier.testTag(CLOUD_ACCOUNT_WELCOME_ROOT_TAG),
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
    StepScaffold(
        title = stringResource(Res.string.welcome_to_logdate),
        onBack = null,
        modifier = modifier,
        supportingText = stringResource(Res.string.account_cloud_sync_promotion_description),
        hero = { StepHeroIcon(Icons.Rounded.Cloud) },
        headerAlignment = Alignment.CenterHorizontally,
        actions = {
            CloudAccountWelcomeActions(
                onContinue = onContinue,
                onSignIn = onSignIn,
                onSkip = onSkip,
                isPasskeySupported = isPasskeySupported,
            )
        },
    ) {
        if (!isPasskeySupported) {
            PasskeyUnsupportedBanner()
        }
        ServerSelectionCard(
            serverSelectionState = serverSelectionState,
            onSelectPreset = onSelectServerPreset,
            onUpdateCustomUrl = onCustomServerUrlChange,
            onShowCustomServerInfo = onShowCustomServerInfo,
        )
        CloudAccountWelcomeBenefits()
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
