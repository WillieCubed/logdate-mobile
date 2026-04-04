package app.logdate.feature.postcards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import app.logdate.feature.postcards.model.CanvasBackground
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkTool
import app.logdate.feature.postcards.model.PostcardDocument
import app.logdate.feature.postcards.model.ShapeKind
import app.logdate.feature.postcards.ui.ExportCaptureRegion
import app.logdate.feature.postcards.ui.ExportEngine
import app.logdate.feature.postcards.ui.ExportPreset
import app.logdate.feature.postcards.ui.ExportResult
import app.logdate.feature.postcards.ui.parseColor
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileOutputStream
import kotlin.uuid.Uuid

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
        stickerUriMap: Map<Uuid, String>,
    ): ExportResult? =
        try {
            val aspectRatio = preset.widthRatio.toFloat() / preset.heightRatio.toFloat()
            val widthPx = targetWidthPx
            val heightPx = (widthPx / aspectRatio).toInt()

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Scale from canvas coordinates (dp-like) to pixel coordinates
            val scaleX = widthPx / captureRegion.width
            val scaleY = heightPx / captureRegion.height
            canvas.translate(-captureRegion.x * scaleX, -captureRegion.y * scaleY)
            canvas.scale(scaleX, scaleY)

            drawBackground(canvas, document.background, captureRegion)

            val sortedElements = document.elements.sortedBy { it.zIndex }
            for (element in sortedElements) {
                drawElement(canvas, element, stickerUriMap)
            }

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

    private fun drawElement(
        canvas: Canvas,
        element: CanvasElement,
        stickerUriMap: Map<Uuid, String> = emptyMap(),
    ) {
        val transform = element.transform
        canvas.save()
        canvas.translate(transform.x, transform.y)
        canvas.rotate(transform.rotation)
        canvas.scale(transform.scaleX, transform.scaleY)

        when (element) {
            is CanvasElement.Photo -> drawPhoto(canvas, element)
            is CanvasElement.Text -> drawText(canvas, element)
            is CanvasElement.Ink -> drawInk(canvas, element)
            is CanvasElement.Shape -> drawShape(canvas, element)
            is CanvasElement.Sticker -> drawSticker(canvas, element, stickerUriMap)
        }

        canvas.restore()
    }

    private fun drawPhoto(
        canvas: Canvas,
        element: CanvasElement.Photo,
    ) {
        val dest = RectF(0f, 0f, PHOTO_EXPORT_SIZE, PHOTO_EXPORT_SIZE)
        try {
            val inputStream =
                context.contentResolver.openInputStream(Uri.parse(element.mediaUri))
            if (inputStream != null) {
                val photoBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (photoBitmap != null) {
                    canvas.drawBitmap(photoBitmap, null, dest, null)
                    photoBitmap.recycle()
                    return
                }
            }
        } catch (e: Exception) {
            Napier.w("Could not load photo for export: ${element.mediaUri}", e)
        }
        drawMissingPlaceholder(canvas, dest)
    }

    private fun drawSticker(
        canvas: Canvas,
        element: CanvasElement.Sticker,
        stickerUriMap: Map<Uuid, String>,
    ) {
        val imageUri = stickerUriMap[element.stickerRef] ?: return
        val dest = RectF(0f, 0f, STICKER_EXPORT_SIZE, STICKER_EXPORT_SIZE)
        try {
            val inputStream = context.contentResolver.openInputStream(Uri.parse(imageUri))
            if (inputStream != null) {
                val stickerBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (stickerBitmap != null) {
                    canvas.drawBitmap(stickerBitmap, null, dest, null)
                    stickerBitmap.recycle()
                    return
                }
            }
        } catch (e: Exception) {
            Napier.w("Could not load sticker for export: $imageUri", e)
        }
    }

    private fun drawMissingPlaceholder(
        canvas: Canvas,
        rect: RectF,
    ) {
        val bgPaint =
            Paint().apply {
                color = android.graphics.Color.LTGRAY
                style = Paint.Style.FILL
            }
        val xPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.DKGRAY
                style = Paint.Style.STROKE
                strokeWidth = PLACEHOLDER_STROKE_WIDTH
            }
        canvas.drawRect(rect, bgPaint)
        canvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, xPaint)
        canvas.drawLine(rect.right, rect.top, rect.left, rect.bottom, xPaint)
    }

    private fun drawText(
        canvas: Canvas,
        element: CanvasElement.Text,
    ) {
        val color = parseColor(element.color)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color =
                    android.graphics.Color.argb(
                        (color.alpha * 255).toInt(),
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt(),
                    )
                textSize = element.fontSize
                typeface = resolveTypeface(element.fontFamily)
            }
        canvas.drawText(element.content, 0f, element.fontSize, paint)
    }

    private fun drawInk(
        canvas: Canvas,
        element: CanvasElement.Ink,
    ) {
        val points = element.points
        if (points.size < 2) return

        val color = parseColor(element.color)
        val alpha =
            when (element.tool) {
                InkTool.HIGHLIGHTER -> HIGHLIGHTER_ALPHA
                else -> 1f
            }
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color =
                    android.graphics.Color.argb(
                        (color.alpha * alpha * 255).toInt(),
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt(),
                    )
                style = Paint.Style.STROKE
                strokeWidth = element.strokeWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

        // Offset ink points relative to the element's own origin
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val path =
            Path().apply {
                moveTo(points[0].x - minX, points[0].y - minY)
                for (i in 1 until points.size) {
                    lineTo(points[i].x - minX, points[i].y - minY)
                }
            }
        canvas.drawPath(path, paint)
    }

    private fun drawShape(
        canvas: Canvas,
        element: CanvasElement.Shape,
    ) {
        val strokeColor = parseColor(element.color)
        val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    android.graphics.Color.argb(
                        (strokeColor.alpha * 255).toInt(),
                        (strokeColor.red * 255).toInt(),
                        (strokeColor.green * 255).toInt(),
                        (strokeColor.blue * 255).toInt(),
                    )
                style = Paint.Style.STROKE
                strokeWidth = element.strokeWidth
            }

        val fillPaint =
            element.fillColor?.let { fillHex ->
                val fillColor = parseColor(fillHex)
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color =
                        android.graphics.Color.argb(
                            (fillColor.alpha * 255).toInt(),
                            (fillColor.red * 255).toInt(),
                            (fillColor.green * 255).toInt(),
                            (fillColor.blue * 255).toInt(),
                        )
                    style = Paint.Style.FILL
                }
            }

        val rect = RectF(0f, 0f, element.width, element.height)

        when (element.shapeKind) {
            ShapeKind.RECTANGLE -> {
                fillPaint?.let { canvas.drawRoundRect(rect, SHAPE_CORNER_RADIUS, SHAPE_CORNER_RADIUS, it) }
                canvas.drawRoundRect(rect, SHAPE_CORNER_RADIUS, SHAPE_CORNER_RADIUS, strokePaint)
            }
            ShapeKind.CIRCLE -> {
                val cx = element.width / 2
                val cy = element.height / 2
                val radius = minOf(cx, cy)
                fillPaint?.let { canvas.drawCircle(cx, cy, radius, it) }
                canvas.drawCircle(cx, cy, radius, strokePaint)
            }
            ShapeKind.LINE -> {
                canvas.drawLine(0f, 0f, element.width, element.height, strokePaint)
            }
            ShapeKind.ARROW -> {
                canvas.drawLine(0f, 0f, element.width, element.height, strokePaint)
                val arrowSize = element.strokeWidth * ARROW_HEAD_SCALE
                val path =
                    Path().apply {
                        moveTo(element.width, element.height)
                        lineTo(element.width - arrowSize, element.height - arrowSize / 2)
                        lineTo(element.width - arrowSize / 2, element.height - arrowSize)
                        close()
                    }
                strokePaint.style = Paint.Style.FILL
                canvas.drawPath(path, strokePaint)
            }
        }
    }

    private fun resolveTypeface(fontFamily: String): Typeface =
        when (fontFamily) {
            "caveat" -> Typeface.create("cursive", Typeface.NORMAL)
            "dancing-script" -> Typeface.create("cursive", Typeface.NORMAL)
            "patrick-hand" -> Typeface.SANS_SERIF
            else -> Typeface.DEFAULT
        }

    private fun drawBackground(
        canvas: Canvas,
        background: CanvasBackground,
        region: ExportCaptureRegion,
    ) {
        val paint = Paint()
        val rect = RectF(region.x, region.y, region.x + region.width, region.y + region.height)
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
                canvas.drawRect(rect, paint)
            }
            is CanvasBackground.Gradient -> {
                val firstColor = background.stops.firstOrNull()?.let { parseColor(it.color) }
                if (firstColor != null) {
                    paint.color =
                        android.graphics.Color.argb(
                            (firstColor.alpha * 255).toInt(),
                            (firstColor.red * 255).toInt(),
                            (firstColor.green * 255).toInt(),
                            (firstColor.blue * 255).toInt(),
                        )
                    canvas.drawRect(rect, paint)
                }
            }
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap): File {
        val cacheDir = File(context.externalCacheDir, CACHE_SUBDIR)
        cacheDir.mkdirs()
        val file = File(cacheDir, "postcard_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        }
        return file
    }

    companion object {
        private const val PHOTO_EXPORT_SIZE = 200f
        private const val STICKER_EXPORT_SIZE = 80f
        private const val SHAPE_CORNER_RADIUS = 4f
        private const val ARROW_HEAD_SCALE = 4
        private const val PLACEHOLDER_STROKE_WIDTH = 2f
        private const val CACHE_SUBDIR = "postcards"
        private const val PNG_QUALITY = 100
        private const val HIGHLIGHTER_ALPHA = 0.4f
    }
}
