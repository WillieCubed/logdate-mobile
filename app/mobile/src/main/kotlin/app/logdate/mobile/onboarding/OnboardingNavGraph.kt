package app.logdate.mobile.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.logdate.mobile.onboarding.ui.OnboardingScreen
import app.logdate.mobile.ui.navigation.RouteDestination


fun NavGraphBuilder.onboardingNavGraph(
    onNavigateBack: () -> Unit,
    onFinish: () -> Unit,
) {
    navigation(
        startDestination = "landing",
        route = RouteDestination.Onboarding.route,
    ) {
        composable("landing") {
            OnboardingScreen(onFinish = onFinish)
        }
    }
}
