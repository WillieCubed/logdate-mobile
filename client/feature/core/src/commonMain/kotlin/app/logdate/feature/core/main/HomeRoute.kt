package app.logdate.feature.core.main

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data object HomeRoute

fun NavHostController.navigateHome() {
    navigate(HomeRoute)
}

/**
 * Navigation graph for core top-level routes
 */
fun NavGraphBuilder.homeGraph(
    onCreateNote: () -> Unit,
    onOpenRewind: (uid: Uuid) -> Unit,
    onOpenSettings: () -> Unit,
) {
    composable<HomeRoute> {
//            mainNavGraph(
//                appState = appState,
//                onCreateEntry = {
//                    navController.navigateToNoteCreation()
//                },
//                onOpenRewind = navController::navigateToRewind,
//                onViewPreviousRewinds = navController::navigateToRewindsOverview,
//                onClose = {
//                    navController.popBackStack()
//                },
//                onCreateJournal = {
//                    navController.navigate(RouteDestination.NewJournal.route)
//                },
//                onOpenJournal = { journalId ->
//                    navController.navigateToJournal(journalId)
//                },
//                onJournalCreated = { journalId ->
//                    navController.navigateFromNew(journalId)
//                },
//                onNavigateBack = {
//                    navController.popBackStack()
//                },
//                onJournalDeleted = {
//                    // Navigate to home
//                    navController.navigate(RouteDestination.Home.route) {
//                        popUpTo(RouteDestination.Base.route) { inclusive = true }
//                    }
//                },
//                onNoteSaved = {
//                    navController.navigate(RouteDestination.Home.route) {
//                        popUpTo(RouteDestination.Base.route) { inclusive = true }
//                    }
//                },
//                onOpenSettings = {
//                    navController.navigate(RouteDestination.Settings.route)
//                },
//            )
    }
}