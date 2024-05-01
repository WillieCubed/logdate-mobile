package app.logdate.feature.timeline.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun TimelineRoute(
    onOpenTimelineItem: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    TimelineScreen(state, onOpenTimelineItem, modifier)
}

@Composable
internal fun TimelineScreen(
    state: TimelineUiState,
    onOpenTimelineItem: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        TimelineUiState.Loading -> TimelineLoadingPlaceholder(modifier)
        is TimelineUiState.Success -> Content(state, onOpenTimelineItem, modifier)
    }
}

@Composable
internal fun Content(
    state: TimelineUiState.Success,
    onItemSelected: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Timeline(state.items, onItemSelected, modifier = modifier)
}


