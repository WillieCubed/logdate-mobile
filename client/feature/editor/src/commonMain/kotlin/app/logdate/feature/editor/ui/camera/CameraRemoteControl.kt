package app.logdate.feature.editor.ui.camera

import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class CameraRemoteControl(
    val commands: Flow<CameraRemoteCommand> = emptyFlow(),
    val autoAcceptCapturedMedia: Boolean = false,
    val onCameraSelectionChanged: (MediaDeviceSelectionUiState) -> Unit = {},
    val onCaptureCompleted: (String, CapturedMediaType) -> Unit = { _, _ -> },
    val onCaptureFailed: (String) -> Unit = {},
) {
    companion object {
        val None = CameraRemoteControl()
    }
}

sealed interface CameraRemoteCommand {
    data object Capture : CameraRemoteCommand

    data object SwitchBuiltInCamera : CameraRemoteCommand

    data class SelectCameraCategory(
        val category: MediaDeviceCategory,
    ) : CameraRemoteCommand

    data class SelectCameraDevice(
        val deviceId: String,
    ) : CameraRemoteCommand

    data object Close : CameraRemoteCommand
}
