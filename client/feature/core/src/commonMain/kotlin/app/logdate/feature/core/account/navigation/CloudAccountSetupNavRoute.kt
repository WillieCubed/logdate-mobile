package app.logdate.feature.core.account.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.logdate.feature.core.account.CloudAccountOnboardingScreen
import app.logdate.feature.core.account.CloudAccountOnboardingViewModel
import app.logdate.feature.core.account.OnboardingStep
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

/**
 * Top-level cloud account setup flow opened from the Settings screen ("Sign in to LogDate
 * Cloud", "Create a cloud account") and from `SyncSettingsScreen`. Reuses the same
 * `CloudAccountOnboardingScreen` the in-onboarding step uses; the only knob is whether to
 * jump directly to the sign-in step instead of starting at the welcome screen.
 */
@Serializable
data class CloudAccountSetupRoute(val startOnSignIn: Boolean = false)

fun NavController.navigateToCloudAccountSetup(startOnSignIn: Boolean = false) {
    navigate(CloudAccountSetupRoute(startOnSignIn = startOnSignIn))
}

fun NavGraphBuilder.cloudAccountSetupRoute(
    onAccountCreated: () -> Unit,
    onSkipped: () -> Unit,
    onBack: () -> Unit,
) {
    composable<CloudAccountSetupRoute> { entry ->
        val viewModel: CloudAccountOnboardingViewModel = koinViewModel()
        val route = entry.toRoute<CloudAccountSetupRoute>()
        LaunchedEffect(route.startOnSignIn) {
            if (route.startOnSignIn) {
                viewModel.setInitialStep(OnboardingStep.SignIn)
            }
        }
        CloudAccountOnboardingScreen(
            viewModel = viewModel,
            onAccountCreated = onAccountCreated,
            onSkipOnboarding = onSkipped,
            onBack = onBack,
        )
    }
}
