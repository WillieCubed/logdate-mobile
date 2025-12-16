package app.logdate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.feature.core.main.HomeViewModel
import app.logdate.feature.location.timeline.ui.LocationTimelineBottomSheet
import app.logdate.ui.timeline.TimelinePane
import app.logdate.ui.timeline.TimelineUiState
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen showing the timeline view that displays all journal entries.
 *
 * @param onNewEntry Callback for when user wants to create a new entry
 * @param onOpenDay Callback for when user selects a specific day to view details
 * @param onOpenSettings Callback for when the user clicks the settings icon
 * @param onOpenSearch Callback for when the user clicks the search icon
 * @param viewModel HomeViewModel that contains timeline data
 */
@Composable
fun TimelinePaneScreen(
    onNewEntry: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLocationTimeline by remember { mutableStateOf(false) }
    
    // Location timeline bottom sheet
    LocationTimelineBottomSheet(
        isVisible = showLocationTimeline,
        onDismiss = { showLocationTimeline = false }
    )
    
    TimelinePane(
        uiState = TimelineUiState(items = uiState.items),
        onNewEntry = onNewEntry,
        onShareMemory = {},
        onOpenDay = { date ->
            // First select the day in the ViewModel
            viewModel.selectDay(date)
            // Then fetch notes for that day
            viewModel.fetchNotesForDate(date)
            // Finally navigate to the detail screen
            onOpenDay(date)
        },
        onSearchClick = onOpenSearch,
        onProfileClick = onOpenSettings,
        onHistoryClick = { showLocationTimeline = true },
    )
}