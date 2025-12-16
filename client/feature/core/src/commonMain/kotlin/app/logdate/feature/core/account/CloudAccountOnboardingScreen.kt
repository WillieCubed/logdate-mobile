package app.logdate.feature.core.account

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CloudAccountOnboardingScreen(
    viewModel: CloudAccountOnboardingViewModel,
    onAccountCreated: () -> Unit,
    onSkipOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Handle completion
    LaunchedEffect(uiState.isAccountCreated, uiState.isSignedIn, uiState.isSkipped) {
        if (uiState.isAccountCreated || uiState.isSignedIn) {
            onAccountCreated()
        } else if (uiState.isSkipped) {
            onSkipOnboarding()
        }
    }
    
    when (uiState.currentStep) {
        OnboardingStep.Welcome -> {
            CloudAccountWelcomeScreen(
                onContinue = viewModel::goToNextStep,
                onSignIn = viewModel::goToSignIn,
                onSkip = viewModel::skipOnboarding,
                modifier = modifier
            )
        }
        
        OnboardingStep.SignIn -> {
            CloudAccountSignInScreen(
                onSignIn = viewModel::signInWithPasskey,
                onAccountRecovery = { /* TODO: Implement recovery */ },
                onPrivacyPolicy = { /* TODO: Implement privacy policy */ },
                onTermsOfService = { /* TODO: Implement terms */ },
                onBack = viewModel::goToPreviousStep,
                isSigningIn = uiState.isSigningIn,
                modifier = modifier
            )
        }
        
        OnboardingStep.DisplayName -> {
            DisplayNameSetupScreen(
                displayName = uiState.displayName,
                onDisplayNameChange = viewModel::updateDisplayName,
                onContinue = viewModel::goToNextStep,
                onBack = viewModel::goToPreviousStep,
                isValid = uiState.canContinueFromDisplayName,
                modifier = modifier
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
                modifier = modifier
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
                modifier = modifier
            )
        }
        
        OnboardingStep.Complete -> {
            // This should be handled by LaunchedEffect above
        }
    }
}

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
    modifier: Modifier = Modifier
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
        modifier = modifier
    )
}