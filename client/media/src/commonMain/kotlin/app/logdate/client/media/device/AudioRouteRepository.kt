package app.logdate.client.media.device

import app.logdate.client.text.UiText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

interface AudioRouteRepository {
    val inputDevices: StateFlow<MediaDeviceSelectionUiState>
    val outputDevices: StateFlow<MediaDeviceSelectionUiState>

    val requestedInputDeviceIds: Flow<String?>
        get() = inputDevices.map { it.selectedDeviceId }

    fun updateRecordingInputDevice(
        deviceId: String?,
        isRecording: Boolean,
        error: UiText? = null,
    ) = Unit

    fun selectInputDevice(deviceId: String)

    fun selectOutputDevice(deviceId: String)
}
