package app.logdate.feature.journals.ui

sealed interface JournalDetailUiState {
    data object Loading : JournalDetailUiState
    data class Success(
        val title: String,
        val entries: List<String>,
    ) : JournalDetailUiState

    data class Error(
        val type: String,
        val message: String,
    ) : JournalDetailUiState
}
