package app.logdate.client.media.device

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SystemControlledAudioRouteRepository : AudioRouteRepository {
    private val _inputDevices = MutableStateFlow(systemControlledSelection(MediaDeviceKind.AUDIO_INPUT))

    private val _outputDevices = MutableStateFlow(systemControlledSelection(MediaDeviceKind.AUDIO_OUTPUT))

    override val inputDevices: StateFlow<MediaDeviceSelectionUiState> = _inputDevices
    override val outputDevices: StateFlow<MediaDeviceSelectionUiState> = _outputDevices

    override fun selectInputDevice(deviceId: String) = Unit

    override fun selectOutputDevice(deviceId: String) = Unit
}
