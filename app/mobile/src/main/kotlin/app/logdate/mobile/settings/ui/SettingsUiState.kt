package app.logdate.mobile.settings.ui

import app.logdate.core.datastore.model.UserData

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Loaded(val userData: UserData) : SettingsUiState
}