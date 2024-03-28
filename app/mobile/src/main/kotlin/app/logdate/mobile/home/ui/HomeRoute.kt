package app.logdate.mobile.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import app.logdate.feature.journals.ui.JournalOpenCallback
import app.logdate.mobile.ui.common.MainAppState
import app.logdate.mobile.ui.navigation.RouteDestination

fun NavController.navigateToTimeline() = navigate(
    RouteDestination.Home.route,
)

/**
 * The main entry point for the home route.
 */
@Composable
fun HomeRoute(
    appState: MainAppState,
    onViewPreviousRewinds: () -> Unit,
    onCreateEntry: () -> Unit,
    onCreateJournal: () -> Unit,
    onOpenJournal: JournalOpenCallback,
) {
    var currentDestination: HomeRouteData by rememberSaveable { mutableStateOf(HomeRouteData.Timeline) }

    HomeScreen(
        currentDestination = currentDestination,
        onUpdateDestination = {
            currentDestination = it
        },
        onViewPreviousRewinds = onViewPreviousRewinds,
        onNewJournal = onCreateJournal,
        onOpenJournal = onOpenJournal,
        onCreateEntry = onCreateEntry,
        shouldShowBottomBar = appState.shouldShowBottomBar,
        isLargeDevice = appState.isLargeDevice,
        shouldShowNavRail = appState.shouldShowNavRail,
    )
}
