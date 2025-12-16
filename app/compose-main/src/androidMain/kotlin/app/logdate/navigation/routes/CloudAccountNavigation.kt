package app.logdate.navigation.routes

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import app.logdate.feature.core.account.ui.AccountCreationCompletionScreen
import app.logdate.feature.core.account.ui.AccountOnboardingViewModel
import app.logdate.feature.core.account.ui.AccountScreen
import app.logdate.feature.core.account.ui.CloudAccountIntroScreen
import app.logdate.feature.core.account.ui.DisplayNameSelectionScreen
import app.logdate.feature.core.account.ui.PasskeyCreationScreen
import app.logdate.feature.core.account.ui.UsernameSelectionScreen
import org.koin.compose.koinInject

/**
 * Extension function to add cloud account setup routes to an EntryProviderBuilder.
 *
 * This function defines all the screens in the cloud account setup flow and their
 * navigation connections. It follows the pattern used in other features of the app.
 *
 * @param onBack Callback for handling back navigation
 * @param onUsernameSelected Callback when username is selected
 * @param onDisplayNameSelected Callback when display name is selected
 * @param onPasskeyCreated Callback when passkey is created
 * @param onSetupCompleted Callback when the entire setup is completed
 * @param onSkip Callback when user chooses to skip (only available during onboarding)
 */
fun EntryProviderBuilder<NavKey>.cloudAccountSetup(
    onBack: () -> Unit,
    onUsernameSelected: () -> Unit,
    onDisplayNameSelected: () -> Unit,
    onPasskeyCreated: () -> Unit,
    onSetupCompleted: () -> Unit,
    onSkip: () -> Unit
) {
    // Cloud Account Intro Screen
    entry<CloudAccountIntroRoute>() { route ->
        // Display the intro screen with ViewModel
        CloudAccountIntroScreen(
            isFromOnboarding = route.isFromOnboarding,
            onContinue = onUsernameSelected,
            onSkip = onSkip,
            onBack = onBack
        )
    }
    
    // The following routes are kept for backward compatibility
    // but will be unused with the new consolidated approach
    
    // Username Selection Screen
    entry<UsernameSelectionRoute>() {
        // Display the username selection screen
        UsernameSelectionScreen(
            onContinue = onDisplayNameSelected,
            onBack = onBack
        )
    }
    
    // Display Name Selection Screen
    entry<DisplayNameSelectionRoute>() {
        // Display the display name selection screen
        DisplayNameSelectionScreen(
            onContinue = onPasskeyCreated,
            onBack = onBack
        )
    }
    
    // Passkey Creation Screen
    entry<PasskeyCreationRoute>() {
        // Display the passkey creation screen
        PasskeyCreationScreen(
            onComplete = onSetupCompleted,
            onBack = onBack
        )
    }
    
    // Account Creation Completion Screen
    entry<AccountCreationCompletionRoute>() {
        // Display the account creation completion screen
        AccountCreationCompletionScreen(
            onFinish = onSetupCompleted
        )
    }
}