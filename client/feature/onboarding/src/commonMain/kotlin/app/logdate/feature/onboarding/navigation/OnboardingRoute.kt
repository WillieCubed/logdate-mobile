package app.logdate.feature.onboarding.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import app.logdate.feature.core.account.CloudAccountOnboardingScreen
import app.logdate.feature.core.account.CloudAccountOnboardingViewModel
import app.logdate.feature.onboarding.ui.MemoriesImportInfoScreen
import app.logdate.feature.onboarding.ui.MemorySelectionScreen
import app.logdate.feature.onboarding.ui.MemorySelectionViewModel
import app.logdate.feature.onboarding.ui.OnboardingCompletionScreen
import app.logdate.feature.onboarding.ui.OnboardingNotificationConfirmationScreen
import app.logdate.feature.onboarding.ui.OnboardingNotificationScreen
import app.logdate.feature.onboarding.ui.OnboardingOverviewScreen
import app.logdate.feature.onboarding.ui.OnboardingStartScreen
import app.logdate.feature.onboarding.ui.PersonalIntroScreen
import app.logdate.feature.onboarding.ui.WelcomeBackScreen
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

sealed interface OnboardingBaseRoute

/**
 * The onboarding route of the app.
 */
@Serializable
data object OnboardingRoute : OnboardingBaseRoute

@Serializable
data object OnboardingStart : OnboardingBaseRoute

@Serializable
data object PersonalIntro : OnboardingBaseRoute

@Serializable
data object AppOverview : OnboardingBaseRoute

@Serializable
data object FirstEntry : OnboardingBaseRoute

@Serializable
data object CloudSync : OnboardingBaseRoute

@Serializable
data object MemoryImport : OnboardingBaseRoute

@Serializable
data object MemorySelection : OnboardingBaseRoute

@Serializable
data object AccountCreation : OnboardingBaseRoute

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
                    onGoToItem(PersonalIntro)
                },
                onStartFromBackup = {
                    onGoToItem(OnboardingComplete)
                },
            )
        }
        composable<PersonalIntro> {
            PersonalIntroScreen(
                onNext = {
                    onGoToItem(AppOverview)
                },
                onBack = onNavigateBack
            )
        }
        composable<AppOverview> {
            OnboardingOverviewScreen(
                onBack = onNavigateBack,
                onNext = {
                    onGoToItem(MemoryImport)
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
            LaunchedEffect(Unit) {
                onGoToItem(OnboardingPreferences)
            }
        }
        composable<CloudSync> { }
        composable<MemoryImport> {
            MemoriesImportInfoScreen(
                onBack = onNavigateBack,
                onContinue = {
                    onGoToItem(MemorySelection)
                },
            )
        }
        composable<MemorySelection> {
            val viewModel = koinViewModel<MemorySelectionViewModel>()
            MemorySelectionScreen(
                uiState = viewModel.uiState.collectAsState().value,
                onBack = onNavigateBack,
                onContinue = {
                    viewModel.processSelectedMemories()
                    onGoToItem(AccountCreation)
                },
                onToggleMemorySelection = viewModel::toggleMemorySelection,
                onLoadMoreMemories = viewModel::loadMoreMemories,
            )
        }
        composable<AccountCreation> {
            // Use the existing CloudAccountOnboardingScreen from core feature
            CloudAccountOnboardingScreen(
                viewModel = koinViewModel<CloudAccountOnboardingViewModel>(),
                onAccountCreated = {
                    onGoToItem(OnboardingPreferences)
                },
                onSkipOnboarding = {
                    onGoToItem(OnboardingPreferences)
                }
            )
        }
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
            WelcomeBackScreen(onFinish = onWelcomeBack)
        }
    }
}