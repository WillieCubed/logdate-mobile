package app.logdate.mobile.ui

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
import app.logdate.mobile.ui.common.rememberMainAppState
import app.logdate.mobile.ui.navigation.RouteDestination
import app.logdate.mobile.ui.navigation.navigateToRewindList
import app.logdate.ui.theme.LogDateTheme

/**
 * Main wrapper for app UI and navigation.
 */
@Composable
fun LogdateAppRoot(
    appState: MainAppState,
    onboarded: Boolean = false,
    viewModel: AppViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val startDestination = when {
        onboarded -> RouteDestination.Base.route
        else -> RouteDestination.Onboarding.route
    }

    LaunchedEffect(
        uiState
    ) {
        val state = uiState as? LaunchAppUiState.Loaded
//        if (state is LaunchAppUiState.Loaded && state.shouldShowBiometricPrompt) {
//            viewModel.showBiometricPrompt()
//        }
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

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Preview(showBackground = true, name = "App Preview - Phone")
@Composable
fun AppPreview_Mobile() {
    LogDateTheme {
        val appState = rememberMainAppState(
            windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(412.dp, 918.dp)),
        )
        LogdateAppRoot(appState)
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Preview(showBackground = true, name = "App Preview - Tablet", device = "id:pixel_tablet")
@Composable
fun AppPreview_Tablet() {
    LogDateTheme {
        val appState = rememberMainAppState(
            windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(1280.dp, 720.dp)),
        )
        LogdateAppRoot(appState)
    }
}