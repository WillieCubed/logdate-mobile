package app.logdate.feature.journals.ui

import app.logdate.shared.model.Journal

data class JournalsOverviewUiState(
    val journals: List<JournalListItemUiState> = emptyList(),
)

sealed interface JournalListItemUiState {
    data class ExistingJournal(
        val data: Journal,
    ) : JournalListItemUiState
    
    data object CreateJournalPlaceholder : JournalListItemUiState
}