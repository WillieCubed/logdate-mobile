@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.client.permissions.EmailVerificationOutcome
import app.logdate.feature.core.account.EmailVerificationStep
import app.logdate.ui.platform.PlatformSheet
import app.logdate.ui.theme.Spacing

/**
 * Settings entry point for the Android Digital Credentials email-verification
 * flow. Hosts the same [EmailVerificationStep] composable used in onboarding
 * (Commit 5) inside a modal bottom sheet so the user can verify without
 * leaving the Settings screen.
 *
 * After [EmailVerificationOutcome.Success] the host should call
 * `getCurrentAccountUseCase(RefreshAccountInfo)` and dismiss the sheet — the
 * verified email then renders inline in the Settings row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailVerificationBottomSheet(
    isVerifying: Boolean,
    outcome: EmailVerificationOutcome?,
    onVerifyClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    PlatformSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            EmailVerificationStep(
                isVerifying = isVerifying,
                outcome = outcome,
                onVerifyClick = onVerifyClick,
                onSkip = onDismiss,
                onContinue = onDismiss,
            )
        }
    }
}
