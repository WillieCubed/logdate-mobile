package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.networking.ServerHealthChecker
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
    CUSTOM
}

/**
 * State for server selection in Advanced Settings.
 */
data class ServerSelectionState(
    val selectedPreset: ServerPreset = ServerPreset.PRODUCTION,
    val localServerAddress: String = DefaultLogDateConfigRepository.DEFAULT_LOCAL_SERVER_ADDRESS,
    val customServerUrl: String = "",
    val validationState: ServerValidationState = ServerValidationState.Idle
)

sealed class ServerValidationState {
    data object Idle : ServerValidationState()
    data object Validating : ServerValidationState()
    data class Success(val serverVersion: String?) : ServerValidationState()
    data class Error(val message: String) : ServerValidationState()
}

class AdvancedSettingsViewModel(
    private val serverHealthChecker: ServerHealthChecker,
    private val configRepository: LogDateConfigRepository,
) : ViewModel() {

    private val _serverSelectionState = MutableStateFlow(
        ServerSelectionState(
            localServerAddress = DefaultLogDateConfigRepository.DEFAULT_LOCAL_SERVER_ADDRESS
        )
    )
    val serverSelectionState: StateFlow<ServerSelectionState> = _serverSelectionState.asStateFlow()

    fun selectServerPreset(preset: ServerPreset) {
        _serverSelectionState.update {
            it.copy(
                selectedPreset = preset,
                validationState = ServerValidationState.Idle
            )
        }
    }

    fun updateLocalServerAddress(address: String) {
        _serverSelectionState.update {
            it.copy(
                localServerAddress = address,
                validationState = ServerValidationState.Idle
            )
        }
    }

    fun updateCustomServerUrl(url: String) {
        _serverSelectionState.update {
            it.copy(
                customServerUrl = url,
                validationState = ServerValidationState.Idle
            )
        }
    }

    fun validateAndSaveServer() {
        val currentState = _serverSelectionState.value

        if (currentState.selectedPreset == ServerPreset.PRODUCTION) {
            saveServerConfiguration(DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL)
            return
        }

        val serverUrl = when (currentState.selectedPreset) {
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
                            validationState = ServerValidationState.Error(
                                error.message ?: "Failed to connect to server"
                            )
                        )
                    }
                }
            )
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
