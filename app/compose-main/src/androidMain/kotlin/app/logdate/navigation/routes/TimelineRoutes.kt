package app.logdate.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import app.logdate.feature.core.main.HomeViewModel
import app.logdate.feature.timeline.ui.details.TimelineDayDetailPanel
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

fun MainAppNavigator.openTimeline() {
    backStack.add(TimelineListRoute)
}

fun MainAppNavigator.openTimelineDetail(
    day: LocalDate,
) {
    // Add the day to the navigation backstack
    backStack.add(TimelineDetail(day))
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderBuilder<NavKey>.timelineRoutes(
    openEntryEditor: () -> Unit,
    onOpenTimelineDetail: (day: LocalDate) -> Unit,
    onCloseTimelineDetail: () -> Unit,
    onOpenSettings: () -> Unit, // This will open the main settings overview
    homeViewModel: HomeViewModel,
) {
    // The home screen
    entry<TimelineListRoute>(
        metadata = HomeScene.homeScene() // Mark this as a home scene entry
    ) { _ ->
        TimelinePaneScreen(
            onNewEntry = openEntryEditor,
            onOpenDay = { day ->
                homeViewModel.selectDay(day)
                onOpenTimelineDetail(day)
            },
            onOpenSettings = onOpenSettings,
            viewModel = homeViewModel
        )
    }
    entry<TimelineDetail> { route ->
        // Explicitly fetch notes for the selected day when entering this screen
        LaunchedEffect(route.day) {
            homeViewModel.selectDay(route.day)
            homeViewModel.fetchNotesForDate(route.day)
        }
        
        TimelineDetailScreen(
            onClose = onCloseTimelineDetail,
            viewModel = homeViewModel,
        )
    }
}

@Composable
fun TimelineDetailScreen(
    onClose: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Log the current UI state for debugging
    println("TimelineDetailScreen UI state: selectedDay=${uiState.selectedDay}, selectedItem=${uiState.selectedItem}")
    
    // Use a safe fallback if selectedDay is null
    uiState.selectedDay?.let { selectedDay ->
        TimelineDayDetailPanel(
            uiState = selectedDay,
            onExit = onClose,
        )
    } ?: TimelineDetailPlaceholder()
}

@Composable
fun TimelineDetailPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Select an entry to view details",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxSize(),
            textAlign = TextAlign.Center
        )
    }
}