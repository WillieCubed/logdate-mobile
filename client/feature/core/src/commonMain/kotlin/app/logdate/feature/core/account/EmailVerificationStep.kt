@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.logdate.client.permissions.EmailVerificationOutcome
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.email_verification_body
import logdate.client.feature.core.generated.resources.email_verification_cancelled_body
import logdate.client.feature.core.generated.resources.email_verification_conflict_body
import logdate.client.feature.core.generated.resources.email_verification_conflict_title
import logdate.client.feature.core.generated.resources.email_verification_continue_button
import logdate.client.feature.core.generated.resources.email_verification_failed_body
import logdate.client.feature.core.generated.resources.email_verification_failed_title
import logdate.client.feature.core.generated.resources.email_verification_in_progress
import logdate.client.feature.core.generated.resources.email_verification_no_credential_body
import logdate.client.feature.core.generated.resources.email_verification_skip_button
import logdate.client.feature.core.generated.resources.email_verification_success_body
import logdate.client.feature.core.generated.resources.email_verification_success_title
import logdate.client.feature.core.generated.resources.email_verification_title
import logdate.client.feature.core.generated.resources.email_verification_verify_button
import org.jetbrains.compose.resources.stringResource

/**
 * Onboarding sub-step rendered after PasskeyCreation. Drives the Android
 * Digital Credentials flow via the view-model and surfaces every
 * [EmailVerificationOutcome] variant inline.
 */
@Composable
fun EmailVerificationStep(
    isVerifying: Boolean,
    outcome: EmailVerificationOutcome?,
    onVerifyClick: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        Icon(
            imageVector = if (outcome is EmailVerificationOutcome.Success) Icons.Filled.CheckCircle else Icons.Filled.MailOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = headlineFor(outcome),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = bodyFor(outcome),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.lg))

        when {
            isVerifying -> {
                CircularProgressIndicator()
                Text(
                    text = stringResource(Res.string.email_verification_in_progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            outcome is EmailVerificationOutcome.Success -> {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.email_verification_continue_button))
                }
            }

            else -> {
                Button(
                    onClick = onVerifyClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.email_verification_verify_button))
                }
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.email_verification_skip_button))
                }
            }
        }
    }
}

@Composable
private fun headlineFor(outcome: EmailVerificationOutcome?): String =
    when (outcome) {
        is EmailVerificationOutcome.Success -> stringResource(Res.string.email_verification_success_title)
        is EmailVerificationOutcome.Conflict -> stringResource(Res.string.email_verification_conflict_title)
        is EmailVerificationOutcome.Failed -> stringResource(Res.string.email_verification_failed_title)
        else -> stringResource(Res.string.email_verification_title)
    }

@Composable
private fun bodyFor(outcome: EmailVerificationOutcome?): String =
    when (outcome) {
        is EmailVerificationOutcome.Success -> stringResource(Res.string.email_verification_success_body, outcome.email)
        is EmailVerificationOutcome.Conflict -> stringResource(Res.string.email_verification_conflict_body)
        is EmailVerificationOutcome.Failed -> stringResource(Res.string.email_verification_failed_body, outcome.reason)
        EmailVerificationOutcome.UserCancelled -> stringResource(Res.string.email_verification_cancelled_body)
        EmailVerificationOutcome.NoCredentialAvailable -> stringResource(Res.string.email_verification_no_credential_body)
        EmailVerificationOutcome.Unsupported, null -> stringResource(Res.string.email_verification_body)
    }
