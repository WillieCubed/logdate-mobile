package app.logdate.client.remote

import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.feature.editor.ui.camera.CameraFacing
import app.logdate.feature.editor.ui.camera.CameraRemoteCommand
import app.logdate.feature.editor.ui.camera.defaultCameraSelection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

object RemoteCameraSessionController {
    private val commandChannel = Channel<CameraRemoteCommand>(capacity = Channel.BUFFERED)
    private val _cameraSelection = MutableStateFlow(defaultCameraSelection(CameraFacing.BACK))

    val commands = commandChannel.receiveAsFlow()
    val cameraSelection: StateFlow<MediaDeviceSelectionUiState> = _cameraSelection.asStateFlow()

    fun send(command: CameraRemoteCommand): Boolean = commandChannel.trySend(command).isSuccess

    fun updateCameraSelection(selection: MediaDeviceSelectionUiState) {
        _cameraSelection.value = selection
    }
}
