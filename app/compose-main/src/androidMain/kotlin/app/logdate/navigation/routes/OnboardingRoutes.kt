package app.logdate.navigation.routes

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.client.domain.dayboundary.HealthConnectStatus
import app.logdate.client.launchHealthConnectSetup
import app.logdate.feature.onboarding.flow.OnboardingEntryMode
import app.logdate.feature.onboarding.flow.OnboardingProgressSnapshot
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
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.OnboardingAccountCreationRoute
import app.logdate.navigation.routes.core.OnboardingAppOverviewRoute
import app.logdate.navigation.routes.core.OnboardingBirthdayRoute
import app.logdate.navigation.routes.core.OnboardingCompleteRoute
import app.logdate.navigation.routes.core.OnboardingDayBoundariesRoute
import app.logdate.navigation.routes.core.OnboardingImportRoute
import app.logdate.navigation.routes.core.OnboardingLocationTimelineRoute
import app.logdate.navigation.routes.core.OnboardingMemorySelectionRoute
import app.logdate.navigation.routes.core.OnboardingNotificationsRoute
import app.logdate.navigation.routes.core.OnboardingRecommendationsRoute
import app.logdate.navigation.routes.core.OnboardingStart
import app.logdate.navigation.routes.core.OnboardingWelcomeBackRoute
import app.logdate.navigation.routes.core.PersonalIntroRoute
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Navigates to the onboarding flow.
 *
 * This clears the backstack and sets the OnboardingStart route as the only entry,
 * ensuring a clean onboarding experience.
 */
fun MainAppNavigator.startOnboarding() {
    safelyClearBackstack(OnboardingStart)
}

fun EntryProviderScope<NavKey>.onboarding(
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    onComplete: () -> Unit,
) {
    routeEntry<OnboardingStart> {
        val viewModel = koinViewModel<OnboardingViewModel>()
        val progressSnapshot by viewModel.progressSnapshot.collectAsState()
        val coroutineScope = rememberCoroutineScope()

        OnboardingStartScreen(
            onNext = {
                coroutineScope.launch {
                    viewModel.setActiveEntryMode(OnboardingEntryMode.FRESH)
                    onNavigate(routeForStep(firstOnboardingStep(OnboardingEntryMode.FRESH, progressSnapshot)))
                }
            },
            onStartFromBackup = {
                coroutineScope.launch {
                    viewModel.setActiveEntryMode(OnboardingEntryMode.CONTINUE_SETUP)
                    onNavigate(routeForStep(firstOnboardingStep(OnboardingEntryMode.CONTINUE_SETUP, progressSnapshot)))
                }
            },
        )
    }
    routeEntry<PersonalIntroRoute> {
        val viewModel = koinViewModel<OnboardingViewModel>()
        val progressSnapshot by viewModel.progressSnapshot.collectAsState()
        val entryMode by viewModel.activeEntryMode.collectAsState()

        PersonalIntroScreen(
            onNext = {
                onNavigate(
                    routeForNextStep(
                        currentStep = OnboardingStep.PERSONAL_INTRO,
                        entryMode = entryMode,
                        snapshot = progressSnapshot.copy(hasPersonalIntro = true),
                    ),
                )
            },
            onBack = onBack,
        )
    }
    routeEntry<OnboardingAppOverviewRoute> {
        val viewModel = koinViewModel<OnboardingViewModel>()
        val progressSnapshot by viewModel.progressSnapshot.collectAsState()
        val entryMode by viewModel.activeEntryMode.collectAsState()

        OnboardingOverviewScreen(
            onBack = onBack,
            onNext = {
                onNavigate(
                    routeForNextStep(
                        currentStep = OnboardingStep.APP_OVERVIEW,
                        entryMode = entryMode,
                        snapshot = progressSnapshot,
                    ),
                )
            },
        )
    }
    routeEntry<OnboardingImportRoute> {
        val viewModel = koinViewModel<OnboardingViewModel>()
        val progressSnapshot by viewModel.progressSnapshot.collectAsState()
        val entryMode by viewModel.activeEntryMode.collectAsState()

        MemoriesImportInfoScreen(
            onBack = onBack,
            onContinue = {
                onNavigate(
                    routeForNextStep(
                        currentStep = OnboardingStep.MEMORY_IMPORT,
                        entryMode = entryMode,
                        snapshot = progressSnapshot,
                    ),
                )
            },
        )
    }
    routeEntry<OnboardingMemorySelectionRoute> {
        val flowViewModel = koinViewModel<OnboardingViewModel>()
        val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
        val entryMode by flowViewModel.activeEntryMode.collectAsState()
        val viewModel = koinViewModel<MemorySelectionViewModel>()
        val uiState by viewModel.uiState.collectAsState()
        val coroutineScope = rememberCoroutineScope()

        MemorySelectionScreen(
            uiState = uiState,
            onBack = onBack,
            onContinue = {
                coroutineScope.launch {
                    viewModel
                        .processSelectedMemories()
                        .onSuccess {
                            onNavigate(
                                routeForNextStep(
                                    currentStep = OnboardingStep.MEMORY_SELECTION,
                                    entryMode = entryMode,
                                    snapshot = progressSnapshot,
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
    routeEntry<OnboardingAccountCreationRoute> {
        val flowViewModel = koinViewModel<OnboardingViewModel>()
        val progressSnapshot by flowViewModel.progressSnapshot.collectAsState()
        val entryMode by flowViewModel.activeEntryMode.collectAsState()

        CloudAccountSetupScreen(
            onBack = onBack,
            onContinue = {
                onNavigate(
                    routeForNextStep(
                        currentStep = OnboardingStep.ACCOUNT,
                        entryMode = entryMode,
                        snapshot = progressSnapshot.copy(hasCloudAccount = true),
                    ),
                )
            },
            onSkip = {
                onNavigate(
                    routeForNextStep(
                        currentStep = OnboardingStep.ACCOUNT,
                        entryMode = entryMode,
                        snapshot = progressSnapshot,
                    ),
                )
            },
        )
    }
    routeEntry<OnboardingBirthdayRoute> {
        val viewModel = koinViewModel<OnboardingViewModel>()
        val progressSnapshot by viewModel.progressSnapshot.collectAsState()
        val entryMode by viewModel.activeEntryMode.collectAsState()

        OnboardingBirthdayScreen(
            onBack = onBack,
            onNext = {
                onNavigate(
                    routeForNextStep(
                        currentStep = OnboardingStep.BIRTHDAY,
                        entryMode = entryMode,
                        snapshot = progressSnapshot.copy(hasBirthday = true),
                    ),
                )
            },
        )
    }
    routeEntry<OnboardingRecommendationsRoute> {
        val viewModel = koinViewModel<OnboardingViewModel>()
        val progressSnapshot by viewModel.progressSnapshot.collectAsState()
        val entryMode by viewModel.activeEntryMode.collectAsState()
        var pendingRecommendationsEnabled by rememberSaveable { mutableStateOf<Boolean?>(null) }

        LaunchedEffect(pendingRecommendationsEnabled, progressSnapshot.healthConnectStatus) {
            if (
                pendingRecommendationsEnabled == null ||
                progressSnapshot.healthConnectStatus == HealthConnectStatus.CHECKING
            ) {
                return@LaunchedEffect
            }

            val recommendationsEnabled = pendingRecommendationsEnabled ?: return@LaunchedEffect
            pendingRecommendationsEnabled = null
            onNavigate(
                routeForNextStep(
                    currentStep = OnboardingStep.RECOMMENDATIONS,
                    entryMode = entryMode,
                    snapshot =
                        progressSnapshot.copy(
                            recommendationsHandledOnThisDevice = true,
                            contextualRecommendationsEnabled = recommendationsEnabled,
                        ),
                ),
            )
        }

        OnboardingRecommendationsScreen(
            onBack = onBack,
            onNext = { recommendationsEnabled ->
                if (progressSnapshot.healthConnectStatus == HealthConnectStatus.CHECKING) {
                    pendingRecommendationsEnabled = recommendationsEnabled
                } else {
                    onNavigate(
                        routeForNextStep(
                            currentStep = OnboardingStep.RECOMMENDATIONS,
                            entryMode = entryMode,
                            snapshot =
                                progressSnapshot.copy(
                                    recommendationsHandledOnThisDevice = true,
                                    contextualRecommendationsEnabled = recommendationsEnabled,
                                ),
                        ),
                    )
                }
            },
        )
    }
    routeEntry<OnboardingDayBoundariesRoute> {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val viewModel = koinViewModel<OnboardingViewModel>()
        val progressSnapshot by viewModel.progressSnapshot.collectAsState()
        val entryMode by viewModel.activeEntryMode.collectAsState()
        var awaitingHealthConnectSetupResult by rememberSaveable { mutableStateOf(false) }

        DisposableEffect(lifecycleOwner, viewModel) {
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && awaitingHealthConnectSetupResult) {
                        awaitingHealthConnectSetupResult = false
                        Napier.i("Refreshing onboarding Health Connect status after setup return")
                        viewModel.refreshHealthStatus()
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        OnboardingDayBoundariesScreen(
            onBack = onBack,
            onSetUpHealthConnect = {
                awaitingHealthConnectSetupResult = true
                launchHealthConnectSetup(context)
            },
            onNext = { dayBoundariesEnabled ->
                onNavigate(
                    routeForNextStep(
                        currentStep = OnboardingStep.DAY_BOUNDARIES,
                        entryMode = entryMode,
                        snapshot =
                            progressSnapshot.copy(
                                dayBoundariesHandledOnThisDevice = true,
                                sleepBasedDayBoundariesEnabled = dayBoundariesEnabled,
                            ),
                    ),
                )
            },
        )
    }
    routeEntry<OnboardingLocationTimelineRoute> {
        val viewModel = koinViewModel<OnboardingViewModel>()
        val progressSnapshot by viewModel.progressSnapshot.collectAsState()
        val entryMode by viewModel.activeEntryMode.collectAsState()

        OnboardingLocationScreen(
            onBack = onBack,
            onNext = { locationEnabled ->
                onNavigate(
                    routeForNextStep(
                        currentStep = OnboardingStep.LOCATION,
                        entryMode = entryMode,
                        snapshot =
                            progressSnapshot.copy(
                                locationHandledOnThisDevice = true,
                                locationTrackingEnabled = locationEnabled,
                            ),
                    ),
                )
            },
        )
    }
    routeEntry<OnboardingNotificationsRoute> {
        val viewModel = koinViewModel<OnboardingViewModel>()
        val progressSnapshot by viewModel.progressSnapshot.collectAsState()
        val entryMode by viewModel.activeEntryMode.collectAsState()

        OnboardingNotificationsScreen(
            onBack = onBack,
            onNext = {
                onNavigate(
                    routeForNextStep(
                        currentStep = OnboardingStep.NOTIFICATIONS,
                        entryMode = entryMode,
                        snapshot = progressSnapshot.copy(notificationsHandledOnThisDevice = true),
                    ),
                )
            },
        )
    }
    routeEntry<OnboardingCompleteRoute> {
        OnboardingCompletionScreen(
            onFinish = onComplete,
            onRequirementsIncomplete = { step ->
                onNavigate(routeForStep(step))
            },
        )
    }
    routeEntry<OnboardingWelcomeBackRoute> {
        WelcomeBackScreen(
            onFinish = onComplete,
        )
    }
}

private fun routeForNextStep(
    currentStep: OnboardingStep,
    entryMode: OnboardingEntryMode,
    snapshot: OnboardingProgressSnapshot,
): NavKey = routeForStep(nextOnboardingStepAfter(currentStep, entryMode, snapshot) ?: terminalStepFor(entryMode))

private fun routeForStep(step: OnboardingStep): NavKey =
    when (step) {
        OnboardingStep.PERSONAL_INTRO -> PersonalIntroRoute
        OnboardingStep.APP_OVERVIEW -> OnboardingAppOverviewRoute
        OnboardingStep.MEMORY_IMPORT -> OnboardingImportRoute
        OnboardingStep.MEMORY_SELECTION -> OnboardingMemorySelectionRoute
        OnboardingStep.ACCOUNT -> OnboardingAccountCreationRoute
        OnboardingStep.BIRTHDAY -> OnboardingBirthdayRoute
        OnboardingStep.RECOMMENDATIONS -> OnboardingRecommendationsRoute
        OnboardingStep.DAY_BOUNDARIES -> OnboardingDayBoundariesRoute
        OnboardingStep.LOCATION -> OnboardingLocationTimelineRoute
        OnboardingStep.NOTIFICATIONS -> OnboardingNotificationsRoute
        OnboardingStep.COMPLETE -> OnboardingCompleteRoute
        OnboardingStep.WELCOME_BACK -> OnboardingWelcomeBackRoute
    }

private fun terminalStepFor(entryMode: OnboardingEntryMode): OnboardingStep =
    when (entryMode) {
        OnboardingEntryMode.FRESH -> OnboardingStep.COMPLETE
        OnboardingEntryMode.CONTINUE_SETUP -> OnboardingStep.WELCOME_BACK
    }
