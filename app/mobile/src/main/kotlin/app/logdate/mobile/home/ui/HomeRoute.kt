package app.logdate.mobile.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import app.logdate.feature.journals.ui.JournalClickCallback
import app.logdate.feature.library.ui.LibraryScreen
import app.logdate.feature.rewind.ui.RewindOpenCallback
import app.logdate.feature.rewind.ui.RewindScreenContent
import app.logdate.feature.rewind.ui.overview.RewindOverviewViewModel
import app.logdate.feature.timeline.ui.TimelineViewModel
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
    onOpenSettings: () -> Unit,
    onCreateEntry: () -> Unit,
    onCreateJournal: () -> Unit,
    onOpenJournal: JournalClickCallback,
) {
    HomeScreenContent(
        onCreateEntry = onCreateEntry,
        onOpenRewind = { },
    )
}

@Composable
internal fun HomeScreenContent(
    onCreateEntry: () -> Unit,
    onOpenRewind: RewindOpenCallback,
    timelineViewModel: TimelineViewModel = hiltViewModel<TimelineViewModel>(),
    rewindViewModel: RewindOverviewViewModel = hiltViewModel(),
) {
    val timelineState by timelineViewModel.uiState.collectAsState()
    val rewindState by rewindViewModel.uiState.collectAsState()

    var showFab by remember { mutableStateOf(true) }

    fun handleFabClick(currentDestination: HomeRouteDestination) = when (currentDestination) {
        HomeRouteDestination.Timeline -> onCreateEntry()
        else -> Unit
    }

    HomeScaffoldWrapper(
        showFab = showFab,
        onFabClick = ::handleFabClick,
    ) { currentDestination ->
        when (currentDestination) {
            HomeRouteDestination.Timeline -> {
                TimelineScreenContent(
                    uiState = timelineState,
                    onCreateEntry = onCreateEntry,
                    onUpdateFabVisibility = { isVisible ->
                        showFab = isVisible
                    },
                    onOpenDay = timelineViewModel::selectItem,
                    onExitDetails = { timelineViewModel.selectItem(null) },
                    onOpenEvent = {},
                )
            }

            HomeRouteDestination.Rewind -> {
                RewindScreenContent(
                    state = rewindState,
                    onOpenRewind = onOpenRewind
                )
            }

            HomeRouteDestination.Library -> {
                LibraryScreen(
                    onGoToItem = {},
                )
            }

            else -> {
            }
        }
    }
}
