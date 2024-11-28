package app.logdate.feature.onboarding.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import app.logdate.feature.onboarding.ui.OnboardingCompletionScreen
import app.logdate.feature.onboarding.ui.OnboardingNotificationConfirmationScreen
import app.logdate.feature.onboarding.ui.OnboardingNotificationScreen
import app.logdate.feature.onboarding.ui.OnboardingOverviewScreen
import app.logdate.feature.onboarding.ui.OnboardingStartScreen
import app.logdate.feature.onboarding.ui.WelcomeBackScreen
import kotlinx.serialization.Serializable

sealed interface OnboardingBaseRoute

/**
 * The onboarding route of the app.
 */
@Serializable
data object OnboardingRoute : OnboardingBaseRoute

@Serializable
data object OnboardingStart : OnboardingBaseRoute

@Serializable
data object AppOverview : OnboardingBaseRoute

@Serializable
data object FirstEntry : OnboardingBaseRoute

@Serializable
data object CloudSync : OnboardingBaseRoute

@Serializable
data object MemoryImport : OnboardingBaseRoute

@Serializable
data object SignIn : OnboardingBaseRoute

/**
 * Screen to configure preferences including notifications.
 */
@Serializable
data object OnboardingPreferences : OnboardingBaseRoute

@Serializable
data object ConfirmPreferences : OnboardingBaseRoute

@Serializable
data object OnboardingComplete : OnboardingBaseRoute

@Serializable
data object WelcomeBack : OnboardingBaseRoute

/**
 * Navigates to the onboarding process.
 *
 * Note that this clears the back stack.
 */
fun NavController.startOnboarding() {
    navigate(OnboardingRoute) {
        popUpTo(OnboardingRoute) { inclusive = true }
    }
}

/**
 * Navigation graph for the onboarding process.
 */
fun NavGraphBuilder.onboardingGraph(
    onNavigateBack: () -> Unit,
    onWelcomeBack: () -> Unit,
    onOnboardingComplete: () -> Unit,
    onGoToItem: (route: OnboardingBaseRoute) -> Unit,
) {
    navigation<OnboardingRoute>(
        startDestination = OnboardingStart,
    ) {
        composable<OnboardingStart> {
            OnboardingStartScreen(
                onNext = {
                    onGoToItem(AppOverview)
                },
                onStartFromBackup = {
                    onGoToItem(OnboardingComplete)
                },
            )
        }
        composable<AppOverview> {
            OnboardingOverviewScreen(
                onBack = onNavigateBack,
                onNext = {
                    onGoToItem(FirstEntry)
                },
            )

        }
        composable<FirstEntry> {
            // TODO: Re-add entry creation during onboarding
//            EntryCreationScreenWrapper(
//                onBack = onNavigateBack,
//                onNext = {
//                    onGoToItem(ONBOARDING_ENABLE_NOTIFICATIONS)
//                },
//                useCompactLayout = useCompactLayout,
//            )
            onGoToItem(OnboardingPreferences)
        }
        composable<CloudSync> { }
        composable<MemoryImport> { }
        composable<OnboardingPreferences> {
            OnboardingNotificationScreen(
                onBack = {
                    onNavigateBack()
                },
                onNext = {
                    onGoToItem(ConfirmPreferences)
                },
            )
        }
        composable<ConfirmPreferences> {
            OnboardingNotificationConfirmationScreen(
                // TODO: Skip ONBOARDING_ENABLE_NOTIFICATIONS if notifications are already enabled
                onBack = onNavigateBack,
                onNext = {
                    onGoToItem(OnboardingComplete)
                },
            )

        }
        composable<SignIn> { }
        composable<OnboardingComplete> {
            OnboardingCompletionScreen(
                onFinish = onOnboardingComplete,
            )
        }
        composable<WelcomeBack> {
            WelcomeBackScreen(onFinish = onWelcomeBack) }
    }
}