package app.logdate.feature.editor.ui.camera

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop capability fallback of [CameraCaptureManager].
 * Camera capture is not yet implemented on Desktop.
 */
class DesktopCameraCaptureManager : CameraCaptureManager {
    private val _state = MutableStateFlow(CameraCaptureState())
    override val state: StateFlow<CameraCaptureState> = _state.asStateFlow()

    override suspend fun startPreview(facing: CameraFacing) {
        _state.value =
            _state.value.copy(
                error = CameraCaptureError.CameraNotAvailable,
            )
    }

    override suspend fun stopPreview() {
        // No-op
    }

    override suspend fun capturePhoto(): String? = null

    override suspend fun startVideoRecording() {
        // No-op
    }

    override suspend fun stopVideoRecording(): String? = null

    override suspend fun switchCamera() {
        // No-op
    }

    override fun setCaptureMode(mode: CaptureMode) {
        _state.value = _state.value.copy(captureMode = mode)
    }

    override fun setAspectRatio(ratio: CameraAspectRatio) {
        _state.value = _state.value.copy(aspectRatio = ratio)
    }

    override fun clearCapturedUri() {
        _state.value = _state.value.copy(lastCapturedUri = null)
    }

    override fun release() {
        // No-op
    }
}
