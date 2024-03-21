package app.logdate.feature.library.ui

import app.logdate.model.LibraryItem

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(
        val data: List<LibraryItem>
    ) : LibraryUiState
}
