package app.logdate.feature.timeline.ui

import app.logdate.model.TimelineItem

sealed interface TimelineUiState {
    data object Loading : TimelineUiState
    data class Success(
        val items: List<TimelineItem>
    ) : TimelineUiState
}
