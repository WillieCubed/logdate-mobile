package app.logdate.mobile.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.logdate.feature.editor.navigation.noteCreationRoute
import app.logdate.feature.journals.navigation.journalsRoute
import app.logdate.feature.journals.ui.JournalClickCallback
import app.logdate.feature.rewind.navigation.rewindRoutes
import app.logdate.feature.rewind.ui.RewindOpenCallback
import app.logdate.mobile.home.ui.HomeRoute
import app.logdate.mobile.ui.common.MainAppState
import app.logdate.mobile.ui.navigation.RouteDestination

/**
 * Navigation graph for the home screen and top-level app entry points.
 */
fun NavGraphBuilder.mainNavGraph(
    appState: MainAppState,
    // TODO: For the love of god, please clean this up
    onCreateEntry: () -> Unit,
    onViewPreviousRewinds: () -> Unit,
    onOpenRewind: RewindOpenCallback,
    onClose: () -> Unit,
    onCreateJournal: () -> Unit,
    onOpenJournal: JournalClickCallback,
    onJournalCreated: (String) -> Unit,
    onJournalDeleted: () -> Unit,
    onNoteSaved: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    navigation(
        startDestination = RouteDestination.Home.route,
        route = RouteDestination.Base.route,
    ) {
        composable(RouteDestination.Home.route) {
            HomeRoute(
                appState = appState,
                onCreateEntry = onCreateEntry,
                onViewPreviousRewinds = onViewPreviousRewinds,
                onCreateJournal = onCreateJournal,
                onOpenJournal = onOpenJournal,
                onOpenSettings = onOpenSettings,
            )
        }
        noteCreationRoute(
            onClose = {
                onNavigateBack()
            },
            onNoteSaved = onNoteSaved,
        )
        rewindRoutes(
            onOpenRewind = onOpenRewind,
            onGoBack = {
                onNavigateBack()
            },
        )
        journalsRoute(
            onGoBack = {
                onNavigateBack()
            },
            onJournalCreated = onJournalCreated,
            onJournalDeleted = onJournalDeleted
        )
    }
}

