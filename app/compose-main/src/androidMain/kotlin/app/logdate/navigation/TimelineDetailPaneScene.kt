package app.logdate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.feature.core.main.HomeViewModel
import app.logdate.feature.timeline.ui.details.TimelineDayDetailPanel
import app.logdate.navigation.routes.TimelineDetailPlaceholder
import org.koin.compose.viewmodel.koinViewModel

/**
 * Scene that displays detailed information about a specific day in the timeline.
 * This component uses real data from the HomeViewModel rather than mock data.
 *
 * @param viewModel The view model containing timeline data
 * @param onClose Callback to close the detail view
 * @param onNewEntry Callback to create a new entry
 * @param onOpenSettings Callback to open settings
 */
@Composable
fun TimelineDetailPaneScene(
    viewModel: HomeViewModel = koinViewModel(),
    onClose: () -> Unit = {},
    onNewEntry: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    // We'll use real data from the view model rather than mock data
    // The uiState.selectedDay property will contain the day information 
    // if a day has been selected
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    uiState.selectedDay?.let { selectedDay ->
        TimelineDayDetailPanel(
            uiState = selectedDay,
            onExit = onClose,
        )
    } ?: TimelineDetailPlaceholder()
}