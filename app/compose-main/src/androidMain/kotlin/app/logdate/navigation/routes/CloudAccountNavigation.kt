package app.logdate.navigation.routes

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.core.account.CloudAccountOnboardingScreen
import app.logdate.feature.core.account.CloudAccountOnboardingViewModel
import app.logdate.feature.core.account.OnboardingStep
import org.koin.compose.viewmodel.koinViewModel

/**
 * Adds the unified cloud account setup flow route used from settings.
 *
 * This reuses [CloudAccountOnboardingScreen] but starts past the welcome pitch,
 * since the user already committed from the settings promotional UI.
 */
fun EntryProviderScope<NavKey>.cloudAccountSetupFlow(
    onBack: () -> Unit,
    onSetupCompleted: () -> Unit,
) {
    routeEntry<CloudAccountSetupFlowRoute> { route ->
        val viewModel = koinViewModel<CloudAccountOnboardingViewModel>()
        val initialStep =
            if (route.startOnSignIn) OnboardingStep.SignIn else OnboardingStep.DisplayName

        LaunchedEffect(Unit) {
            viewModel.setInitialStep(initialStep)
        }

        CloudAccountOnboardingScreen(
            viewModel = viewModel,
            onAccountCreated = onSetupCompleted,
            onSkipOnboarding = onBack,
            onBack = onBack,
        )
    }
}
