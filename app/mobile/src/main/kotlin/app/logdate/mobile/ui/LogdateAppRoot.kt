package app.logdate.mobile.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.logdate.feature.editor.navigation.navigateToNoteCreation
import app.logdate.feature.journals.navigation.navigateFromNew
import app.logdate.feature.journals.navigation.navigateToJournal
import app.logdate.feature.onboarding.navigation.launchOnboarding
import app.logdate.feature.onboarding.navigation.onboardingGraph
import app.logdate.feature.rewind.navigation.navigateToRewind
import app.logdate.feature.rewind.navigation.navigateToRewindsOverview
import app.logdate.mobile.settings.ui.settingsGraph
import app.logdate.mobile.ui.common.MainAppState
import app.logdate.mobile.ui.navigation.RouteDestination

/**
 * Main wrapper for app UI and navigation.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LogdateAppRoot(
    appState: MainAppState,
    viewModel: AppViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(
        uiState
    ) {
        val state = uiState as? LaunchAppUiState.Loaded
        if (state !is LaunchAppUiState.Loaded) {
            return@LaunchedEffect
        }
        // Ensure that onboarding is completed before proceeding
        if (!state.isOnboarded) {
            navController.launchOnboarding()
            return@LaunchedEffect
        }
        if (state.isBiometricEnabled) {
            viewModel.showBiometricPrompt(context as FragmentActivity)
        }
    }

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = RouteDestination.Base.route,
        ) {
            onboardingGraph(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onGoToItem = { id ->
                    navController.navigate(id)
                },
                onFinish = {
                    // Navigate to base route after onboarding is complete, making sure
                    // to clear the back stack.
                    navController.navigate(RouteDestination.Base.route) {
                        // TODO: Figure out why this breaks stuff
//                    popUpTo(RouteDestination.Onboarding.route) { inclusive = true }
                    }
                }
            )
            mainNavGraph(
                appState = appState,
                onCreateEntry = {
                    navController.navigateToNoteCreation()
                },
                onOpenRewind = navController::navigateToRewind,
                onViewPreviousRewinds = navController::navigateToRewindsOverview,
                onClose = {
                    navController.popBackStack()
                },
                onCreateJournal = {
                    navController.navigate(RouteDestination.NewJournal.route)
                },
                onOpenJournal = { journalId ->
                    navController.navigateToJournal(journalId)
                },
                onJournalCreated = { journalId ->
                    navController.navigateFromNew(journalId)
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onJournalDeleted = {
                    // Navigate to home
                    navController.navigate(RouteDestination.Home.route) {
                        popUpTo(RouteDestination.Base.route) { inclusive = true }
                    }
                },
                onNoteSaved = {
                    navController.navigate(RouteDestination.Home.route) {
                        popUpTo(RouteDestination.Base.route) { inclusive = true }
                    }
                },
                onOpenSettings = {
                    navController.navigate(RouteDestination.Settings.route)
                },
            )
            settingsGraph(
                onGoBack = {
                    navController.popBackStack()
                },
                onReset = {
                    // Navigate to onboarding, clearing the back stack
                    navController.navigate(RouteDestination.Onboarding.route) {
                        popUpTo(RouteDestination.Base.route) { inclusive = true }
                    }
                },
            )
        }
    }
}