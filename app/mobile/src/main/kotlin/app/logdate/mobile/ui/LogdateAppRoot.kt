package app.logdate.mobile.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.logdate.feature.journals.navigation.navigateFromNew
import app.logdate.feature.journals.navigation.navigateToJournal
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
    onboarded: Boolean = false,
    navController: NavHostController = rememberNavController(),
) {
    val startDestination = when {
        onboarded -> RouteDestination.Base.route
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
                // Navigate to base route after onboarding is complete, making sure
                // to clear the back stack.
                navController.navigate(RouteDestination.Base.route) {
                    popUpTo(RouteDestination.Onboarding.route) { inclusive = true }
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
            }
        )
        settingsGraph()
    }
}
