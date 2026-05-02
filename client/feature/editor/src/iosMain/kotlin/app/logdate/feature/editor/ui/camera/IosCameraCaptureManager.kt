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
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
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
 * iOS [CameraCaptureManager] backed by `AVCaptureSession` with `AVCapturePhotoOutput`.
 *
 * Photo capture is fully wired: photos are saved as JPEGs under the app's
 * `Documents/imports/` directory and the resulting file URL is published through
 * [CameraCaptureState.lastCapturedUri]. Video recording remains a TODO — the contract is
 * preserved so the editor screen does not crash, but `startVideoRecording` / `stopVideoRecording`
 * surface a clear unsupported-state error.
 *
 * The exposed [previewLayer] is intended to be embedded in a Compose [androidx.compose.ui.viewinterop.UIKitView]
 * so the live preview renders inside the editor screen.
 */
class IosCameraCaptureManager : CameraCaptureManager {
    private val _state = MutableStateFlow(CameraCaptureState())
    override val state: StateFlow<CameraCaptureState> = _state.asStateFlow()

    private val session = AVCaptureSession()
    private val photoOutput = AVCapturePhotoOutput()
    private var currentInput: AVCaptureDeviceInput? = null
    private var pendingCapture: CompletableDeferred<String?>? = null
    private val photoDelegate = PhotoCaptureDelegate()

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
                ensureInputForFacing(facing)
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
        if (session.isRunning()) {
            withContext(Dispatchers.Default) { session.stopRunning() }
        }
        _state.update { it.copy(isPreviewActive = false) }
    }

    override suspend fun capturePhoto(): String? {
        if (!session.isRunning()) {
            _state.update { it.copy(error = CameraCaptureError.CaptureFailed) }
            return null
        }
        val deferred = CompletableDeferred<String?>()
        pendingCapture = deferred
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
        // Video recording isn't wired yet — would require AVCaptureMovieFileOutput plus a separate
        // AVCaptureFileOutputRecordingDelegate to persist the temp URL after didFinishRecording.
        _state.update { it.copy(error = CameraCaptureError.RecordingFailed) }
    }

    override suspend fun stopVideoRecording(): String? {
        _state.update { it.copy(isRecording = false) }
        return null
    }

    override suspend fun switchCamera() {
        val nextFacing = if (_state.value.cameraFacing == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK
        withContext(Dispatchers.Default) {
            session.beginConfiguration()
            currentInput?.let { session.removeInput(it) }
            ensureInputForFacing(nextFacing)
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
        if (session.isRunning()) session.stopRunning()
        currentInput?.let { session.removeInput(it) }
        currentInput = null
        _state.update { it.copy(isPreviewActive = false, isRecording = false) }
    }

    private fun ensureInputForFacing(facing: CameraFacing) {
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
                currentInput = input
            } else {
                _state.update { it.copy(error = CameraCaptureError.CameraNotAvailable) }
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
            val callback = pendingCapture ?: return
            pendingCapture = null
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
            val destPath = createCapturePath()
            if (destPath == null) {
                callback.complete(null)
                return
            }
            val ok = data.writeToFile(path = destPath, atomically = true)
            callback.complete(if (ok) "file://$destPath" else null)
        }
    }
}

private fun createCapturePath(): String? {
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
    val dest: NSURL = importsDir.URLByAppendingPathComponent("${Uuid.random()}.jpg") ?: return null
    return dest.path
}
