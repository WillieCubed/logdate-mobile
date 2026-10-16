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
import app.logdate.client.text.UiText
import app.logdate.client.text.toUiText
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
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.devices_error_load
import logdate.client.feature.core.generated.resources.devices_error_remove
import logdate.client.feature.core.generated.resources.devices_error_rename
import logdate.client.feature.core.generated.resources.devices_error_reset_id
import logdate.client.feature.core.generated.resources.devices_error_unknown
import logdate.client.feature.core.generated.resources.devices_fallback_name
import logdate.client.feature.core.generated.resources.devices_last_active_unknown
import logdate.client.feature.core.generated.resources.devices_platform_android
import logdate.client.feature.core.generated.resources.devices_platform_ios
import logdate.client.feature.core.generated.resources.devices_platform_linux
import logdate.client.feature.core.generated.resources.devices_platform_macos
import logdate.client.feature.core.generated.resources.devices_platform_unknown
import logdate.client.feature.core.generated.resources.devices_platform_wear_os
import logdate.client.feature.core.generated.resources.devices_platform_web
import logdate.client.feature.core.generated.resources.devices_platform_windows
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
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
                            error = Res.string.devices_error_load.toUiText(e.message.toString()),
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

    private fun LinkedDeviceSession.toUiState(): LinkedDeviceUiState {
        val platformName = linkedPlatformName(platform)
        return LinkedDeviceUiState(
            id = id,
            name = deviceName(name, platformName),
            platformName = platformName,
            linkedAtFormatted = lastActiveText(linkedAt),
            lastSeenFormatted = lastActiveText(lastSeenAt),
            pairedWatch = pairedWatch,
            erasesData = platform == DeviceLinkPlatform.WEAR_OS,
        )
    }

    private fun linkedPlatformName(platform: String): StringResource =
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
                    it.copy(error = Res.string.devices_error_rename.toUiText(e.message.toString()))
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
                    val reason = result.exceptionOrNull()?.message?.toUiText() ?: Res.string.devices_error_unknown.toUiText()
                    _uiState.update { it.copy(error = Res.string.devices_error_remove.toUiText(reason)) }
                    return@launch
                }
                loadDevices() // Reload devices to update UI
            } catch (e: Exception) {
                Napier.e("Failed to remove device", e)
                _uiState.update {
                    it.copy(error = Res.string.devices_error_remove.toUiText(e.message.toString()))
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
                    it.copy(error = Res.string.devices_error_reset_id.toUiText(e.message.toString()))
                }
            }
        }
    }

    /**
     * Converts a DeviceInfo to a DeviceInfoUiState.
     */
    private fun DeviceInfo.toUiState(isCurrentDevice: Boolean): DeviceInfoUiState {
        val platformName = getPlatformName(platform)
        return DeviceInfoUiState(
            id = id,
            name = deviceName(name, platformName),
            platformName = platformName,
            lastActiveFormatted = lastActiveText(lastActive),
            appVersion = appVersion,
            isCurrentDevice = isCurrentDevice,
        )
    }

    /**
     * Gets a user-friendly platform name.
     */
    private fun deviceName(
        name: String,
        platformName: StringResource,
    ): UiText =
        safeDeviceDisplayName(name, "").takeIf { it.isNotBlank() }?.toUiText()
            ?: Res.string.devices_fallback_name.toUiText(platformName.toUiText())

    private fun lastActiveText(timestamp: Instant): UiText =
        formatDeviceLastActive(timestamp)?.toUiText() ?: Res.string.devices_last_active_unknown.toUiText()

    private fun getPlatformName(platform: DevicePlatform): StringResource =
        when (platform) {
            DevicePlatform.ANDROID -> Res.string.devices_platform_android
            DevicePlatform.WEAR_OS -> Res.string.devices_platform_wear_os
            DevicePlatform.IOS -> Res.string.devices_platform_ios
            DevicePlatform.MACOS -> Res.string.devices_platform_macos
            DevicePlatform.WINDOWS -> Res.string.devices_platform_windows
            DevicePlatform.LINUX -> Res.string.devices_platform_linux
            DevicePlatform.WEB -> Res.string.devices_platform_web
            DevicePlatform.UNKNOWN -> Res.string.devices_platform_unknown
        }
}

/**
 * UI state for the devices screen.
 */
data class DevicesUiState(
    val isLoading: Boolean = false,
    val devices: List<DeviceInfoUiState> = emptyList(),
    val error: UiText? = null,
    val linked: LinkedDevicesUiState = LinkedDevicesUiState.Unavailable,
)

/**
 * UI state for a device.
 */
data class DeviceInfoUiState(
    val id: Uuid,
    val name: UiText,
    val platformName: StringResource,
    val lastActiveFormatted: UiText,
    val appVersion: String,
    val isCurrentDevice: Boolean,
)
