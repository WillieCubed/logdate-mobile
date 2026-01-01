package app.logdate.feature.editor.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for managing camera capture operations.
 * Handles photo and video capture, camera switching, and state management.
 */
class CameraViewModel(
    private val cameraCaptureManager: CameraCaptureManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            cameraCaptureManager.state.collect { captureState ->
                _uiState.update { current ->
                    current.copy(
                        isPreviewActive = captureState.isPreviewActive,
                        cameraFacing = captureState.cameraFacing,
                        captureMode = captureState.captureMode,
                        isRecording = captureState.isRecording,
                        recordingDurationMs = captureState.recordingDurationMs,
                        capturedMediaUri = captureState.lastCapturedUri,
                        error = captureState.error?.toErrorMessage()
                    )
                }
            }
        }
    }

    /**
     * Starts the camera preview with the default back-facing camera.
     */
    fun startPreview() {
        viewModelScope.launch {
            Napier.d("CameraViewModel: Starting preview")
            cameraCaptureManager.startPreview()
        }
    }

    /**
     * Stops the camera preview.
     */
    fun stopPreview() {
        viewModelScope.launch {
            Napier.d("CameraViewModel: Stopping preview")
            cameraCaptureManager.stopPreview()
        }
    }

    /**
     * Switches between front and back cameras.
     */
    fun switchCamera() {
        viewModelScope.launch {
            Napier.d("CameraViewModel: Switching camera")
            cameraCaptureManager.switchCamera()
        }
    }

    /**
     * Sets the capture mode to photo or video.
     */
    fun setCaptureMode(mode: CaptureMode) {
        Napier.d("CameraViewModel: Setting capture mode to $mode")
        cameraCaptureManager.setCaptureMode(mode)
    }

    /**
     * Captures a photo or toggles video recording based on current mode.
     */
    fun capture() {
        viewModelScope.launch {
            when (_uiState.value.captureMode) {
                CaptureMode.PHOTO -> capturePhoto()
                CaptureMode.VIDEO -> toggleVideoRecording()
            }
        }
    }

    /**
     * Captures a photo.
     */
    private suspend fun capturePhoto() {
        Napier.d("CameraViewModel: Capturing photo")
        _uiState.update { it.copy(isCapturing = true) }

        val uri = cameraCaptureManager.capturePhoto()

        _uiState.update {
            it.copy(
                isCapturing = false,
                capturedMediaUri = uri,
                capturedMediaType = if (uri != null) CapturedMediaType.PHOTO else null
            )
        }

        if (uri != null) {
            Napier.d("CameraViewModel: Photo captured at $uri")
        } else {
            Napier.e("CameraViewModel: Photo capture failed")
        }
    }

    /**
     * Toggles video recording on/off.
     */
    private suspend fun toggleVideoRecording() {
        if (_uiState.value.isRecording) {
            Napier.d("CameraViewModel: Stopping video recording")
            val uri = cameraCaptureManager.stopVideoRecording()
            _uiState.update {
                it.copy(
                    capturedMediaUri = uri,
                    capturedMediaType = if (uri != null) CapturedMediaType.VIDEO else null
                )
            }
        } else {
            Napier.d("CameraViewModel: Starting video recording")
            cameraCaptureManager.startVideoRecording()
        }
    }

    /**
     * Clears the captured media URI and resets capture state.
     */
    fun clearCapturedMedia() {
        _uiState.update {
            it.copy(
                capturedMediaUri = null,
                capturedMediaType = null,
                error = null
            )
        }
    }

    /**
     * Clears any error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        cameraCaptureManager.release()
        Napier.d("CameraViewModel: Released camera resources")
    }
}

/**
 * UI state for the camera capture screen.
 */
data class CameraUiState(
    val isPreviewActive: Boolean = false,
    val cameraFacing: CameraFacing = CameraFacing.BACK,
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val isCapturing: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val capturedMediaUri: String? = null,
    val capturedMediaType: CapturedMediaType? = null,
    val error: String? = null
) {
    /**
     * Returns a formatted duration string for video recording (MM:SS).
     */
    val formattedDuration: String
        get() {
            val seconds = (recordingDurationMs / 1000) % 60
            val minutes = (recordingDurationMs / 1000) / 60
            return "%02d:%02d".format(minutes, seconds)
        }
}

/**
 * Converts a [CameraCaptureError] to a user-friendly error message.
 */
private fun CameraCaptureError.toErrorMessage(): String = when (this) {
    is CameraCaptureError.CameraNotAvailable -> "Camera is not available"
    is CameraCaptureError.PermissionDenied -> "Camera permission denied"
    is CameraCaptureError.CaptureFailed -> "Failed to capture photo"
    is CameraCaptureError.RecordingFailed -> "Failed to record video"
    is CameraCaptureError.Unknown -> message
}
