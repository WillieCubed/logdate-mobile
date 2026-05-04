package app.logdate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.core.main.HomeViewModel
import app.logdate.feature.timeline.ui.details.TimelineDayDetailPanel
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

/**
 * Per-day timeline detail route. Mirrors the Android Nav3 `TimelineDetail` destination so
 * search day-tap, journal entry day-tap, and any other commonMain caller can route the user
 * to the same panel iOS / desktop already render inline on the home tab.
 *
 * `LocalDate` is serialized as ISO-8601 string for Compose Navigation save-state compatibility
 * on iOS.
 */
@Serializable
data class TimelineDetailRoute(val dateIso: String) : NavKey

fun NavController.navigateToTimelineDay(date: LocalDate) {
    navigate(TimelineDetailRoute(date.toString()))
}

fun NavGraphBuilder.timelineDetailRoute(
    onClose: () -> Unit,
    onOpenLocations: (() -> Unit)? = null,
    onOpenEvent: (eventId: String) -> Unit = {},
    onDecorate: (() -> Unit)? = null,
) {
    composable<TimelineDetailRoute> { entry ->
        val route = entry.toRoute<TimelineDetailRoute>()
        TimelineDetailScreen(
            date = LocalDate.parse(route.dateIso),
            onClose = onClose,
            onOpenLocations = onOpenLocations,
            onOpenEvent = onOpenEvent,
            onDecorate = onDecorate,
        )
    }
}

@Composable
private fun TimelineDetailScreen(
    date: LocalDate,
    onClose: () -> Unit,
    onOpenLocations: (() -> Unit)? = null,
    onOpenEvent: (eventId: String) -> Unit = {},
    onDecorate: (() -> Unit)? = null,
    viewModel: HomeViewModel = koinViewModel(),
) {
    LaunchedEffect(date) {
        viewModel.selectDay(date)
    }
    val uiState by viewModel.uiState.collectAsState()
    uiState.selectedDay?.let { selectedDay ->
        TimelineDayDetailPanel(
            uiState = selectedDay,
            onExit = onClose,
            onOpenEvent = onOpenEvent,
            onOpenLocations = onOpenLocations,
            onDecorate = onDecorate,
        )
    }
}
