package app.logdate.feature.postcards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.FileProvider
import app.logdate.feature.postcards.model.CanvasBackground
import app.logdate.feature.postcards.model.PostcardDocument
import app.logdate.feature.postcards.ui.ExportCaptureRegion
import app.logdate.feature.postcards.ui.ExportEngine
import app.logdate.feature.postcards.ui.ExportPreset
import app.logdate.feature.postcards.ui.ExportResult
import app.logdate.feature.postcards.ui.parseColor
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileOutputStream

/**
 * Android implementation of [ExportEngine].
 *
 * Renders the postcard document to a [Bitmap] using Android's Canvas API,
 * saves it as a PNG to the app's cache directory, and returns a shareable
 * FileProvider URI.
 */
class AndroidExportEngine(
    private val context: Context,
) : ExportEngine {
    override suspend fun exportToPng(
        document: PostcardDocument,
        captureRegion: ExportCaptureRegion,
        preset: ExportPreset,
        targetWidthPx: Int,
    ): ExportResult? =
        try {
            val aspectRatio = preset.widthRatio.toFloat() / preset.heightRatio.toFloat()
            val widthPx = targetWidthPx
            val heightPx = (widthPx / aspectRatio).toInt()

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Scale from canvas coordinates to pixel coordinates
            val scaleX = widthPx / captureRegion.width
            val scaleY = heightPx / captureRegion.height
            canvas.translate(-captureRegion.x * scaleX, -captureRegion.y * scaleY)
            canvas.scale(scaleX, scaleY)

            // Draw background
            drawBackground(canvas, document.background, captureRegion)

            // Save to cache
            val file = saveBitmapToCache(bitmap)
            bitmap.recycle()

            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file,
                )

            ExportResult(
                uri = uri.toString(),
                widthPx = widthPx,
                heightPx = heightPx,
            )
        } catch (e: Exception) {
            Napier.e("Postcard export failed", e)
            null
        }

    private fun drawBackground(
        canvas: Canvas,
        background: CanvasBackground,
        region: ExportCaptureRegion,
    ) {
        val paint = Paint()
        when (background) {
            is CanvasBackground.SolidColor -> {
                val color = parseColor(background.value)
                paint.color =
                    android.graphics.Color.argb(
                        (color.alpha * 255).toInt(),
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt(),
                    )
                canvas.drawRect(
                    RectF(region.x, region.y, region.x + region.width, region.y + region.height),
                    paint,
                )
            }
            is CanvasBackground.Gradient -> {
                // Fall back to first stop color for initial implementation
                val firstColor = background.stops.firstOrNull()?.let { parseColor(it.color) }
                if (firstColor != null) {
                    paint.color =
                        android.graphics.Color.argb(
                            (firstColor.alpha * 255).toInt(),
                            (firstColor.red * 255).toInt(),
                            (firstColor.green * 255).toInt(),
                            (firstColor.blue * 255).toInt(),
                        )
                    canvas.drawRect(
                        RectF(region.x, region.y, region.x + region.width, region.y + region.height),
                        paint,
                    )
                }
            }
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap): File {
        val cacheDir = File(context.externalCacheDir, "postcards")
        cacheDir.mkdirs()
        val file = File(cacheDir, "postcard_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}
