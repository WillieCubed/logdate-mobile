package app.logdate.mobile.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.logdate.feature.journals.ui.navigateToJournal
import app.logdate.mobile.onboarding.onboardingNavGraph
import app.logdate.mobile.settings.ui.settingsGraph
import app.logdate.mobile.ui.common.MainAppState
import app.logdate.mobile.ui.common.navigateToNoteCreation
import app.logdate.mobile.ui.navigation.RouteDestination
import app.logdate.mobile.ui.navigation.navigateToRewindList

/**
 * Main wrapper for app UI and navigation.
 */
@Composable
fun LogdateAppRoot(
    appState: MainAppState,
    navController: NavHostController = rememberNavController(),
) {
    val onboarded = false

    val startDestination = when {
        onboarded -> RouteDestination.Home.route
        else -> RouteDestination.Onboarding.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        onboardingNavGraph(
            onNavigateBack = {
                navController.popBackStack()
            },
            onFinish = {
                navController.navigate(RouteDestination.Base.route)
            }
        )
        mainNavGraph(
            appState = appState,
            onNavigateBack = {
                navController.popBackStack()
            },
            onClose = {
                navController.popBackStack()
            },
            onCreateEntry = {
                navController.navigateToNoteCreation()
            },
            onViewPreviousRewinds = {
                navController.navigateToRewindList()
            },
            onOpenJournal = {
                navController.navigateToJournal(it)
            },
        )
        settingsGraph()
    }
}

