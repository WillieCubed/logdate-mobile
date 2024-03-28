package app.logdate.feature.timeline.ui

import app.logdate.core.data.notes.JournalNote

sealed interface TimelineUiState {
    data object Loading : TimelineUiState
    data class Success(
        val items: List<JournalNote>
    ) : TimelineUiState
}
