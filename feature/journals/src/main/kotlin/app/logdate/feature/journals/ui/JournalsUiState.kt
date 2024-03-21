package app.logdate.feature.journals.ui

import app.logdate.model.Journal

sealed interface JournalsUiState {
    data object Loading : JournalsUiState
    data class Success(
        val journals: List<Journal>
    ) : JournalsUiState
}

data class JournalListItemUiState(
    val data: Journal
)