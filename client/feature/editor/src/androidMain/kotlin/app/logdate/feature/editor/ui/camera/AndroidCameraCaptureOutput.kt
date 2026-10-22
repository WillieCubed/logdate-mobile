package app.logdate.feature.editor.ui.camera

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import app.logdate.client.device.restore.admitNativeContent
import app.logdate.client.media.MediaManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** Owns durable capture output and finalization independently of preview teardown. */
internal class AndroidCameraCaptureOutput(
    private val context: Context,
    private val mediaManager: MediaManager,
    private val state: MutableStateFlow<CameraCaptureState>,
) {
    private val appContext = context.applicationContext
    private var currentRecording: Recording? = null
    private var recordingDeferred: CompletableDeferred<String?>? = null
    private var recordingFile: java.io.File? = null

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Capture output must survive activity teardown and Android's cache eviction. The
     * finalizer may still be running after the camera ViewModel is cleared, so these files
     * intentionally live in app-private durable storage rather than [Context.getCacheDir].
     */
    private val captureStagingDirectory =
        java.io.File(appContext.filesDir, "camera-captures")
    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    suspend fun capturePhoto(imageCapture: () -> ImageCapture?): String? =
        withContext(Dispatchers.Main) {
            val capture =
                imageCapture() ?: run {
                    Napier.e("ImageCapture is not initialized")
                    state.update { it.copy(error = CameraCaptureError.CaptureFailed) }
                    return@withContext null
                }

            val timeStamp = timestampFormat.format(Date())
            val fileName = "LOGDATE_$timeStamp.jpg"
            admitNativeContent(captureStagingDirectory)
            val sourceFile = java.io.File.createTempFile("logdate-camera-", ".jpg", captureStagingDirectory)
            val outputOptions = ImageCapture.OutputFileOptions.Builder(sourceFile).build()
            val capturedPath = awaitPhotoCapture(capture, outputOptions, sourceFile)
            capturedPath ?: return@withContext null
            try {
                val uri =
                    withContext(Dispatchers.IO) {
                        mediaManager.saveMediaFromFile(capturedPath, fileName, "image/jpeg")
                    }
                state.update {
                    it.copy(
                        lastCapturedUri = uri,
                        error = null,
                    )
                }
                uri
            } catch (exception: Exception) {
                Napier.e("Failed to persist photo capture", exception)
                state.update { it.copy(error = CameraCaptureError.CaptureFailed) }
                null
            } finally {
                java.io.File(capturedPath).delete()
            }
        }

    private suspend fun awaitPhotoCapture(
        capture: ImageCapture,
        outputOptions: ImageCapture.OutputFileOptions,
        sourceFile: java.io.File,
    ): String? =
        suspendCancellableCoroutine<String?> { continuation ->
            continuation.invokeOnCancellation {
                // Keep the durable staged file for the capture finalizer/recovery path.
                // Deleting it here loses a valid capture when the activity is destroyed
                // while CameraX is delivering the callback.
            }
            capture.takePicture(
                outputOptions,
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (continuation.isActive) {
                            continuation.resume(sourceFile.absolutePath)
                        } else {
                            // The callback can arrive after the activity/VM was torn down.
                            // Keep the durable staged file for recovery instead of losing
                            // a successfully captured photo.
                            Napier.d("Photo capture completed after camera teardown; retaining staged output")
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Napier.e("Photo capture failed", exception)
                        state.update { it.copy(error = CameraCaptureError.CaptureFailed) }
                        sourceFile.delete()
                        continuation.resume(null)
                    }
                },
            )
        }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun startVideoRecording(videoCapture: VideoCapture<Recorder>?) {
        val capture =
            videoCapture ?: run {
                Napier.e("VideoCapture is not initialized")
                state.update { it.copy(error = CameraCaptureError.RecordingFailed) }
                return
            }

        if (currentRecording != null) {
            Napier.w("Recording already in progress")
            return
        }

        val timeStamp = timestampFormat.format(Date())
        val fileName = "LOGDATE_$timeStamp.mp4"

        admitNativeContent(captureStagingDirectory)
        val outputFile = java.io.File.createTempFile("logdate-camera-", ".mp4", captureStagingDirectory)
        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        val deferred = CompletableDeferred<String?>()
        recordingDeferred = deferred
        recordingFile = outputFile

        try {
            currentRecording =
                capture.output
                    .prepareRecording(context, outputOptions)
                    .withAudioEnabled()
                    .start(mainExecutor) { event ->
                        onVideoRecordEvent(event, fileName)
                    }
        } catch (e: SecurityException) {
            Napier.e("Audio permission required for video recording", e)
            state.update { it.copy(error = CameraCaptureError.PermissionDenied) }
            outputFile.delete()
            recordingFile = null
            deferred.complete(null)
            recordingDeferred = null
        } catch (e: Exception) {
            Napier.e("Failed to start video recording", e)
            state.update { it.copy(error = CameraCaptureError.RecordingFailed) }
            outputFile.delete()
            recordingFile = null
            deferred.complete(null)
            recordingDeferred = null
        }
    }

    private fun onVideoRecordEvent(
        event: VideoRecordEvent,
        fileName: String,
    ) {
        when (event) {
            is VideoRecordEvent.Start -> {
                state.update {
                    it.copy(
                        isRecording = true,
                        recordingDurationMs = 0L,
                        error = null,
                    )
                }
            }

            is VideoRecordEvent.Status -> {
                state.update {
                    it.copy(recordingDurationMs = event.recordingStats.recordedDurationNanos / 1_000_000)
                }
            }

            is VideoRecordEvent.Finalize -> {
                finalizeVideoRecording(event, fileName)
            }
        }
    }

    private fun finalizeVideoRecording(
        event: VideoRecordEvent.Finalize,
        fileName: String,
    ) {
        val file = recordingFile
        currentRecording = null
        recordingFile = null
        captureScope.launch(Dispatchers.IO) {
            val uri =
                if (!event.hasError() && file != null) {
                    runCatching {
                        mediaManager.saveMediaFromFile(
                            sourceFilePath = file.absolutePath,
                            fileName = fileName,
                            mimeType = "video/mp4",
                        )
                    }.onFailure { error ->
                        Napier.e("Failed to persist video capture", error)
                    }.getOrNull()
                } else {
                    Napier.e("Video recording failed with error: ${event.error}")
                    null
                }
            file?.delete()
            withContext(Dispatchers.Main.immediate) {
                state.update {
                    it.copy(
                        isRecording = false,
                        lastCapturedUri = uri,
                        error =
                            if (uri == null) CameraCaptureError.RecordingFailed else null,
                    )
                }
                recordingDeferred?.complete(uri)
                recordingDeferred = null
            }
        }
    }

    suspend fun stopVideoRecording(): String? {
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

    fun stopRecording() {
        currentRecording?.stop()
        currentRecording = null
    }
}
