@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.about_passkeys
import logdate.client.feature.core.generated.resources.account_confirm_title
import logdate.client.feature.core.generated.resources.account_create_cta
import logdate.client.feature.core.generated.resources.account_handle_change
import logdate.client.feature.core.generated.resources.account_passkey_headline
import logdate.client.feature.core.generated.resources.account_passkey_learn_more
import logdate.client.feature.core.generated.resources.account_passkey_not_supported_description
import logdate.client.feature.core.generated.resources.account_passkey_point_device_auth
import logdate.client.feature.core.generated.resources.account_passkey_point_multi_device
import logdate.client.feature.core.generated.resources.account_passkey_point_no_passwords
import logdate.client.feature.core.generated.resources.account_passkey_point_phish_resistant
import logdate.client.feature.core.generated.resources.account_passkey_subhead
import logdate.client.feature.core.generated.resources.account_step_progress
import logdate.client.feature.core.generated.resources.account_will_sign_in_to
import logdate.client.feature.core.generated.resources.creating_account
import logdate.client.feature.core.generated.resources.passkeys_not_supported
import logdate.client.ui.generated.resources.common_dismiss
import logdate.client.ui.generated.resources.common_go_back
import logdate.client.ui.generated.resources.common_try_again
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

/**
 * Final step of LogDate Cloud account setup: confirm who the account will belong to, then create it.
 *
 * This screen is deliberately a *confirmation*, not a form. Name and bio are captured during app
 * onboarding, so re-collecting them here produced empty fields on a screen labelled "review your
 * details". The identity is the subject of the screen; the passkey explainer is collapsed because a
 * user who has reached the last step has already opted in.
 */
@Composable
fun PasskeyAccountCreationFinalContent(
    displayName: String,
    username: String,
    onCreateAccount: () -> Unit,
    onBack: () -> Unit,
    isCreatingAccount: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    isPasskeySupported: Boolean,
    handleDomain: String,
    serverDisplayName: String,
    stepNumber: Int,
    stepCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg),
    ) {
        StepHeader(
            stepNumber = stepNumber,
            stepCount = stepCount,
            onBack = onBack,
            enabled = !isCreatingAccount,
        )

        // Scrollable body; the call to action stays pinned so it is reachable without scrolling
        // on short screens and does not float mid-screen on tall ones.
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(Spacing.lg))

            Text(
                text = stringResource(Res.string.account_confirm_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.xl))

            AccountIdentity(
                displayName = displayName,
                username = username,
                handleDomain = handleDomain,
                onChange = onBack,
                changeEnabled = !isCreatingAccount,
            )

            Spacer(Modifier.height(Spacing.xl))

            PasskeyExplainer()

            if (!isPasskeySupported) {
                Spacer(Modifier.height(Spacing.lg))
                NoticeCard(
                    icon = Icons.Default.Warning,
                    title = stringResource(Res.string.passkeys_not_supported),
                    body = stringResource(Res.string.account_passkey_not_supported_description),
                )
            }

            errorMessage?.let { error ->
                Spacer(Modifier.height(Spacing.lg))
                ErrorBanner(message = error, onDismiss = onClearError)
            }

            Spacer(Modifier.height(Spacing.xl))
        }

        Button(
            onClick = onCreateAccount,
            enabled = !isCreatingAccount && isPasskeySupported,
            modifier = Modifier.fillMaxWidth(),
        ) {
            when {
                isCreatingAccount -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(Res.string.creating_account))
                }

                errorMessage != null -> Text(stringResource(UiRes.string.common_try_again))

                else -> Text(stringResource(Res.string.account_create_cta))
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = stringResource(Res.string.account_will_sign_in_to, serverDisplayName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun StepHeader(
    stepNumber: Int,
    stepCount: Int,
    onBack: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = enabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(UiRes.string.common_go_back),
            )
        }

        LinearProgressIndicator(
            progress = { if (stepCount <= 0) 1f else stepNumber.toFloat() / stepCount },
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md),
        )

        Text(
            text = stringResource(Res.string.account_step_progress, stepNumber, stepCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The account as the user will see it elsewhere: a monogram, their name, and one canonical handle.
 *
 * The handle is rendered as a single `@user@domain` token rather than a sentence in a value slot,
 * which is what produced the doubled `@@domain` when the username was still blank.
 */
@Composable
private fun AccountIdentity(
    displayName: String,
    username: String,
    handleDomain: String,
    onChange: () -> Unit,
    changeEnabled: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    displayName
                        .trim()
                        .firstOrNull()
                        ?.uppercase()
                        .orEmpty(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.sm))

        // Only render a handle once there is a username to put in it; interpolating a blank
        // one produces the doubled "@@domain" this screen was rebuilt to stop showing.
        if (username.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = "@$username@$handleDomain",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }

        TextButton(onClick = onChange, enabled = changeEnabled) {
            Text(stringResource(Res.string.account_handle_change))
        }
    }
}

/**
 * One line of reassurance, with the detail behind a disclosure so the last step stays scannable.
 */
@Composable
private fun PasskeyExplainer() {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.xs).size(20.dp),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.account_passkey_headline),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(Res.string.account_passkey_subhead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        TextButton(onClick = { expanded = !expanded }) {
            Text(
                text =
                    if (expanded) {
                        stringResource(Res.string.about_passkeys)
                    } else {
                        stringResource(Res.string.account_passkey_learn_more)
                    },
            )
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = Spacing.xl, bottom = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PasskeyPoint(stringResource(Res.string.account_passkey_point_no_passwords))
                PasskeyPoint(stringResource(Res.string.account_passkey_point_device_auth))
                PasskeyPoint(stringResource(Res.string.account_passkey_point_phish_resistant))
                PasskeyPoint(stringResource(Res.string.account_passkey_point_multi_device))
            }
        }
    }
}

@Composable
private fun PasskeyPoint(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.xs).size(16.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoticeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(UiRes.string.common_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PasskeyAccountCreationFinalScreenPreview() {
    MaterialTheme {
        Surface {
            PasskeyAccountCreationFinalContent(
                displayName = "Alex Johnson",
                username = "alex_j",
                onCreateAccount = {},
                onBack = {},
                isCreatingAccount = false,
                errorMessage = null,
                onClearError = {},
                isPasskeySupported = true,
                handleDomain = "logdate.app",
                serverDisplayName = "LogDate Cloud",
                stepNumber = 2,
                stepCount = 2,
            )
        }
    }
}

@Preview
@Composable
private fun PasskeyAccountCreationFinalScreenErrorPreview() {
    MaterialTheme {
        Surface {
            PasskeyAccountCreationFinalContent(
                displayName = "Alex Johnson",
                username = "alex_j",
                onCreateAccount = {},
                onBack = {},
                isCreatingAccount = false,
                errorMessage = "Too many attempts. Please wait a moment before trying again.",
                onClearError = {},
                isPasskeySupported = true,
                handleDomain = "logdate.app",
                serverDisplayName = "LogDate Cloud",
                stepNumber = 3,
                stepCount = 3,
            )
        }
    }
}
