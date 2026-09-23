package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.feature.core.settings.updates.AppUpdateCheckTrigger
import app.logdate.feature.core.settings.updates.AppUpdateController
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Runs manual app update checks from `Settings > Advanced`, exposing the state produced by the
 * platform-specific [AppUpdateController].
 */
class AdvancedSettingsViewModel(
    private val appUpdateController: AppUpdateController,
) : ViewModel() {
    /** Play-update status exposed directly from the platform app-update controller. */
    val appUpdateUiState: StateFlow<AppUpdateUiState> = appUpdateController.uiState

    /** Starts a user-initiated Play update check from `Settings > Advanced`. */
    fun checkForAppUpdates() {
        viewModelScope.launch {
            appUpdateController.checkForUpdates(AppUpdateCheckTrigger.Manual)
        }
    }

    /** Requests installation of a flexible update that has already been downloaded. */
    fun completeAppUpdate() {
        viewModelScope.launch {
            appUpdateController.completeUpdate()
        }
    }
}
