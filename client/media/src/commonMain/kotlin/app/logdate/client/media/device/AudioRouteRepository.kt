package app.logdate.client.media.device

import kotlinx.coroutines.flow.StateFlow

interface AudioRouteRepository {
    val inputDevices: StateFlow<MediaDeviceSelectionUiState>
    val outputDevices: StateFlow<MediaDeviceSelectionUiState>

    fun selectInputDevice(deviceId: String)

    fun selectOutputDevice(deviceId: String)
}
