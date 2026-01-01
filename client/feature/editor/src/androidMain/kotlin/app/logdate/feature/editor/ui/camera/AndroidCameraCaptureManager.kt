package app.logdate.feature.editor.ui.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Android implementation of [CameraCaptureManager] using CameraX.
 */
class AndroidCameraCaptureManager(
    private val context: Context
) : CameraCaptureManager {

    private val _state = MutableStateFlow(CameraCaptureState())
    override val state: StateFlow<CameraCaptureState> = _state.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private var lifecycleOwner: LifecycleOwner? = null

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    /**
     * Sets the lifecycle owner for camera binding.
     * Must be called before starting the preview.
     */
    fun setLifecycleOwner(owner: LifecycleOwner) {
        lifecycleOwner = owner
    }

    /**
     * Gets the preview use case for binding to a viewfinder.
     */
    fun getPreview(): Preview? = preview

    override suspend fun startPreview(facing: CameraFacing) {
        try {
            val provider = ProcessCameraProvider.awaitInstance(context)
            cameraProvider = provider

            val cameraSelector = when (facing) {
                CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            }

            preview = Preview.Builder()
                .setPreviewStabilizationEnabled(true)
                .build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val owner = lifecycleOwner
            if (owner != null) {
                provider.unbindAll()
                provider.bindToLifecycle(
                    owner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )
            }

            _state.update {
                it.copy(
                    isPreviewActive = true,
                    cameraFacing = facing,
                    error = null
                )
            }

            Napier.d("Camera preview started with facing: $facing")
        } catch (e: Exception) {
            Napier.e("Failed to start camera preview", e)
            _state.update {
                it.copy(
                    isPreviewActive = false,
                    error = CameraCaptureError.Unknown(e.message ?: "Unknown error")
                )
            }
        }
    }

    override suspend fun stopPreview() {
        try {
            currentRecording?.stop()
            currentRecording = null
            cameraProvider?.unbindAll()
            _state.update {
                it.copy(
                    isPreviewActive = false,
                    isRecording = false,
                    recordingDurationMs = 0L
                )
            }
            Napier.d("Camera preview stopped")
        } catch (e: Exception) {
            Napier.e("Error stopping camera preview", e)
        }
    }

    override suspend fun capturePhoto(): String? = withContext(Dispatchers.Main) {
        val capture = imageCapture ?: run {
            Napier.e("ImageCapture is not initialized")
            _state.update { it.copy(error = CameraCaptureError.CaptureFailed) }
            return@withContext null
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "LOGDATE_${timeStamp}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/LogDate")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        return@withContext suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                outputOptions,
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val uri = output.savedUri?.toString()
                        Napier.d("Photo captured: $uri")
                        _state.update {
                            it.copy(
                                lastCapturedUri = uri,
                                error = null
                            )
                        }
                        continuation.resume(uri)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Napier.e("Photo capture failed", exception)
                        _state.update { it.copy(error = CameraCaptureError.CaptureFailed) }
                        continuation.resume(null)
                    }
                }
            )
        }
    }

    override suspend fun startVideoRecording() {
        val capture = videoCapture ?: run {
            Napier.e("VideoCapture is not initialized")
            _state.update { it.copy(error = CameraCaptureError.RecordingFailed) }
            return
        }

        if (currentRecording != null) {
            Napier.w("Recording already in progress")
            return
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "LOGDATE_${timeStamp}.mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/LogDate")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        try {
            currentRecording = capture.output
                .prepareRecording(context, outputOptions)
                .withAudioEnabled()
                .start(mainExecutor) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            Napier.d("Video recording started")
                            _state.update {
                                it.copy(
                                    isRecording = true,
                                    recordingDurationMs = 0L,
                                    error = null
                                )
                            }
                        }

                        is VideoRecordEvent.Status -> {
                            _state.update {
                                it.copy(recordingDurationMs = event.recordingStats.recordedDurationNanos / 1_000_000)
                            }
                        }

                        is VideoRecordEvent.Finalize -> {
                            val uri = if (!event.hasError()) {
                                event.outputResults.outputUri.toString()
                            } else {
                                Napier.e("Video recording failed with error: ${event.error}")
                                null
                            }
                            _state.update {
                                it.copy(
                                    isRecording = false,
                                    lastCapturedUri = uri,
                                    error = if (event.hasError()) CameraCaptureError.RecordingFailed else null
                                )
                            }
                            currentRecording = null
                            Napier.d("Video recording finalized: $uri")
                        }
                    }
                }
        } catch (e: SecurityException) {
            Napier.e("Audio permission required for video recording", e)
            _state.update { it.copy(error = CameraCaptureError.PermissionDenied) }
        } catch (e: Exception) {
            Napier.e("Failed to start video recording", e)
            _state.update { it.copy(error = CameraCaptureError.RecordingFailed) }
        }
    }

    override suspend fun stopVideoRecording(): String? {
        val recording = currentRecording ?: run {
            Napier.w("No recording in progress")
            return null
        }

        recording.stop()

        return suspendCancellableCoroutine { continuation ->
            var resumed = false
            val checkState: () -> Unit = {
                if (!resumed && !_state.value.isRecording && _state.value.lastCapturedUri != null) {
                    resumed = true
                    continuation.resume(_state.value.lastCapturedUri)
                }
            }

            mainExecutor.execute {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!resumed) {
                        resumed = true
                        continuation.resume(_state.value.lastCapturedUri)
                    }
                }, 2000)
            }
        }
    }

    override suspend fun switchCamera() {
        val newFacing = when (_state.value.cameraFacing) {
            CameraFacing.FRONT -> CameraFacing.BACK
            CameraFacing.BACK -> CameraFacing.FRONT
        }

        if (_state.value.isRecording) {
            Napier.w("Cannot switch camera while recording")
            return
        }

        stopPreview()
        startPreview(newFacing)
    }

    override fun setCaptureMode(mode: CaptureMode) {
        _state.update { it.copy(captureMode = mode) }
        Napier.d("Capture mode set to: $mode")
    }

    override fun release() {
        currentRecording?.stop()
        currentRecording = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        preview = null
        imageCapture = null
        videoCapture = null
        lifecycleOwner = null
        _state.update { CameraCaptureState() }
        Napier.d("Camera resources released")
    }
}
