package app.logdate.feature.stickers

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import app.logdate.feature.stickers.ui.StickerSubjectExtractor
import com.google.android.gms.mlkit.subject.segmentation.SubjectSegmentation
import com.google.android.gms.mlkit.subject.segmentation.SubjectSegmentationResult
import com.google.android.gms.mlkit.subject.segmentation.SubjectSegmenterOptions
import com.google.mlkit.vision.common.InputImage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android implementation using MLKit Subject Segmentation.
 *
 * Runs entirely on-device via Play Services with no cloud dependency.
 */
class MlKitStickerSubjectExtractor(
    private val context: Context,
) : StickerSubjectExtractor {
    private val segmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions
                .Builder()
                .enableForegroundBitmap()
                .build(),
        )
    }

    override suspend fun extractSubject(imageUri: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val inputImage = InputImage.fromFilePath(context, Uri.parse(imageUri))
                val result = processImage(inputImage)
                val foreground = result.foregroundBitmap ?: return@withContext null
                encodeToPng(foreground)
            } catch (e: Exception) {
                Napier.e("Sticker subject extraction failed", e)
                null
            }
        }

    private suspend fun processImage(inputImage: InputImage): SubjectSegmentationResult =
        suspendCancellableCoroutine { cont ->
            segmenter
                .process(inputImage)
                .addOnSuccessListener { result -> cont.resume(result) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
                .addOnCanceledListener { cont.cancel() }
        }

    private fun encodeToPng(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }
}
