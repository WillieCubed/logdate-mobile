package app.logdate.mobile.ui

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
import app.logdate.feature.onboarding.navigation.onboardingGraph
import app.logdate.mobile.settings.ui.settingsGraph
import app.logdate.mobile.ui.common.MainAppState
import app.logdate.mobile.ui.navigation.RouteDestination
import app.logdate.mobile.ui.navigation.navigateToRewindList

/**
 * Main wrapper for app UI and navigation.
 */
@Composable
fun LogdateAppRoot(
    appState: MainAppState,
    viewModel: AppViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val startDestination = RouteDestination.Onboarding.route
    val context = LocalContext.current

    LaunchedEffect(
        uiState
    ) {
        val state = uiState as? LaunchAppUiState.Loaded
        if (state is LaunchAppUiState.Loaded && state.isBiometricEnabled) {
            viewModel.showBiometricPrompt(context as FragmentActivity)
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
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
            onViewPreviousRewinds = {
                navController.navigateToRewindList()
            },
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