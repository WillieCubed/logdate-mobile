package app.logdate.feature.core.settings.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.device.identity.DefaultDeviceManager
import app.logdate.client.device.identity.safeDeviceDisplayName
import app.logdate.client.device.models.DeviceInfo
import app.logdate.client.device.models.DevicePlatform
import app.logdate.client.sync.peer.devices.LinkedDeviceRemoval
import app.logdate.client.sync.peer.devices.LinkedDeviceSession
import app.logdate.client.sync.peer.devices.LinkedDevicesLoad
import app.logdate.client.sync.peer.devices.LinkedDevicesRepository
import app.logdate.shared.model.devices.DeviceLinkPlatform
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * ViewModel for managing devices screen.
 */
class DevicesViewModel(
    private val deviceManager: DefaultDeviceManager,
    private val linkedDevices: LinkedDevicesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()
    private var devicesJob: Job? = null
    private var linkedJob: Job? = null
    private var removalJob: Job? = null
    private var linkedSessions: List<LinkedDeviceSession> = emptyList()

    /**
     * Loads devices associated with the current account.
     */
    fun loadDevices() {
        loadLinkedDevices()
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

    /**
     * Lists the devices linked to the account with their own sessions, when the account can link
     * devices. A running removal lists them itself once it finishes.
     */
    fun loadLinkedDevices() {
        if (removalJob?.isActive == true) return
        linkedJob?.cancel()
        linkedJob = viewModelScope.launch { refreshLinkedDevices(notice = null) }
    }

    /**
     * Removes linked device [id]: its session ends, and a watch this phone linked is also asked to
     * erase its data. The removal's steps run to the end even if the page closes, so a watch is never
     * left revoked without being asked to erase.
     */
    fun removeLinkedDevice(id: String) {
        if (removalJob?.isActive == true) return
        val listed = _uiState.value.linked as? LinkedDevicesUiState.Listed ?: return
        val device = linkedSessions.firstOrNull { it.id == id } ?: return
        linkedJob?.cancel()
        setLinked(listed.copy(removingId = id, notice = null))
        removalJob =
            viewModelScope.launch {
                when (val result = withContext(NonCancellable) { linkedDevices.remove(device) }) {
                    LinkedDeviceRemoval.Removed -> refreshLinkedDevices(notice = null)
                    LinkedDeviceRemoval.RemovedWillWipeOnNextConnect ->
                        refreshLinkedDevices(notice = LinkedDeviceNotice.ErasesOnNextConnect(device.name))
                    is LinkedDeviceRemoval.Failed -> {
                        Napier.w("Linked device ${device.id} was not removed: ${result.failure}")
                        val notice = LinkedDeviceNotice.RemoveFailed(device.id, device.name, result.failure)
                        setLinked(listed.copy(removingId = null, notice = notice))
                    }
                }
            }
    }

    fun dismissLinkedDeviceNotice() {
        val listed = _uiState.value.linked as? LinkedDevicesUiState.Listed ?: return
        setLinked(listed.copy(notice = null))
    }

    private suspend fun refreshLinkedDevices(notice: LinkedDeviceNotice?) {
        if (!linkedDevices.available()) {
            setLinked(LinkedDevicesUiState.Unavailable)
            return
        }
        if (_uiState.value.linked !is LinkedDevicesUiState.Listed) setLinked(LinkedDevicesUiState.Loading)
        val state =
            when (val loaded = linkedDevices.load()) {
                LinkedDevicesLoad.Unavailable -> LinkedDevicesUiState.Unavailable
                is LinkedDevicesLoad.Failed -> {
                    Napier.w("Linked devices could not be listed: ${loaded.failure}")
                    LinkedDevicesUiState.Failed(loaded.failure)
                }
                is LinkedDevicesLoad.Loaded -> {
                    linkedSessions = loaded.devices
                    LinkedDevicesUiState.Listed(loaded.devices.map { it.toUiState() }, notice = notice)
                }
            }
        setLinked(state)
    }

    private fun setLinked(state: LinkedDevicesUiState) {
        _uiState.update { it.copy(linked = state) }
    }

    private fun LinkedDeviceSession.toUiState(): LinkedDeviceUiState =
        LinkedDeviceUiState(
            id = id,
            name = safeDeviceDisplayName(name, "${linkedPlatformName(platform)} device"),
            platformName = linkedPlatformName(platform),
            linkedAtFormatted = formatDeviceLastActive(linkedAt),
            lastSeenFormatted = formatDeviceLastActive(lastSeenAt),
            pairedWatch = pairedWatch,
            erasesData = platform == DeviceLinkPlatform.WEAR_OS,
        )

    private fun linkedPlatformName(platform: String): String =
        when (platform) {
            DeviceLinkPlatform.WEAR_OS -> getPlatformName(DevicePlatform.WEAR_OS)
            else -> getPlatformName(DevicePlatform.UNKNOWN)
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
            DevicePlatform.WEAR_OS -> "Wear OS"
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
    val linked: LinkedDevicesUiState = LinkedDevicesUiState.Unavailable,
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
