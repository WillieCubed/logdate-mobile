package app.logdate.feature.rewind.ui

sealed interface RewindDetailUiState {
    data object Loading : RewindDetailUiState
    data class Success(
        val entries: List<String>,
    ) : RewindDetailUiState

    data class Error(
        val type: String,
        val message: String,
    ) : RewindDetailUiState
}
