package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.feature.core.settings.updates.AppUpdateCheckTrigger
import app.logdate.feature.core.settings.updates.AppUpdateController
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exposes app updates and the saved developer-mode choice in About LogDate.
 */
class AdvancedSettingsViewModel(
    private val appUpdateController: AppUpdateController,
    private val preferences: LogdatePreferencesDataSource,
) : ViewModel() {
    val developerModeEnabled: StateFlow<Boolean> =
        preferences.observeDeveloperModeEnabled().stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false,
        )
    private val developerModeWriteFailed = MutableStateFlow(false)
    val developerModeSaveFailed: StateFlow<Boolean> = developerModeWriteFailed.asStateFlow()

    fun setDeveloperModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferences.setDeveloperModeEnabled(enabled)
                developerModeWriteFailed.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Could not save developer mode preference", e)
                developerModeWriteFailed.value = true
            }
        }
    }

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
