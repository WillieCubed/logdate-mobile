package app.logdate.wear.presentation.camera

import app.logdate.client.media.device.DefaultMediaDevices
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface RemoteCameraDeviceStore {
    val cameraSelection: StateFlow<MediaDeviceSelectionUiState>
}

object WearRemoteCameraDeviceStore : RemoteCameraDeviceStore {
    private val _cameraSelection = MutableStateFlow(defaultRemoteCameraSelection())
    override val cameraSelection: StateFlow<MediaDeviceSelectionUiState> = _cameraSelection.asStateFlow()

    fun update(selection: MediaDeviceSelectionUiState) {
        if (selection.devices.isEmpty()) return
        _cameraSelection.value = selection
    }
}

fun defaultRemoteCameraSelection(): MediaDeviceSelectionUiState =
    MediaDeviceSelectionUiState(
        kind = MediaDeviceKind.CAMERA,
        devices = listOf(DefaultMediaDevices.backCamera, DefaultMediaDevices.frontCamera),
        selectedDeviceId = DefaultMediaDevices.backCamera.id,
    )
