@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.logdate.feature.editor.ui.camera

import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureFileOutput
import platform.AVFoundation.AVCaptureFileOutputRecordingDelegateProtocol
import platform.AVFoundation.AVCaptureMovieFileOutput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.position
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.darwin.NSObject
import kotlin.uuid.Uuid

/**
 * iOS [CameraCaptureManager] backed by `AVCaptureSession` with photo and video outputs.
 *
 * Photo capture saves JPEGs to `Documents/imports/`; video recording saves MOV files to the same
 * folder. Both surface their final file URL through [CameraCaptureState.lastCapturedUri].
 *
 * The exposed [previewLayer] is intended to be embedded in a Compose
 * [androidx.compose.ui.viewinterop.UIKitView] so the live preview renders inside the editor.
 */
class IosCameraCaptureManager : CameraCaptureManager {
    private val _state = MutableStateFlow(CameraCaptureState())
    override val state: StateFlow<CameraCaptureState> = _state.asStateFlow()

    private val session = AVCaptureSession()
    private val photoOutput = AVCapturePhotoOutput()
    private val movieOutput = AVCaptureMovieFileOutput()
    private var videoInput: AVCaptureDeviceInput? = null
    private var audioInput: AVCaptureDeviceInput? = null
    private var pendingPhoto: CompletableDeferred<String?>? = null
    private var pendingVideo: CompletableDeferred<String?>? = null
    private val photoDelegate = PhotoCaptureDelegate()
    private val movieDelegate = MovieCaptureDelegate()

    val previewLayer: AVCaptureVideoPreviewLayer =
        AVCaptureVideoPreviewLayer(session = session).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }

    init {
        if (session.canAddOutput(photoOutput)) {
            session.addOutput(photoOutput)
        }
    }

    override suspend fun startPreview(facing: CameraFacing) {
        if (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) != AVAuthorizationStatusAuthorized) {
            _state.update { it.copy(error = CameraCaptureError.PermissionDenied) }
            return
        }
        try {
            withContext(Dispatchers.Default) {
                ensureVideoInputForFacing(facing)
                if (!session.isRunning()) session.startRunning()
            }
            _state.update {
                it.copy(
                    isPreviewActive = true,
                    cameraFacing = facing,
                    error = null,
                )
            }
        } catch (t: Throwable) {
            Napier.w("AVCaptureSession start failed: ${t.message}")
            _state.update { it.copy(error = CameraCaptureError.Unknown(t.message ?: "")) }
        }
    }

    override suspend fun stopPreview() {
        if (movieOutput.isRecording()) {
            movieOutput.stopRecording()
        }
        if (session.isRunning()) {
            withContext(Dispatchers.Default) { session.stopRunning() }
        }
        _state.update { it.copy(isPreviewActive = false, isRecording = false) }
    }

    override suspend fun capturePhoto(): String? {
        if (!session.isRunning()) {
            _state.update { it.copy(error = CameraCaptureError.CaptureFailed) }
            return null
        }
        val deferred = CompletableDeferred<String?>()
        pendingPhoto = deferred
        withContext(Dispatchers.Main) {
            photoOutput.capturePhotoWithSettings(
                settings = AVCapturePhotoSettings.photoSettings(),
                delegate = photoDelegate,
            )
        }
        val uri = deferred.await()
        if (uri != null) {
            _state.update { it.copy(lastCapturedUri = uri, error = null) }
        }
        return uri
    }

    override suspend fun startVideoRecording() {
        if (!session.isRunning() || videoInput == null) {
            _state.update { it.copy(error = CameraCaptureError.RecordingFailed) }
            return
        }
        if (movieOutput.isRecording()) return

        withContext(Dispatchers.Default) {
            session.beginConfiguration()
            if (!session.outputs.contains(movieOutput) && session.canAddOutput(movieOutput)) {
                session.addOutput(movieOutput)
            }
            ensureAudioInput()
            session.commitConfiguration()
        }

        val outputUrl = createVideoCaptureUrl()
        if (outputUrl == null) {
            _state.update { it.copy(error = CameraCaptureError.RecordingFailed) }
            return
        }
        val deferred = CompletableDeferred<String?>()
        pendingVideo = deferred
        withContext(Dispatchers.Main) {
            movieOutput.startRecordingToOutputFileURL(
                outputFileURL = outputUrl,
                recordingDelegate = movieDelegate,
            )
        }
        _state.update { it.copy(isRecording = true, error = null) }
    }

    override suspend fun stopVideoRecording(): String? {
        if (!movieOutput.isRecording()) return null
        val deferred = pendingVideo ?: return null
        withContext(Dispatchers.Main) { movieOutput.stopRecording() }
        val uri = deferred.await()
        _state.update { it.copy(isRecording = false, lastCapturedUri = uri ?: it.lastCapturedUri) }
        return uri
    }

    override suspend fun switchCamera() {
        val nextFacing = if (_state.value.cameraFacing == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK
        withContext(Dispatchers.Default) {
            session.beginConfiguration()
            videoInput?.let { session.removeInput(it) }
            ensureVideoInputForFacing(nextFacing)
            session.commitConfiguration()
        }
        _state.update { it.copy(cameraFacing = nextFacing) }
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
        if (movieOutput.isRecording()) movieOutput.stopRecording()
        if (session.isRunning()) session.stopRunning()
        videoInput?.let { session.removeInput(it) }
        audioInput?.let { session.removeInput(it) }
        videoInput = null
        audioInput = null
        _state.update { it.copy(isPreviewActive = false, isRecording = false) }
    }

    private fun ensureVideoInputForFacing(facing: CameraFacing) {
        val targetPosition =
            if (facing == CameraFacing.FRONT) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack

        @Suppress("UNCHECKED_CAST")
        val devices = AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo) as List<AVCaptureDevice>
        val device = devices.firstOrNull { it.position == targetPosition } ?: devices.firstOrNull()
        if (device == null) {
            _state.update { it.copy(error = CameraCaptureError.CameraNotAvailable) }
            return
        }
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            val input = AVCaptureDeviceInput.deviceInputWithDevice(device = device, error = errorPtr.ptr)
            if (input == null) {
                Napier.w("AVCaptureDeviceInput creation failed: ${errorPtr.value?.localizedDescription}")
                _state.update { it.copy(error = CameraCaptureError.CameraNotAvailable) }
                return
            }
            if (session.canAddInput(input)) {
                session.addInput(input)
                videoInput = input
            } else {
                _state.update { it.copy(error = CameraCaptureError.CameraNotAvailable) }
            }
        }
    }

    private fun ensureAudioInput() {
        if (audioInput != null) return
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio) ?: return
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            val input = AVCaptureDeviceInput.deviceInputWithDevice(device = device, error = errorPtr.ptr)
            if (input == null) {
                Napier.w("AVCaptureDeviceInput audio creation failed: ${errorPtr.value?.localizedDescription}")
                return
            }
            if (session.canAddInput(input)) {
                session.addInput(input)
                audioInput = input
            }
        }
    }

    private inner class PhotoCaptureDelegate :
        NSObject(),
        AVCapturePhotoCaptureDelegateProtocol {
        override fun captureOutput(
            output: AVCapturePhotoOutput,
            didFinishProcessingPhoto: AVCapturePhoto,
            error: NSError?,
        ) {
            val callback = pendingPhoto ?: return
            pendingPhoto = null
            if (error != null) {
                Napier.w("AVCapturePhotoOutput error: ${error.localizedDescription}")
                callback.complete(null)
                return
            }
            val data = didFinishProcessingPhoto.fileDataRepresentation()
            if (data == null) {
                callback.complete(null)
                return
            }
            val destPath = createPhotoCapturePath()
            if (destPath == null) {
                callback.complete(null)
                return
            }
            val ok = data.writeToFile(path = destPath, atomically = true)
            callback.complete(if (ok) "file://$destPath" else null)
        }
    }

    private inner class MovieCaptureDelegate :
        NSObject(),
        AVCaptureFileOutputRecordingDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureFileOutput,
            didFinishRecordingToOutputFileAtURL: NSURL,
            fromConnections: List<*>,
            error: NSError?,
        ) {
            val callback = pendingVideo ?: return
            pendingVideo = null
            if (error != null) {
                Napier.w("AVCaptureMovieFileOutput error: ${error.localizedDescription}")
                callback.complete(null)
                return
            }
            callback.complete(didFinishRecordingToOutputFileAtURL.absoluteString)
        }
    }
}

private fun createPhotoCapturePath(): String? = createImportsUrl(extension = "jpg")?.path

private fun createVideoCaptureUrl(): NSURL? = createImportsUrl(extension = "mov")

private fun createImportsUrl(extension: String): NSURL? {
    val docs =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: return null
    val importsDir = docs.URLByAppendingPathComponent("imports") ?: return null
    NSFileManager.defaultManager.createDirectoryAtURL(
        url = importsDir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return importsDir.URLByAppendingPathComponent("${Uuid.random()}.$extension")
}
