package app.logdate.feature.editor.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.feature.editor.ui.formatMediaDuration
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for managing camera capture operations.
 * Handles photo and video capture, camera switching, and state management.
 *
 * Camera preview lifecycle is serialized through [previewJob] to prevent
 * stop/start races when transitioning between capture and review states.
 */
class CameraViewModel(
    private val cameraCaptureManager: CameraCaptureManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            cameraCaptureManager.state.collect { captureState ->
                _uiState.update { current ->
                    current.copy(
                        isPreviewActive = captureState.isPreviewActive,
                        cameraFacing = captureState.cameraFacing,
                        captureMode = captureState.captureMode,
                        aspectRatio = captureState.aspectRatio,
                        isRecording = captureState.isRecording,
                        recordingDurationMs = captureState.recordingDurationMs,
                        capturedMediaUri = captureState.lastCapturedUri,
                        error = captureState.error?.toErrorMessage(),
                        cameraSelection = captureState.cameraSelection,
                    )
                }
            }
        }
    }

    /**
     * Starts the camera preview with the default back-facing camera.
     * Cancels any in-flight stop operation first, then stops and restarts cleanly.
     */
    fun startPreview() {
        // Cancel any pending stop/start to avoid races
        previewJob?.cancel()
        previewJob =
            viewModelScope.launch {
                // Stop first in case previous session left the camera bound
                cameraCaptureManager.stopPreview()
                cameraCaptureManager.startPreview()
            }
    }

    /**
     * Returns the camera capture manager for platform-specific operations.
     */
    fun getCaptureManager(): CameraCaptureManager = cameraCaptureManager

    /**
     * Stops the camera preview.
     * Cancels any in-flight start operation first.
     */
    fun stopPreview() {
        previewJob?.cancel()
        previewJob =
            viewModelScope.launch {
                cameraCaptureManager.stopPreview()
            }
    }

    /**
     * Switches between front and back cameras.
     */
    suspend fun switchCamera() {
        previewJob?.cancelAndJoin()
        previewJob =
            viewModelScope.launch {
                cameraCaptureManager.switchCamera()
            }
        previewJob?.join()
    }

    suspend fun selectCameraDevice(deviceId: String) {
        previewJob?.cancelAndJoin()
        previewJob =
            viewModelScope.launch {
                cameraCaptureManager.selectCameraDevice(deviceId)
            }
        previewJob?.join()
    }

    /**
     * Sets the capture mode to photo or video.
     */
    fun setCaptureMode(mode: CaptureMode) {
        cameraCaptureManager.setCaptureMode(mode)
    }

    /**
     * Sets the aspect ratio for the camera viewfinder and capture.
     */
    fun setAspectRatio(ratio: CameraAspectRatio) {
        cameraCaptureManager.setAspectRatio(ratio)
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
        _uiState.update { it.copy(isCapturing = true) }

        val uri = cameraCaptureManager.capturePhoto()

        _uiState.update {
            it.copy(
                isCapturing = false,
                capturedMediaUri = uri,
                capturedMediaType = if (uri != null) CapturedMediaType.PHOTO else null,
            )
        }

        if (uri == null) {
            Napier.e("CameraViewModel: Photo capture failed")
        }
    }

    /**
     * Toggles video recording on/off.
     */
    private suspend fun toggleVideoRecording() {
        if (_uiState.value.isRecording) {
            val uri = cameraCaptureManager.stopVideoRecording()
            _uiState.update {
                it.copy(
                    capturedMediaUri = uri,
                    capturedMediaType = if (uri != null) CapturedMediaType.VIDEO else null,
                )
            }
        } else {
            cameraCaptureManager.startVideoRecording()
        }
    }

    /**
     * Clears the captured media URI and resets capture state.
     * Also clears the manager's state to prevent the init collector from
     * resurrecting the old URI on the next state emission.
     */
    fun clearCapturedMedia() {
        cameraCaptureManager.clearCapturedUri()
        _uiState.update {
            it.copy(
                capturedMediaUri = null,
                capturedMediaType = null,
                error = null,
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
    }
}

/**
 * UI state for the camera capture screen.
 */
data class CameraUiState(
    val isPreviewActive: Boolean = false,
    val cameraFacing: CameraFacing = CameraFacing.BACK,
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val aspectRatio: CameraAspectRatio = CameraAspectRatio.STANDARD,
    val isCapturing: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val capturedMediaUri: String? = null,
    val capturedMediaType: CapturedMediaType? = null,
    val error: String? = null,
    val cameraSelection: MediaDeviceSelectionUiState = defaultCameraSelection(cameraFacing),
) {
    /**
     * Returns a formatted duration string for video recording (MM:SS).
     */
    val formattedDuration: String
        get() {
            return formatMediaDuration(recordingDurationMs, true)
        }
}

/**
 * Converts a [CameraCaptureError] to a user-friendly error message.
 */
private fun CameraCaptureError.toErrorMessage(): String =
    when (this) {
        is CameraCaptureError.CameraNotAvailable -> "Camera is not available"
        is CameraCaptureError.PermissionDenied -> "Camera permission denied"
        is CameraCaptureError.CaptureFailed -> "Failed to capture photo"
        is CameraCaptureError.RecordingFailed -> "Failed to record video"
        is CameraCaptureError.Unknown -> message
    }
