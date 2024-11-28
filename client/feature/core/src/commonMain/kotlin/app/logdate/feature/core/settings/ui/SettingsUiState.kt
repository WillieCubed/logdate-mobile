package app.logdate.feature.core.settings.ui

import app.logdate.shared.model.user.UserData


sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Loaded(val userData: UserData) : SettingsUiState
}