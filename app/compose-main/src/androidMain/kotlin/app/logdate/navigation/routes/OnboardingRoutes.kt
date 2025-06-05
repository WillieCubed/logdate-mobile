package app.logdate.navigation

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import app.logdate.feature.onboarding.ui.CloudAccountSetupScreen
import app.logdate.feature.onboarding.ui.MemoriesImportInfoScreen
import app.logdate.feature.onboarding.ui.OnboardingCompletionScreen
import app.logdate.feature.onboarding.ui.OnboardingStartScreen
import app.logdate.feature.onboarding.ui.WelcomeBackScreen

fun MainAppNavigator.startOnboarding() {
    // Use safelyClearBackstack to ensure the backstack is never empty
    safelyClearBackstack(OnboardingStart)
}

/**
 * Provides the navigation routes for the onboarding flow.
 *
 * @param onBack Callback to handle back navigation
 * @param onStartOnboarding Callback to start the onboarding process
 * @param onContinueToEntry Callback to continue to the entry screen
 * @param onImportCompleted Callback triggered when import is completed
 * @param onWelcomeBack Callback triggered when the user is welcomed back
 * @param onComplete Callback triggered when onboarding is complete
 */
fun EntryProviderBuilder<NavKey>.onboarding(
    onBack: () -> Unit,
    onStartOnboarding: () -> Unit,
    onContinueToEntry: () -> Unit,
    onImportCompleted: () -> Unit,
    onWelcomeBack: () -> Unit,
    onComplete: () -> Unit,
) {
    entry<OnboardingStart> {
        OnboardingStartScreen(
            onNext = onStartOnboarding,
            onStartFromBackup = onStartOnboarding
        )
    }
    entry<OnboardingSignIn> { _ ->
        CloudAccountSetupScreen(
            onBack = onBack,
            onSkip = onContinueToEntry,
            onContinue = onWelcomeBack,
        )
    }
    entry<OnboardingEntryRoute> {

    }
    entry<OnboardingImportRoute> {
        MemoriesImportInfoScreen(
            onBack = onBack,
            onContinue = onImportCompleted,
        )
    }
    entry<OnboardingCompleteRoute> {
        OnboardingCompletionScreen(
            onFinish = onComplete,
        )
    }
    entry<OnboardingWelcomeBackRoute> {
        WelcomeBackScreen(
            onFinish = onComplete,
        )
    }
}