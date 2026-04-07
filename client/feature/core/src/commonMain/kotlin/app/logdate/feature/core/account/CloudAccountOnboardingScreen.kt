@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.feature.core.settings.ui.CustomServerInfoBottomSheet
import app.logdate.feature.core.settings.ui.ServerPreset
import app.logdate.shared.model.ServerDescriptor
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.atproto_recovery_guidance_body
import logdate.client.feature.core.generated.resources.atproto_recovery_guidance_title
import logdate.client.ui.generated.resources.common_dismiss
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

private const val FALLBACK_HANDLE_DOMAIN = "logdate.app"
private const val FALLBACK_PRIVACY_POLICY_URL = "https://logdate.app/privacy"
private const val FALLBACK_TERMS_OF_SERVICE_URL = "https://logdate.app/terms"
private const val CUSTOM_SERVER_FALLBACK_NAME = "Custom server"

/**
 * Cloud account onboarding screen reusable from both onboarding and settings.
 *
 * By default the flow starts at [OnboardingStep.Welcome]. Call
 * [CloudAccountOnboardingViewModel.setInitialStep] before this composable
 * renders to skip the welcome pitch (e.g. when entering from settings where
 * the user has already seen a promotional screen).
 *
 * **Side-effects observed via [LaunchedEffect]:**
 * - [CloudAccountOnboardingUiState.isAccountCreated] / [CloudAccountOnboardingUiState.isSignedIn] → calls [onAccountCreated]
 * - [CloudAccountOnboardingUiState.isSkipped] → calls [onSkipOnboarding]
 * - [CloudAccountOnboardingUiState.isExitRequested] → calls [onBack]
 *
 * @param onBack Called when the user navigates back past the entry step.
 */
@Composable
fun CloudAccountOnboardingScreen(
    viewModel: CloudAccountOnboardingViewModel,
    onAccountCreated: () -> Unit,
    onSkipOnboarding: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val showCustomServerInfo = remember { mutableStateOf(false) }
    val showRecoveryInfo = remember { mutableStateOf(false) }
    val serverPresentation =
        remember(uiState.serverSelectionState) {
            uiState.serverSelectionState.toPresentation()
        }

    if (showCustomServerInfo.value) {
        CustomServerInfoBottomSheet(
            onDismiss = { showCustomServerInfo.value = false },
            onUseCustomServer = {
                viewModel.selectServerPreset(ServerPreset.CUSTOM)
                showCustomServerInfo.value = false
            },
        )
    }

    if (showRecoveryInfo.value) {
        AlertDialog(
            onDismissRequest = { showRecoveryInfo.value = false },
            title = { Text(stringResource(Res.string.atproto_recovery_guidance_title)) },
            text = { Text(stringResource(Res.string.atproto_recovery_guidance_body)) },
            confirmButton = {
                TextButton(onClick = { showRecoveryInfo.value = false }) {
                    Text(stringResource(UiRes.string.common_dismiss))
                }
            },
        )
    }

    // Handle completion and exit
    LaunchedEffect(uiState.isAccountCreated, uiState.isSignedIn, uiState.isSkipped, uiState.isExitRequested) {
        if (uiState.isAccountCreated || uiState.isSignedIn) {
            onAccountCreated()
        } else if (uiState.isSkipped) {
            onSkipOnboarding()
        } else if (uiState.isExitRequested) {
            onBack()
        }
    }

    when (uiState.currentStep) {
        OnboardingStep.Welcome -> {
            CloudAccountWelcomeScreen(
                onContinue = viewModel::goToNextStep,
                onSignIn = viewModel::goToSignIn,
                onSkip = viewModel::skipOnboarding,
                serverSelectionState = uiState.serverSelectionState,
                onSelectServerPreset = viewModel::selectServerPreset,
                onCustomServerUrlChange = viewModel::updateCustomServerUrl,
                onShowCustomServerInfo = { showCustomServerInfo.value = true },
                isPasskeySupported = uiState.isPasskeySupported,
                modifier = modifier,
            )
        }

        OnboardingStep.SignIn -> {
            CloudAccountSignInScreen(
                onSignIn = viewModel::signInWithPasskey,
                onAccountRecovery = { showRecoveryInfo.value = true },
                onPrivacyPolicy = serverPresentation.privacyPolicyUrl?.let { { uriHandler.openUri(it) } },
                onTermsOfService = serverPresentation.termsOfServiceUrl?.let { { uriHandler.openUri(it) } },
                onBack = viewModel::goToPreviousStep,
                isSigningIn = uiState.isSigningIn,
                errorMessage = uiState.errorMessage,
                onClearError = viewModel::clearError,
                serverDisplayName = serverPresentation.displayName,
                serverHandleDomain = serverPresentation.handleDomain,
                modifier = modifier,
            )
        }

        OnboardingStep.DisplayName -> {
            DisplayNameSetupScreen(
                displayName = uiState.displayName,
                onDisplayNameChange = viewModel::updateDisplayName,
                onContinue = viewModel::goToNextStep,
                onBack = viewModel::goToPreviousStep,
                isValid = uiState.canContinueFromDisplayName,
                modifier = modifier,
            )
        }

        OnboardingStep.Username -> {
            UsernameSetupScreen(
                username = uiState.username,
                onUsernameChange = viewModel::updateUsername,
                onContinue = viewModel::goToNextStep,
                onBack = viewModel::goToPreviousStep,
                usernameAvailability = uiState.usernameAvailability,
                isValid = uiState.canContinueFromUsername,
                handleDomain = serverPresentation.handleDomain,
                modifier = modifier,
            )
        }

        OnboardingStep.PasskeyCreation -> {
            PasskeyAccountCreationFinalScreen(
                displayName = uiState.displayName,
                username = uiState.username,
                bio = uiState.bio,
                onBioChange = viewModel::updateBio,
                onCreateAccount = viewModel::createAccount,
                onBack = viewModel::goToPreviousStep,
                isCreatingAccount = uiState.isCreatingAccount,
                errorMessage = uiState.errorMessage,
                onClearError = viewModel::clearError,
                isPasskeySupported = uiState.isPasskeySupported,
                handleDomain = serverPresentation.handleDomain,
                serverDisplayName = serverPresentation.displayName,
                modifier = modifier,
            )
        }

        OnboardingStep.Complete -> {
            // This should be handled by LaunchedEffect above
        }
    }
}

private data class ServerPresentation(
    val displayName: String,
    val handleDomain: String,
    val privacyPolicyUrl: String?,
    val termsOfServiceUrl: String?,
)

private fun app.logdate.feature.core.settings.ui.ServerSelectionState.toPresentation(): ServerPresentation {
    val descriptor = activeServerDescriptor
    val isProduction = selectedPreset == ServerPreset.PRODUCTION
    return ServerPresentation(
        displayName = descriptor.displayNameOrFallback(isProduction),
        handleDomain = descriptor?.handleDomain ?: if (isProduction) FALLBACK_HANDLE_DOMAIN else "your-server.example.com",
        privacyPolicyUrl = descriptor?.privacyPolicyUrl ?: if (isProduction) FALLBACK_PRIVACY_POLICY_URL else null,
        termsOfServiceUrl = descriptor?.termsOfServiceUrl ?: if (isProduction) FALLBACK_TERMS_OF_SERVICE_URL else null,
    )
}

private fun ServerDescriptor?.displayNameOrFallback(isProduction: Boolean): String =
    this?.displayName ?: if (isProduction) "LogDate Cloud" else CUSTOM_SERVER_FALLBACK_NAME

@Composable
private fun PasskeyAccountCreationFinalScreen(
    displayName: String,
    username: String,
    bio: String,
    onBioChange: (String) -> Unit,
    onCreateAccount: () -> Unit,
    onBack: () -> Unit,
    isCreatingAccount: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    isPasskeySupported: Boolean,
    handleDomain: String,
    serverDisplayName: String,
    modifier: Modifier = Modifier,
) {
    // Reuse the existing final creation screen but adapt it for the flow
    PasskeyAccountCreationFinalContent(
        displayName = displayName,
        username = username,
        bio = bio,
        onBioChange = onBioChange,
        onCreateAccount = onCreateAccount,
        onBack = onBack,
        isCreatingAccount = isCreatingAccount,
        errorMessage = errorMessage,
        onClearError = onClearError,
        isPasskeySupported = isPasskeySupported,
        handleDomain = handleDomain,
        serverDisplayName = serverDisplayName,
        modifier = modifier,
    )
}
