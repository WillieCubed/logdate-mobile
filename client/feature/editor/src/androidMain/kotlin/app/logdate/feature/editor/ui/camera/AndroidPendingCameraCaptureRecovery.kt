package app.logdate.feature.editor.ui.camera

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** Reopens a draft's private capture on this installation, including after Android transfer. */
internal class AndroidPendingCameraCaptureRecovery(
    private val filesDir: File,
    private val save: suspend (path: String, name: String, mimeType: String) -> String,
    private val complete: (File, String) -> Boolean = ::decodableCameraCapture,
) {
    suspend fun recover(sourcePath: String): RecoveredCameraCapture? =
        withContext(Dispatchers.IO) {
            val name = CAMERA_SOURCE.matchEntire(sourcePath)?.groupValues?.get(1) ?: return@withContext null
            val directory = File(filesDir, "camera-captures")
            if (!Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) return@withContext null
            val capture =
                CameraCaptureStagingStore(directory)
                    .recoverableFiles()
                    .firstOrNull { it.file.name == name }
                    ?: return@withContext null
            if (!Files.isRegularFile(capture.file.toPath(), LinkOption.NOFOLLOW_LINKS) ||
                capture.file.length() == 0L ||
                !complete(capture.file, capture.mimeType)
            ) {
                return@withContext null
            }
            val uri = save(capture.file.absolutePath, capture.fileName, capture.mimeType)
            RecoveredCameraCapture(
                uri = uri,
                type = if (capture.mimeType.startsWith("video/")) CapturedMediaType.VIDEO else CapturedMediaType.PHOTO,
            )
        }

    private companion object {
        val CAMERA_SOURCE =
            Regex("^/data/(?:data|user/\\d+|user_de/\\d+)/studio\\.hypertext\\.logdate/files/camera-captures/([^/]+)$")
    }
}

private fun decodableCameraCapture(
    file: File,
    mimeType: String,
): Boolean =
    runCatching {
        if (mimeType.startsWith("image/")) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            bounds.outWidth > 0 && bounds.outHeight > 0
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it >= 0L } == true
            } finally {
                retriever.release()
            }
        }
    }.getOrDefault(false)

internal data class RecoveredCameraCapture(
    val uri: String,
    val type: CapturedMediaType,
)
