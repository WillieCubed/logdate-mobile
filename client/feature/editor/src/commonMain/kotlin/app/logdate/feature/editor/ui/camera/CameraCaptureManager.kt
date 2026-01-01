package app.logdate.feature.editor.ui.camera

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing camera capture operations.
 * Platform-specific implementations handle the actual camera hardware.
 */
interface CameraCaptureManager {
    /**
     * Observable state of the camera capture manager.
     */
    val state: StateFlow<CameraCaptureState>

    /**
     * Initializes the camera and starts the preview.
     * @param facing The initial camera facing direction.
     */
    suspend fun startPreview(facing: CameraFacing = CameraFacing.BACK)

    /**
     * Stops the camera preview and releases resources.
     */
    suspend fun stopPreview()

    /**
     * Captures a photo.
     * @return The URI of the captured photo, or null if capture failed.
     */
    suspend fun capturePhoto(): String?

    /**
     * Starts video recording.
     */
    suspend fun startVideoRecording()

    /**
     * Stops video recording.
     * @return The URI of the recorded video, or null if recording failed.
     */
    suspend fun stopVideoRecording(): String?

    /**
     * Switches between front and back camera.
     */
    suspend fun switchCamera()

    /**
     * Sets the capture mode (photo or video).
     */
    fun setCaptureMode(mode: CaptureMode)

    /**
     * Releases all camera resources.
     * Should be called when the camera is no longer needed.
     */
    fun release()
}

/**
 * The current state of the camera capture manager.
 */
data class CameraCaptureState(
    val isPreviewActive: Boolean = false,
    val cameraFacing: CameraFacing = CameraFacing.BACK,
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val lastCapturedUri: String? = null,
    val error: CameraCaptureError? = null
)

/**
 * Camera facing direction.
 */
enum class CameraFacing {
    FRONT,
    BACK
}

/**
 * Capture mode for the camera.
 */
enum class CaptureMode {
    PHOTO,
    VIDEO
}

/**
 * Type of captured media.
 */
enum class CapturedMediaType {
    PHOTO,
    VIDEO
}

/**
 * Errors that can occur during camera operations.
 */
sealed class CameraCaptureError {
    data object CameraNotAvailable : CameraCaptureError()
    data object PermissionDenied : CameraCaptureError()
    data object CaptureFailed : CameraCaptureError()
    data object RecordingFailed : CameraCaptureError()
    data class Unknown(val message: String) : CameraCaptureError()
}
