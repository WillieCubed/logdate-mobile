package app.logdate.feature.journals.ui

import app.logdate.shared.model.Journal

data class JournalsOverviewUiState(
    val journals: List<JournalListItemUiState> = emptyList(),
)

data class JournalListItemUiState(
    val data: Journal,
)