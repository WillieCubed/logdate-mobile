package app.logdate.feature.editor.ui.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.provider.MediaStore
import android.view.Surface
import androidx.annotation.RequiresPermission
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
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
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(CAMERA_ROUTE_PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(CameraCaptureState())
    override val state: StateFlow<CameraCaptureState> = _state.asStateFlow()

    private val _previewStreaming = MutableStateFlow(false)

    /**
     * Emits `true` when the attached [PreviewView] reports that frames are streaming.
     */
    val previewStreaming: StateFlow<Boolean> = _previewStreaming.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var camera: Camera? = null
    private var recordingDeferred: CompletableDeferred<String?>? = null
    private var previewView: PreviewView? = null
    private var previewStreamObserver: Observer<PreviewView.StreamState>? = null

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
     * Attaches the [PreviewView] used to render the live camera stream.
     */
    fun attachPreviewView(view: PreviewView?) {
        if (previewView === view) {
            return
        }

        previewStreamObserver?.let { observer ->
            previewView?.previewStreamState?.removeObserver(observer)
        }

        previewView = view

        if (view == null) {
            previewStreamObserver = null
            _previewStreaming.value = false
            return
        }

        val observer =
            Observer<PreviewView.StreamState> { state ->
                _previewStreaming.value = state == PreviewView.StreamState.STREAMING
            }
        previewStreamObserver = observer
        view.previewStreamState.observeForever(observer)
        _previewStreaming.value = view.previewStreamState.value == PreviewView.StreamState.STREAMING

        preview?.surfaceProvider = view.surfaceProvider
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
     * @param x Pixel x coordinate in the attached [PreviewView].
     * @param y Pixel y coordinate in the attached [PreviewView].
     */
    fun tapToFocus(
        x: Float,
        y: Float,
    ) {
        val cam = camera ?: return
        val currentPreviewView = previewView ?: return
        if (!_previewStreaming.value) {
            return
        }

        val point = currentPreviewView.meteringPointFactory.createPoint(x, y)
        val action =
            FocusMeteringAction
                .Builder(point)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    override suspend fun startPreview(facing: CameraFacing) =
        withContext(Dispatchers.Main) {
            try {
                val provider = ProcessCameraProvider.awaitInstance(context)
                cameraProvider = provider
                val owner = lifecycleOwner ?: error("LifecycleOwner is not set")
                val cameraSelection =
                    cameraSelectionFor(
                        provider = provider,
                        preferredDeviceId = _state.value.cameraSelection.selectedDeviceId ?: preferredCameraDeviceId(),
                        fallbackFacing = facing,
                    )
                val selectedDeviceId = cameraSelection.selectedDeviceId
                val selector = cameraSelectorForDeviceId(selectedDeviceId)

                _previewStreaming.value = false
                createUseCases()
                provider.unbindAll()
                camera =
                    provider.bindToLifecycle(
                        owner,
                        selector,
                        preview,
                        imageCapture,
                        videoCapture,
                    )

                _state.update {
                    it.copy(
                        isPreviewActive = true,
                        cameraFacing = cameraSelection.facingForSelection(),
                        error = null,
                        cameraSelection = cameraSelection,
                    )
                }
                persistPreferredCameraDeviceId(selectedDeviceId)
            } catch (e: Exception) {
                Napier.e("Failed to start camera preview", e)
                _previewStreaming.value = false
                _state.update {
                    it.copy(
                        isPreviewActive = false,
                        error = CameraCaptureError.Unknown(e.message ?: "Unknown error"),
                    )
                }
            }
        }

    override suspend fun stopPreview() =
        withContext(Dispatchers.Main) {
            try {
                currentRecording?.stop()
                currentRecording = null
                camera = null
                _previewStreaming.value = false
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

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
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
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/LogDate")
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

    override suspend fun switchCamera() =
        withContext(Dispatchers.Main) {
            if (_state.value.isRecording) {
                Napier.w("Cannot switch camera while recording")
                return@withContext
            }

            val provider =
                cameraProvider ?: run {
                    _state.update {
                        it.copy(error = CameraCaptureError.Unknown("Camera provider is not ready"))
                    }
                    return@withContext
                }
            val owner =
                lifecycleOwner ?: run {
                    _state.update {
                        it.copy(error = CameraCaptureError.Unknown("Lifecycle owner is not ready"))
                    }
                    return@withContext
                }
            val newFacing =
                when (_state.value.cameraFacing) {
                    CameraFacing.FRONT -> CameraFacing.BACK
                    CameraFacing.BACK -> CameraFacing.FRONT
                }
            val cameraSelection =
                cameraSelectionFor(
                    provider = provider,
                    fallbackFacing = newFacing,
                    requireBuiltIn = true,
                )
            val selectedDeviceId =
                cameraSelection
                    .devices
                    .firstOrNull { it.category.matchesFacing(newFacing) }
                    ?.id
                    ?: cameraSelection.selectedDeviceId

            try {
                _state.update { it.copy(error = null) }
                _previewStreaming.value = false
                createUseCases()
                provider.unbindAll()
                camera =
                    provider.bindToLifecycle(
                        owner,
                        cameraSelectorForDeviceId(selectedDeviceId),
                        preview,
                        imageCapture,
                        videoCapture,
                    )
                _state.update {
                    it.copy(
                        cameraFacing = cameraSelection.copy(selectedDeviceId = selectedDeviceId).facingForSelection(),
                        error = null,
                        cameraSelection = cameraSelection.copy(selectedDeviceId = selectedDeviceId),
                    )
                }
                persistPreferredCameraDeviceId(selectedDeviceId)
            } catch (e: Exception) {
                Napier.e("Failed to switch camera", e)
                _previewStreaming.value = false
                _state.update {
                    it.copy(error = CameraCaptureError.Unknown(e.message ?: "Switch failed"))
                }
            }
        }

    override suspend fun selectCameraDevice(deviceId: String) =
        withContext(Dispatchers.Main) {
            if (_state.value.isRecording) {
                Napier.w("Cannot select camera while recording")
                return@withContext
            }

            val provider =
                cameraProvider ?: run {
                    _state.update {
                        it.copy(error = CameraCaptureError.Unknown("Camera provider is not ready"))
                    }
                    return@withContext
                }
            val owner =
                lifecycleOwner ?: run {
                    _state.update {
                        it.copy(error = CameraCaptureError.Unknown("Lifecycle owner is not ready"))
                    }
                    return@withContext
                }
            val cameraSelection =
                cameraSelectionFor(
                    provider = provider,
                    preferredDeviceId = deviceId,
                    fallbackFacing = _state.value.cameraFacing,
                )

            if (cameraSelection.selectedDeviceId != deviceId) {
                Napier.w("Requested camera is unavailable: $deviceId")
                return@withContext
            }
            if (_state.value.cameraSelection.selectedDeviceId == deviceId) return@withContext

            try {
                _state.update { it.copy(error = null) }
                _previewStreaming.value = false
                createUseCases()
                provider.unbindAll()
                camera =
                    provider.bindToLifecycle(
                        owner,
                        cameraSelectorForDeviceId(deviceId),
                        preview,
                        imageCapture,
                        videoCapture,
                    )
                _state.update {
                    it.copy(
                        cameraFacing = cameraSelection.facingForSelection(),
                        error = null,
                        cameraSelection = cameraSelection,
                    )
                }
                persistPreferredCameraDeviceId(deviceId)
            } catch (e: Exception) {
                Napier.e("Failed to select camera", e)
                _previewStreaming.value = false
                _state.update {
                    it.copy(error = CameraCaptureError.Unknown(e.message ?: "Camera selection failed"))
                }
            }
        }

    override fun setCaptureMode(mode: CaptureMode) {
        _state.update { it.copy(captureMode = mode) }
    }

    override fun setAspectRatio(ratio: CameraAspectRatio) {
        _state.update { it.copy(aspectRatio = ratio) }
    }

    override fun clearCapturedUri() {
        _state.update { it.copy(lastCapturedUri = null) }
    }

    override fun release() {
        currentRecording?.stop()
        currentRecording = null
        recordingDeferred?.complete(null)
        recordingDeferred = null
        previewStreamObserver?.let { observer ->
            previewView?.previewStreamState?.removeObserver(observer)
        }
        previewStreamObserver = null
        previewView = null
        _previewStreaming.value = false
        camera = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        preview = null
        imageCapture = null
        videoCapture = null
        lifecycleOwner = null
        _state.update { CameraCaptureState() }
    }

    private fun cameraSelectorForDeviceId(deviceId: String?): CameraSelector {
        val cameraId = deviceId?.removePrefix(CAMERA_DEVICE_ID_PREFIX)
        if (cameraId.isNullOrBlank() || cameraId == deviceId) {
            return CameraSelector.DEFAULT_BACK_CAMERA
        }

        return CameraSelector
            .Builder()
            .addCameraFilter { cameraInfos ->
                cameraInfos.filter { info ->
                    Camera2CameraInfo.from(info).cameraId == cameraId
                }
            }.build()
    }

    private fun cameraSelectionFor(
        provider: ProcessCameraProvider,
        preferredDeviceId: String? = null,
        fallbackFacing: CameraFacing = CameraFacing.BACK,
        requireBuiltIn: Boolean = false,
    ): MediaDeviceSelectionUiState {
        val devices =
            provider
                .availableCameraInfos
                .mapIndexed { index, cameraInfo -> cameraInfo.toMediaDeviceUiState(index) }
                .filter {
                    !requireBuiltIn ||
                        it.category == MediaDeviceCategory.FRONT_CAMERA ||
                        it.category == MediaDeviceCategory.BACK_CAMERA
                }.ifEmpty { defaultCameraSelection(fallbackFacing).devices }

        val selectedId =
            preferredDeviceId?.takeIf { preferred -> devices.any { it.id == preferred } }
                ?: devices.firstOrNull { it.category.matchesFacing(fallbackFacing) }?.id
                ?: devices.first().id
        val routeControlMessage =
            when {
                preferredDeviceId != null && preferredDeviceId != selectedId ->
                    "Selected camera is unavailable. LogDate will use ${devices.first { it.id == selectedId }.label}."
                devices.none { it.isExternal } ->
                    "External cameras appear here when connected."
                else -> null
            }

        return MediaDeviceSelectionUiState(
            kind = MediaDeviceKind.CAMERA,
            devices = devices,
            selectedDeviceId = selectedId,
            isSelectionControllable = devices.size > 1,
            routeControlMessage = routeControlMessage,
        )
    }

    private fun preferredCameraDeviceId(): String? = preferences.getString(KEY_PREFERRED_CAMERA_DEVICE_ID, null)

    private fun persistPreferredCameraDeviceId(deviceId: String?) {
        if (deviceId == null) return
        preferences
            .edit()
            .putString(KEY_PREFERRED_CAMERA_DEVICE_ID, deviceId)
            .apply()
    }

    private fun CameraInfo.toMediaDeviceUiState(index: Int): MediaDeviceUiState {
        val camera2Info = Camera2CameraInfo.from(this)
        val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
        val category = lensFacing.toCameraCategory()
        return MediaDeviceUiState(
            id = "$CAMERA_DEVICE_ID_PREFIX${camera2Info.cameraId}",
            label = cameraLabel(category, index),
            kind = MediaDeviceKind.CAMERA,
            category = category,
            isExternal = category == MediaDeviceCategory.EXTERNAL,
        )
    }

    private fun Int?.toCameraCategory(): MediaDeviceCategory =
        when (this) {
            CameraCharacteristics.LENS_FACING_FRONT -> MediaDeviceCategory.FRONT_CAMERA
            CameraCharacteristics.LENS_FACING_BACK -> MediaDeviceCategory.BACK_CAMERA
            CameraCharacteristics.LENS_FACING_EXTERNAL -> MediaDeviceCategory.EXTERNAL
            else -> MediaDeviceCategory.EXTERNAL
        }

    private fun cameraLabel(
        category: MediaDeviceCategory,
        index: Int,
    ): String =
        when (category) {
            MediaDeviceCategory.FRONT_CAMERA -> "Front camera"
            MediaDeviceCategory.BACK_CAMERA -> "Back camera"
            MediaDeviceCategory.EXTERNAL -> "External camera ${index + 1}"
            else -> "Camera ${index + 1}"
        }

    private fun MediaDeviceCategory.matchesFacing(facing: CameraFacing): Boolean =
        when (facing) {
            CameraFacing.FRONT -> this == MediaDeviceCategory.FRONT_CAMERA
            CameraFacing.BACK -> this == MediaDeviceCategory.BACK_CAMERA
        }

    private fun MediaDeviceSelectionUiState.facingForSelection(): CameraFacing =
        when (selectedDevice?.category) {
            MediaDeviceCategory.FRONT_CAMERA -> CameraFacing.FRONT
            else -> CameraFacing.BACK
        }

    private fun createUseCases() {
        val currentPreviewView = previewView ?: error("PreviewView is not attached")
        val resolutionSelector = resolutionSelector()
        val targetRotation = currentDisplayRotation(currentPreviewView)

        preview =
            Preview
                .Builder()
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(targetRotation)
                .build()
                .apply {
                    setSurfaceProvider(currentPreviewView.surfaceProvider)
                }

        imageCapture =
            ImageCapture
                .Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(targetRotation)
                .build()

        // Prefer the highest reasonable quality the device supports, with a
        // graceful fallback so cameras that don't expose UHD/FHD still record
        // instead of failing with no producer.
        val qualitySelector =
            QualitySelector.fromOrderedList(
                listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                FallbackStrategy.higherQualityOrLowerThan(Quality.SD),
            )

        val recorder =
            Recorder
                .Builder()
                .setQualitySelector(qualitySelector)
                .build()
        videoCapture =
            VideoCapture
                .Builder(recorder)
                .setTargetRotation(targetRotation)
                .build()
    }

    /**
     * Resolves the current display rotation so CameraX can pre-rotate frames.
     * Without an explicit target the camera can output sideways frames on
     * foldables and tablets that change orientation mid-session.
     */
    private fun currentDisplayRotation(view: PreviewView): Int =
        view.display?.rotation
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.context.display.rotation
            } else {
                Surface.ROTATION_0
            }

    private fun resolutionSelector(): ResolutionSelector {
        val aspectRatioStrategy =
            when (_state.value.aspectRatio) {
                CameraAspectRatio.STANDARD -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                CameraAspectRatio.FULL,
                CameraAspectRatio.SQUARE,
                -> AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            }

        return ResolutionSelector
            .Builder()
            .setAspectRatioStrategy(aspectRatioStrategy)
            .build()
    }

    private companion object {
        const val CAMERA_DEVICE_ID_PREFIX = "camera-"
        const val CAMERA_ROUTE_PREFS_NAME = "logdate_camera_routes"
        const val KEY_PREFERRED_CAMERA_DEVICE_ID = "preferred_camera_device_id"
    }
}
