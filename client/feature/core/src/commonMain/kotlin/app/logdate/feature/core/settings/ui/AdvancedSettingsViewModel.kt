package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.networking.ServerHealthChecker
import app.logdate.feature.core.settings.updates.AppUpdateCheckTrigger
import app.logdate.feature.core.settings.updates.AppUpdateController
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Server preset options for connecting to LogDate servers.
 */
enum class ServerPreset {
    PRODUCTION,
    LOCAL,
    CUSTOM,
}

/**
 * State for server selection in Advanced Settings.
 */
data class ServerSelectionState(
    val selectedPreset: ServerPreset = ServerPreset.PRODUCTION,
    val localServerAddress: String = DefaultLogDateConfigRepository.DEFAULT_LOCAL_SERVER_ADDRESS,
    val customServerUrl: String = "",
    val validationState: ServerValidationState = ServerValidationState.Idle,
)

/** Result of validating the selected server configuration before persisting it. */
sealed class ServerValidationState {
    /** No validation has run for the current selection. */
    data object Idle : ServerValidationState()

    /** A connection test is currently in progress. */
    data object Validating : ServerValidationState()

    /** The selected server responded successfully and reported its version. */
    data class Success(
        val serverVersion: String?,
    ) : ServerValidationState()

    /** The selected server could not be reached or rejected the request. */
    data class Error(
        val message: String,
    ) : ServerValidationState()
}

/**
 * Coordinates advanced settings actions for server configuration and manual app updates.
 *
 * The view model keeps server-editing state local to the screen while exposing the shared
 * app-update state produced by the platform-specific [AppUpdateController].
 */
class AdvancedSettingsViewModel(
    private val serverHealthChecker: ServerHealthChecker,
    private val configRepository: LogDateConfigRepository,
    private val appUpdateController: AppUpdateController,
) : ViewModel() {
    private val _serverSelectionState =
        MutableStateFlow(
            ServerSelectionState(
                localServerAddress = DefaultLogDateConfigRepository.DEFAULT_LOCAL_SERVER_ADDRESS,
            ),
        )

    /** Editable server-selection state shown in the advanced settings form. */
    val serverSelectionState: StateFlow<ServerSelectionState> = _serverSelectionState.asStateFlow()

    /** Play-update status exposed directly from the platform app-update controller. */
    val appUpdateUiState: StateFlow<AppUpdateUiState> = appUpdateController.uiState

    /** Switches the selected server preset and clears any previous validation outcome. */
    fun selectServerPreset(preset: ServerPreset) {
        _serverSelectionState.update {
            it.copy(
                selectedPreset = preset,
                validationState = ServerValidationState.Idle,
            )
        }
    }

    /** Updates the editable local server address without persisting it yet. */
    fun updateLocalServerAddress(address: String) {
        _serverSelectionState.update {
            it.copy(
                localServerAddress = address,
                validationState = ServerValidationState.Idle,
            )
        }
    }

    /** Updates the editable custom server URL without persisting it yet. */
    fun updateCustomServerUrl(url: String) {
        _serverSelectionState.update {
            it.copy(
                customServerUrl = url,
                validationState = ServerValidationState.Idle,
            )
        }
    }

    /**
     * Validates the currently selected server, then persists it when the health check succeeds.
     *
     * Production does not need a live probe because it always points at the default backend URL.
     */
    fun validateAndSaveServer() {
        val currentState = _serverSelectionState.value

        if (currentState.selectedPreset == ServerPreset.PRODUCTION) {
            saveServerConfiguration(DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL)
            return
        }

        val serverUrl =
            when (currentState.selectedPreset) {
                ServerPreset.LOCAL -> {
                    val address = currentState.localServerAddress
                    if (address.startsWith("http")) address else "http://$address"
                }
                ServerPreset.CUSTOM -> currentState.customServerUrl
                ServerPreset.PRODUCTION -> DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL
            }

        if (serverUrl.isBlank()) {
            _serverSelectionState.update {
                it.copy(validationState = ServerValidationState.Error("Server URL cannot be empty"))
            }
            return
        }

        viewModelScope.launch {
            _serverSelectionState.update {
                it.copy(validationState = ServerValidationState.Validating)
            }

            val result = serverHealthChecker.checkServerHealth(serverUrl)

            result.fold(
                onSuccess = { healthInfo ->
                    Napier.i("Server health check succeeded: $healthInfo")
                    _serverSelectionState.update {
                        it.copy(validationState = ServerValidationState.Success(healthInfo.version))
                    }
                    saveServerConfiguration(serverUrl)
                },
                onFailure = { error ->
                    Napier.e("Server health check failed", error)
                    _serverSelectionState.update {
                        it.copy(
                            validationState =
                                ServerValidationState.Error(
                                    error.message ?: "Failed to connect to server",
                                ),
                        )
                    }
                },
            )
        }
    }

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

    private fun saveServerConfiguration(serverUrl: String) {
        viewModelScope.launch {
            try {
                configRepository.updateBackendUrl(serverUrl)

                val currentState = _serverSelectionState.value
                if (currentState.selectedPreset == ServerPreset.LOCAL) {
                    configRepository.updateLocalServerAddress(currentState.localServerAddress)
                }

                Napier.i("Server configuration saved: $serverUrl")
            } catch (e: Exception) {
                Napier.e("Failed to save server configuration", e)
            }
        }
    }
}
