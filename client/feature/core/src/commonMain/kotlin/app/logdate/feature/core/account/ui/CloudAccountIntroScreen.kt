@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.`continue`
import logdate.client.feature.core.generated.resources.logdate_cloud
import logdate.client.feature.core.generated.resources.not_now
import logdate.client.feature.core.generated.resources.securely_sync_your_journals_notes_and_memories_across_all_your_devices
import logdate.client.feature.core.generated.resources.skip_for_now_2
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Introduction screen for LogDate Cloud account setup.
 *
 * This screen explains the benefits of creating a cloud account and
 * provides options to continue with setup or skip (if from onboarding).
 *
 * @param isFromOnboarding Whether this flow was started from onboarding.
 * @param onContinue Callback when the user chooses to continue with setup.
 * @param onSkip Callback when the user chooses to skip (only shown if isFromOnboarding is true).
 * @param onBack Callback when the user chooses to go back.
 * @param viewModel The ViewModel for this screen.
 */
@Composable
fun CloudAccountIntroScreen(
    isFromOnboarding: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    viewModel: AccountOnboardingViewModel = koinViewModel(),
) {
    // No need to observe ViewModel state for navigation - use callbacks directly

    CloudAccountIntroContent(
        isFromOnboarding = isFromOnboarding,
        onContinue = onContinue,
        onSkip = onSkip,
        onBack = onBack,
    )
}

@Composable
fun CloudAccountIntroContent(
    isFromOnboarding: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold { paddingValues ->
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            CloudAccountIntroBody(
                isFromOnboarding = isFromOnboarding,
                onContinue = onContinue,
                onSkip = onSkip,
                onBack = onBack,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
            )
        }
    }
}

@Composable
private fun CloudAccountIntroBody(
    isFromOnboarding: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Rounded.Cloud,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = stringResource(Res.string.logdate_cloud),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.securely_sync_your_journals_notes_and_memories_across_all_your_devices),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text =
                "• No password required - uses secure passkeys\n" +
                    "• End-to-end encryption for your data\n" +
                    "• Works across all your devices\n" +
                    "• Your data remains available offline",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.`continue`))
        }

        if (isFromOnboarding) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.skip_for_now_2))
            }
        } else {
            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.not_now))
            }
        }
    }
}
