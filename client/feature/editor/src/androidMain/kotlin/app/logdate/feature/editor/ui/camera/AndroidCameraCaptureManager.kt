package app.logdate.feature.editor.ui.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
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
import kotlinx.coroutines.CompletableDeferred
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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Android implementation of [CameraCaptureManager] using CameraX.
 */
class AndroidCameraCaptureManager(
    private val context: Context,
) : CameraCaptureManager {
    private val _state = MutableStateFlow(CameraCaptureState())
    override val state: StateFlow<CameraCaptureState> = _state.asStateFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)

    /**
     * Observable surface request for binding to a [CameraXViewfinder].
     * Emits a new [SurfaceRequest] each time the preview is started or the camera is switched.
     */
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var camera: Camera? = null
    private var recordingDeferred: CompletableDeferred<String?>? = null

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * Sets the lifecycle owner for camera binding.
     * Must be called before starting the preview.
     */
    fun setLifecycleOwner(owner: LifecycleOwner) {
        lifecycleOwner = owner
    }

    /**
     * Sets the camera zoom ratio.
     * @param ratio Zoom ratio where 1.0 is no zoom. Clamped to the camera's supported range.
     */
    fun setZoomRatio(ratio: Float) {
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value ?: return
        val clamped = ratio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clamped)
    }

    /**
     * Triggers tap-to-focus at the given point.
     * @param meteringPointFactory Factory from the viewfinder for coordinate mapping.
     * @param x Normalized x coordinate (0..1) or pixel coordinate depending on factory.
     * @param y Normalized y coordinate (0..1) or pixel coordinate depending on factory.
     */
    fun tapToFocus(
        meteringPointFactory: MeteringPointFactory,
        x: Float,
        y: Float,
    ) {
        val cam = camera ?: return
        val point = meteringPointFactory.createPoint(x, y)
        val action =
            FocusMeteringAction
                .Builder(point)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    override suspend fun startPreview(facing: CameraFacing) {
        try {
            val provider = ProcessCameraProvider.awaitInstance(context)
            cameraProvider = provider

            val cameraSelector =
                when (facing) {
                    CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                    CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                }

            val resolutionSelector =
                ResolutionSelector
                    .Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()

            preview =
                Preview
                    .Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build()
                    .apply {
                        setSurfaceProvider { request ->
                            _surfaceRequest.value = request
                        }
                    }

            imageCapture =
                ImageCapture
                    .Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setResolutionSelector(resolutionSelector)
                    .build()

            val recorder =
                Recorder
                    .Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val owner = lifecycleOwner
            if (owner != null) {
                provider.unbindAll()
                camera =
                    provider.bindToLifecycle(
                        owner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        videoCapture,
                    )
            }

            _state.update {
                it.copy(
                    isPreviewActive = true,
                    cameraFacing = facing,
                    error = null,
                )
            }
        } catch (e: Exception) {
            Napier.e("Failed to start camera preview", e)
            _state.update {
                it.copy(
                    isPreviewActive = false,
                    error = CameraCaptureError.Unknown(e.message ?: "Unknown error"),
                )
            }
        }
    }

    override suspend fun stopPreview() {
        try {
            currentRecording?.stop()
            currentRecording = null
            camera = null
            _surfaceRequest.value = null
            cameraProvider?.unbindAll()
            _state.update {
                it.copy(
                    isPreviewActive = false,
                    isRecording = false,
                    recordingDurationMs = 0L,
                )
            }
        } catch (e: Exception) {
            Napier.e("Error stopping camera preview", e)
        }
    }

    override suspend fun capturePhoto(): String? =
        withContext(Dispatchers.Main) {
            val capture =
                imageCapture ?: run {
                    Napier.e("ImageCapture is not initialized")
                    _state.update { it.copy(error = CameraCaptureError.CaptureFailed) }
                    return@withContext null
                }

            val timeStamp = timestampFormat.format(Date())
            val fileName = "LOGDATE_$timeStamp.jpg"

            val contentValues =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/LogDate")
                    }
                }

            val outputOptions =
                ImageCapture.OutputFileOptions
                    .Builder(
                        context.contentResolver,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues,
                    ).build()

            return@withContext suspendCancellableCoroutine { continuation ->
                capture.takePicture(
                    outputOptions,
                    mainExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val uri = output.savedUri?.toString()

                            _state.update {
                                it.copy(
                                    lastCapturedUri = uri,
                                    error = null,
                                )
                            }
                            continuation.resume(uri)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Napier.e("Photo capture failed", exception)
                            _state.update { it.copy(error = CameraCaptureError.CaptureFailed) }
                            continuation.resume(null)
                        }
                    },
                )
            }
        }

    override suspend fun startVideoRecording() {
        val capture =
            videoCapture ?: run {
                Napier.e("VideoCapture is not initialized")
                _state.update { it.copy(error = CameraCaptureError.RecordingFailed) }
                return
            }

        if (currentRecording != null) {
            Napier.w("Recording already in progress")
            return
        }

        val timeStamp = timestampFormat.format(Date())
        val fileName = "LOGDATE_$timeStamp.mp4"

        val contentValues =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/LogDate")
                }
            }

        val outputOptions =
            MediaStoreOutputOptions
                .Builder(
                    context.contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                ).setContentValues(contentValues)
                .build()

        val deferred = CompletableDeferred<String?>()
        recordingDeferred = deferred

        try {
            currentRecording =
                capture.output
                    .prepareRecording(context, outputOptions)
                    .withAudioEnabled()
                    .start(mainExecutor) { event ->
                        when (event) {
                            is VideoRecordEvent.Start -> {
                                _state.update {
                                    it.copy(
                                        isRecording = true,
                                        recordingDurationMs = 0L,
                                        error = null,
                                    )
                                }
                            }

                            is VideoRecordEvent.Status -> {
                                _state.update {
                                    it.copy(recordingDurationMs = event.recordingStats.recordedDurationNanos / 1_000_000)
                                }
                            }

                            is VideoRecordEvent.Finalize -> {
                                val uri =
                                    if (!event.hasError()) {
                                        event.outputResults.outputUri.toString()
                                    } else {
                                        Napier.e("Video recording failed with error: ${event.error}")
                                        null
                                    }
                                _state.update {
                                    it.copy(
                                        isRecording = false,
                                        lastCapturedUri = uri,
                                        error = if (event.hasError()) CameraCaptureError.RecordingFailed else null,
                                    )
                                }
                                currentRecording = null
                                recordingDeferred?.complete(uri)
                                recordingDeferred = null
                            }
                        }
                    }
        } catch (e: SecurityException) {
            Napier.e("Audio permission required for video recording", e)
            _state.update { it.copy(error = CameraCaptureError.PermissionDenied) }
            deferred.complete(null)
            recordingDeferred = null
        } catch (e: Exception) {
            Napier.e("Failed to start video recording", e)
            _state.update { it.copy(error = CameraCaptureError.RecordingFailed) }
            deferred.complete(null)
            recordingDeferred = null
        }
    }

    override suspend fun stopVideoRecording(): String? {
        val recording =
            currentRecording ?: run {
                Napier.w("No recording in progress")
                return null
            }

        val deferred =
            recordingDeferred ?: run {
                Napier.w("No recording deferred available")
                recording.stop()
                return null
            }

        recording.stop()
        return deferred.await()
    }

    override suspend fun switchCamera() {
        val newFacing =
            when (_state.value.cameraFacing) {
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
    }

    override fun clearCapturedUri() {
        _state.update { it.copy(lastCapturedUri = null) }
    }

    override fun release() {
        currentRecording?.stop()
        currentRecording = null
        recordingDeferred?.complete(null)
        recordingDeferred = null
        camera = null
        _surfaceRequest.value = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        preview = null
        imageCapture = null
        videoCapture = null
        lifecycleOwner = null
        _state.update { CameraCaptureState() }
    }
}
