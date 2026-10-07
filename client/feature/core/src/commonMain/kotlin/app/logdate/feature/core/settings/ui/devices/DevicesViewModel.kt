package app.logdate.feature.core.settings.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.device.identity.DefaultDeviceManager
import app.logdate.client.device.identity.safeDeviceDisplayName
import app.logdate.client.device.models.DeviceInfo
import app.logdate.client.device.models.DevicePlatform
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * ViewModel for managing devices screen.
 */
class DevicesViewModel(
    private val deviceManager: DefaultDeviceManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()
    private var devicesJob: Job? = null

    /**
     * Loads devices associated with the current account.
     */
    fun loadDevices() {
        devicesJob?.cancel()
        _uiState.update { it.copy(isLoading = it.devices.isEmpty()) }

        devicesJob =
            viewModelScope.launch {
                try {
                    // Get current device info
                    val currentDevice = deviceManager.getCurrentDeviceInfo()
                    launch { deviceManager.registerWithCloud() }

                    // Collect associated devices
                    deviceManager.getAssociatedDevices().collect { devices ->
                        val registeredCurrent = devices.firstOrNull { it.id == currentDevice.id } ?: currentDevice
                        val allDevices = (listOf(registeredCurrent) + devices).distinctBy { it.id }
                        val deviceUiStates = allDevices.map { it.toUiState(it.id == currentDevice.id) }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                devices = deviceUiStates,
                            )
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    Napier.e("Failed to load devices", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load devices: ${e.message}",
                        )
                    }
                }
            }
    }

    fun stopObserving() {
        devicesJob?.cancel()
        devicesJob = null
    }

    /**
     * Renames the current device.
     */
    fun renameDevice(newName: String) {
        viewModelScope.launch {
            try {
                deviceManager.renameDevice(newName)
                loadDevices() // Reload devices to update UI
            } catch (e: Exception) {
                Napier.e("Failed to rename device", e)
                _uiState.update {
                    it.copy(error = "Failed to rename device: ${e.message}")
                }
            }
        }
    }

    /**
     * Removes a device from the account.
     */
    fun removeDevice(deviceId: Uuid) {
        viewModelScope.launch {
            try {
                val result = deviceManager.removeDevice(deviceId)
                if (result.isFailure) {
                    val message = result.exceptionOrNull()?.message ?: "Unknown error"
                    _uiState.update { it.copy(error = "Failed to remove device: $message") }
                    return@launch
                }
                loadDevices() // Reload devices to update UI
            } catch (e: Exception) {
                Napier.e("Failed to remove device", e)
                _uiState.update {
                    it.copy(error = "Failed to remove device: ${e.message}")
                }
            }
        }
    }

    /**
     * Resets the current device ID.
     */
    fun resetDeviceId() {
        viewModelScope.launch {
            try {
                deviceManager.refreshDeviceId()
                loadDevices() // Reload devices to update UI
            } catch (e: Exception) {
                Napier.e("Failed to reset device ID", e)
                _uiState.update {
                    it.copy(error = "Failed to reset device ID: ${e.message}")
                }
            }
        }
    }

    /**
     * Converts a DeviceInfo to a DeviceInfoUiState.
     */
    private fun DeviceInfo.toUiState(isCurrentDevice: Boolean): DeviceInfoUiState =
        DeviceInfoUiState(
            id = id,
            name = safeDeviceDisplayName(name, "${getPlatformName(platform)} device"),
            platformName = getPlatformName(platform),
            lastActiveFormatted = formatDeviceLastActive(lastActive),
            appVersion = appVersion,
            isCurrentDevice = isCurrentDevice,
        )

    /**
     * Gets a user-friendly platform name.
     */
    private fun getPlatformName(platform: DevicePlatform): String =
        when (platform) {
            DevicePlatform.ANDROID -> "Android"
            DevicePlatform.IOS -> "iOS"
            DevicePlatform.MACOS -> "macOS"
            DevicePlatform.WINDOWS -> "Windows"
            DevicePlatform.LINUX -> "Linux"
            DevicePlatform.WEB -> "Web"
            DevicePlatform.UNKNOWN -> "Unknown"
        }
}

/**
 * UI state for the devices screen.
 */
data class DevicesUiState(
    val isLoading: Boolean = false,
    val devices: List<DeviceInfoUiState> = emptyList(),
    val error: String? = null,
)

/**
 * UI state for a device.
 */
data class DeviceInfoUiState(
    val id: Uuid,
    val name: String,
    val platformName: String,
    val lastActiveFormatted: String,
    val appVersion: String,
    val isCurrentDevice: Boolean,
)
