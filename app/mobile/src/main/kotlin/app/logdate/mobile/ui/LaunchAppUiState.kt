package app.logdate.mobile.ui

sealed interface LaunchAppUiState {
    data object Loading : LaunchAppUiState
    data class Loaded(val isOnboarded: Boolean) : LaunchAppUiState
}