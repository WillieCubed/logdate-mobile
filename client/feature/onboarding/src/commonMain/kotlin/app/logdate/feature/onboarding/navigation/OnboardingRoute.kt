package app.logdate.feature.onboarding.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import app.logdate.client.domain.dayboundary.HealthConnectStatus
import app.logdate.feature.core.account.CloudAccountOnboardingScreen
import app.logdate.feature.core.account.CloudAccountOnboardingViewModel
import app.logdate.feature.onboarding.flow.OnboardingEntryMode
import app.logdate.feature.onboarding.flow.OnboardingStep
import app.logdate.feature.onboarding.flow.firstOnboardingStep
import app.logdate.feature.onboarding.flow.nextOnboardingStepAfter
import app.logdate.feature.onboarding.ui.CloudAccountSetupScreen
import app.logdate.feature.onboarding.ui.MemoriesImportInfoScreen
import app.logdate.feature.onboarding.ui.MemorySelectionScreen
import app.logdate.feature.onboarding.ui.MemorySelectionViewModel
import app.logdate.feature.onboarding.ui.OnboardingBirthdayScreen
import app.logdate.feature.onboarding.ui.OnboardingCompletionScreen
import app.logdate.feature.onboarding.ui.OnboardingDayBoundariesScreen
import app.logdate.feature.onboarding.ui.OnboardingLocationScreen
import app.logdate.feature.onboarding.ui.OnboardingNotificationsScreen
import app.logdate.feature.onboarding.ui.OnboardingOverviewScreen
import app.logdate.feature.onboarding.ui.OnboardingRecommendationsScreen
import app.logdate.feature.onboarding.ui.OnboardingStartScreen
import app.logdate.feature.onboarding.ui.OnboardingViewModel
import app.logdate.feature.onboarding.ui.PersonalIntroScreen
import app.logdate.feature.onboarding.ui.WelcomeBackScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import app.logdate.feature.core.account.OnboardingStep as CloudAccountEntryStep

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

@Serializable
data object BirthdayIntro : OnboardingBaseRoute

@Serializable
data object FeatureRecommendations : OnboardingBaseRoute

@Serializable
data object FeatureDayBoundaries : OnboardingBaseRoute

@Serializable
data object FeatureLocationTimeline : OnboardingBaseRoute

@Serializable
data object FeatureNotifications : OnboardingBaseRoute

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
            val flowViewModel = koinViewModel<OnboardingViewModel>()
            val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
            val coroutineScope = rememberCoroutineScope()

            OnboardingStartScreen(
                onNext = {
                    coroutineScope.launch {
                        flowViewModel.setActiveEntryMode(OnboardingEntryMode.FRESH)
                        onGoToItem(routeForStep(firstOnboardingStep(OnboardingEntryMode.FRESH, progressSnapshot)))
                    }
                },
                onStartFromBackup = {
                    coroutineScope.launch {
                        flowViewModel.setActiveEntryMode(OnboardingEntryMode.CONTINUE_SETUP)
                        onGoToItem(routeForStep(firstOnboardingStep(OnboardingEntryMode.CONTINUE_SETUP, progressSnapshot)))
                    }
                },
            )
        }
        composable<PersonalIntro> {
            val flowViewModel = koinViewModel<OnboardingViewModel>()
            val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
            val entryMode by flowViewModel.activeEntryMode.collectAsState()

            PersonalIntroScreen(
                onNext = {
                    onGoToItem(
                        routeForStep(
                            nextOnboardingStepAfter(
                                currentStep = OnboardingStep.PERSONAL_INTRO,
                                entryMode = entryMode,
                                snapshot = progressSnapshot.copy(hasPersonalIntro = true),
                            ) ?: terminalStepFor(entryMode),
                        ),
                    )
                },
                onBack = onNavigateBack,
            )
        }
        composable<AppOverview> {
            val flowViewModel = koinViewModel<OnboardingViewModel>()
            val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
            val entryMode by flowViewModel.activeEntryMode.collectAsState()

            OnboardingOverviewScreen(
                onBack = onNavigateBack,
                onNext = {
                    onGoToItem(
                        routeForStep(
                            nextOnboardingStepAfter(
                                currentStep = OnboardingStep.APP_OVERVIEW,
                                entryMode = entryMode,
                                snapshot = progressSnapshot,
                            ) ?: terminalStepFor(entryMode),
                        ),
                    )
                },
            )
        }
        composable<CloudSync> {
            val flowViewModel = koinViewModel<OnboardingViewModel>()
            val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
            val entryMode by flowViewModel.activeEntryMode.collectAsState()

            CloudAccountSetupScreen(
                onBack = onNavigateBack,
                onContinue = {
                    onGoToItem(
                        routeForStep(
                            nextOnboardingStepAfter(
                                currentStep = OnboardingStep.ACCOUNT,
                                entryMode = entryMode,
                                snapshot = progressSnapshot.copy(hasCloudAccount = true),
                            ) ?: terminalStepFor(entryMode),
                        ),
                    )
                },
                onSkip = {
                    onGoToItem(
                        routeForStep(
                            nextOnboardingStepAfter(
                                currentStep = OnboardingStep.ACCOUNT,
                                entryMode = entryMode,
                                snapshot = progressSnapshot,
                            ) ?: terminalStepFor(entryMode),
                        ),
                    )
                },
            )
        }
        composable<MemoryImport> {
            val flowViewModel = koinViewModel<OnboardingViewModel>()
            val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
            val entryMode by flowViewModel.activeEntryMode.collectAsState()

            MemoriesImportInfoScreen(
                onBack = onNavigateBack,
                onContinue = {
                    onGoToItem(
                        routeForStep(
                            nextOnboardingStepAfter(
                                currentStep = OnboardingStep.MEMORY_IMPORT,
                                entryMode = entryMode,
                                snapshot = progressSnapshot,
                            ) ?: terminalStepFor(entryMode),
                        ),
                    )
                },
            )
        }
        composable<MemorySelection> {
            val flowViewModel = koinViewModel<OnboardingViewModel>()
            val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
            val entryMode by flowViewModel.activeEntryMode.collectAsState()
            val viewModel = koinViewModel<MemorySelectionViewModel>()
            val coroutineScope = rememberCoroutineScope()
            MemorySelectionScreen(
                uiState = viewModel.uiState.collectAsState().value,
                onBack = onNavigateBack,
                onContinue = {
                    coroutineScope.launch {
                        viewModel
                            .processSelectedMemories()
                            .onSuccess {
                                onGoToItem(
                                    routeForStep(
                                        nextOnboardingStepAfter(
                                            currentStep = OnboardingStep.MEMORY_SELECTION,
                                            entryMode = entryMode,
                                            snapshot = progressSnapshot,
                                        ) ?: terminalStepFor(entryMode),
                                    ),
                                )
                            }
                    }
                },
                onToggleMemorySelection = viewModel::toggleMemorySelection,
                onLoadMoreMemories = viewModel::loadMoreMemories,
                onRefreshMemories = viewModel::refreshMemories,
            )
        }
        composable<AccountCreation> {
            val flowViewModel = koinViewModel<OnboardingViewModel>()
            val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
            val entryMode by flowViewModel.activeEntryMode.collectAsState()

            CloudAccountSetupScreen(
                onBack = onNavigateBack,
                onContinue = {
                    onGoToItem(
                        routeForStep(
                            nextOnboardingStepAfter(
                                currentStep = OnboardingStep.ACCOUNT,
                                entryMode = entryMode,
                                snapshot = progressSnapshot.copy(hasCloudAccount = true),
                            ) ?: terminalStepFor(entryMode),
                        ),
                    )
                },
                onSkip = {
                    onGoToItem(
                        routeForStep(
                            nextOnboardingStepAfter(
                                currentStep = OnboardingStep.ACCOUNT,
                                entryMode = entryMode,
                                snapshot = progressSnapshot,
                            ) ?: terminalStepFor(entryMode),
                        ),
                    )
                },
            )
        }
        composable<BirthdayIntro> {
            val flowViewModel = koinViewModel<OnboardingViewModel>()
            val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
            val entryMode by flowViewModel.activeEntryMode.collectAsState()

            OnboardingBirthdayScreen(
                onBack = onNavigateBack,
                onNext = {
                    onGoToItem(
                        routeForStep(
                            nextOnboardingStepAfter(
                                currentStep = OnboardingStep.BIRTHDAY,
                                entryMode = entryMode,
                                snapshot = progressSnapshot.copy(hasBirthday = true),
                            ) ?: terminalStepFor(entryMode),
                        ),
                    )
                },
            )
        }
        composable<FeatureRecommendations> {
            val viewModel = koinViewModel<OnboardingViewModel>()
            val progressSnapshot by viewModel.progressSnapshot.collectAsState()
            val entryMode by viewModel.activeEntryMode.collectAsState()
            val healthConnectStatus by viewModel.healthConnectStatus.collectAsState()
            var pendingNextRoute by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(pendingNextRoute, healthConnectStatus) {
                if (
                    !pendingNextRoute ||
                    healthConnectStatus == HealthConnectStatus.CHECKING
                ) {
                    return@LaunchedEffect
                }

                pendingNextRoute = false
                onGoToItem(
                    routeForStep(
                        nextOnboardingStepAfter(
                            currentStep = OnboardingStep.RECOMMENDATIONS,
                            entryMode = entryMode,
                            snapshot = progressSnapshot,
                        ) ?: terminalStepFor(entryMode),
                    ),
                )
            }

            OnboardingRecommendationsScreen(
                viewModel = viewModel,
                onBack = onNavigateBack,
                onNext = {
                    if (
                        healthConnectStatus == HealthConnectStatus.CHECKING
                    ) {
                        pendingNextRoute = true
                    } else {
                        onGoToItem(
                            routeForStep(
                                nextOnboardingStepAfter(
                                    currentStep = OnboardingStep.RECOMMENDATIONS,
                                    entryMode = entryMode,
                                    snapshot = progressSnapshot,
                                ) ?: terminalStepFor(entryMode),
                            ),
                        )
                    }
                },
            )
        }
        composable<FeatureDayBoundaries> {
            val flowViewModel = koinViewModel<OnboardingViewModel>()
            val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
            val entryMode by flowViewModel.activeEntryMode.collectAsState()

            OnboardingDayBoundariesScreen(
                onBack = onNavigateBack,
                onNext = {
                    onGoToItem(
                        routeForStep(
                            nextOnboardingStepAfter(
                                currentStep = OnboardingStep.DAY_BOUNDARIES,
                                entryMode = entryMode,
                                snapshot = progressSnapshot,
                            ) ?: terminalStepFor(entryMode),
                        ),
                    )
                },
            )
        }
        composable<FeatureLocationTimeline> {
            val flowViewModel = koinViewModel<OnboardingViewModel>()
            val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
            val entryMode by flowViewModel.activeEntryMode.collectAsState()

            OnboardingLocationScreen(
                onBack = onNavigateBack,
                onNext = {
                    onGoToItem(
                        routeForStep(
                            nextOnboardingStepAfter(
                                currentStep = OnboardingStep.LOCATION,
                                entryMode = entryMode,
                                snapshot = progressSnapshot,
                            ) ?: terminalStepFor(entryMode),
                        ),
                    )
                },
            )
        }
        composable<FeatureNotifications> {
            val flowViewModel = koinViewModel<OnboardingViewModel>()
            val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
            val entryMode by flowViewModel.activeEntryMode.collectAsState()

            OnboardingNotificationsScreen(
                onBack = onNavigateBack,
                onNext = {
                    onGoToItem(
                        routeForStep(
                            nextOnboardingStepAfter(
                                currentStep = OnboardingStep.NOTIFICATIONS,
                                entryMode = entryMode,
                                snapshot = progressSnapshot.copy(notificationsHandledOnThisDevice = true),
                            ) ?: terminalStepFor(entryMode),
                        ),
                    )
                },
            )
        }
        composable<SignIn> {
            val flowViewModel = koinViewModel<OnboardingViewModel>()
            val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
            val entryMode by flowViewModel.activeEntryMode.collectAsState()
            val cloudAccountViewModel = koinViewModel<CloudAccountOnboardingViewModel>()

            LaunchedEffect(Unit) {
                cloudAccountViewModel.resetFlow()
                cloudAccountViewModel.setInitialStep(CloudAccountEntryStep.SignIn)
            }

            CloudAccountOnboardingScreen(
                viewModel = cloudAccountViewModel,
                onAccountCreated = {
                    onGoToItem(
                        routeForStep(
                            nextOnboardingStepAfter(
                                currentStep = OnboardingStep.ACCOUNT,
                                entryMode = entryMode,
                                snapshot = progressSnapshot.copy(hasCloudAccount = true),
                            ) ?: terminalStepFor(entryMode),
                        ),
                    )
                },
                onSkipOnboarding = {
                    onGoToItem(
                        routeForStep(
                            nextOnboardingStepAfter(
                                currentStep = OnboardingStep.ACCOUNT,
                                entryMode = entryMode,
                                snapshot = progressSnapshot,
                            ) ?: terminalStepFor(entryMode),
                        ),
                    )
                },
                onBack = onNavigateBack,
            )
        }
        composable<OnboardingComplete> {
            OnboardingCompletionScreen(
                onFinish = onOnboardingComplete,
                onRequirementsIncomplete = { step ->
                    onGoToItem(routeForStep(step))
                },
            )
        }
        composable<WelcomeBack> {
            WelcomeBackScreen(onFinish = onWelcomeBack)
        }
    }
}

private fun routeForStep(step: OnboardingStep): OnboardingBaseRoute =
    when (step) {
        OnboardingStep.PERSONAL_INTRO -> PersonalIntro
        OnboardingStep.APP_OVERVIEW -> AppOverview
        OnboardingStep.MEMORY_IMPORT -> MemoryImport
        OnboardingStep.MEMORY_SELECTION -> MemorySelection
        OnboardingStep.ACCOUNT -> AccountCreation
        OnboardingStep.BIRTHDAY -> BirthdayIntro
        OnboardingStep.RECOMMENDATIONS -> FeatureRecommendations
        OnboardingStep.DAY_BOUNDARIES -> FeatureDayBoundaries
        OnboardingStep.LOCATION -> FeatureLocationTimeline
        OnboardingStep.NOTIFICATIONS -> FeatureNotifications
        OnboardingStep.COMPLETE -> OnboardingComplete
        OnboardingStep.WELCOME_BACK -> WelcomeBack
    }

private fun terminalStepFor(entryMode: OnboardingEntryMode): OnboardingStep =
    when (entryMode) {
        OnboardingEntryMode.FRESH -> OnboardingStep.COMPLETE
        OnboardingEntryMode.CONTINUE_SETUP -> OnboardingStep.WELCOME_BACK
    }
