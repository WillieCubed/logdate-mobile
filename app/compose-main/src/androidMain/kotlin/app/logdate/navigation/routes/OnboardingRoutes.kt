package app.logdate.navigation.routes

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import app.logdate.feature.onboarding.ui.CloudAccountSetupScreen
import app.logdate.feature.onboarding.ui.MemoriesImportInfoScreen
import app.logdate.feature.onboarding.ui.OnboardingCompletionScreen
import app.logdate.feature.onboarding.ui.OnboardingStartScreen
import app.logdate.feature.onboarding.ui.PersonalIntroScreen
import app.logdate.feature.onboarding.ui.WelcomeBackScreen
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.OnboardingCompleteRoute
import app.logdate.navigation.routes.core.OnboardingEntryRoute
import app.logdate.navigation.routes.core.OnboardingImportRoute
import app.logdate.navigation.routes.core.OnboardingSignIn
import app.logdate.navigation.routes.core.OnboardingStart
import app.logdate.navigation.routes.core.OnboardingWelcomeBackRoute
import app.logdate.navigation.routes.core.PersonalIntroRoute

/**
 * Navigates to the onboarding flow.
 * 
 * This clears the backstack and sets the OnboardingStart route as the only entry,
 * ensuring a clean onboarding experience.
 */
fun MainAppNavigator.startOnboarding() {
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
    entry<OnboardingStart>() {
        OnboardingStartScreen(
            onNext = onStartOnboarding,
            onStartFromBackup = onStartOnboarding
        )
    }
    entry<PersonalIntroRoute>() {
        PersonalIntroScreen(
            onNext = onContinueToEntry,
            onBack = onBack
        )
    }
    entry<OnboardingSignIn>() { _ ->
        CloudAccountSetupScreen(
            onBack = onBack,
            onSkip = onContinueToEntry,
            onContinue = onWelcomeBack,
        )
    }
    entry<OnboardingEntryRoute>() {

    }
    entry<OnboardingImportRoute>() {
        MemoriesImportInfoScreen(
            onBack = onBack,
            onContinue = onImportCompleted,
        )
    }
    entry<OnboardingCompleteRoute>() {
        OnboardingCompletionScreen(
            onFinish = onComplete,
        )
    }
    entry<OnboardingWelcomeBackRoute>() {
        WelcomeBackScreen(
            onFinish = onComplete,
        )
    }
}