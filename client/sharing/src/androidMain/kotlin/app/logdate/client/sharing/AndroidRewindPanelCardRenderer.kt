package app.logdate.client.sharing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.util.AtomicFile
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.math.abs

/**
 * Renders a non-quote Rewind panel onto a 1080×1920 portrait card for Stories-style sharing.
 *
 * The layout mirrors [AndroidRewindQuoteCardRenderer] — a title centered against a
 * hue-derived background, with optional body text and a small period label — so a Rewind's
 * mix of quote and non-quote panels feels visually consistent when shared. Per-kind layout
 * tweaks (e.g. a real photo backdrop for IMAGE) land in a follow-up; today every kind uses
 * the same card structure.
 */
class AndroidRewindPanelCardRenderer(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RewindPanelCardRenderer {
    override suspend fun render(panel: RewindPanel): String? {
        if (panel.title.isBlank()) return null
        return withContext(ioDispatcher) {
            runCatching {
                val bitmap = drawCard(panel)
                val file = cacheFile(panel)
                writeAtomically(file) { stream ->
                    bitmap.compress(ShareAssetFormats.ASSET_COMPRESS_FORMAT, ASSET_QUALITY, stream)
                }
                fileToShareUri(file).toString()
            }.onFailure { error ->
                Napier.w("Failed to render rewind panel card", error)
            }.getOrNull()
        }
    }

    private fun drawCard(panel: RewindPanel): Bitmap {
        val hue = accentHue(panel.accentSeed)
        val backgroundColor = ColorUtils.HSLToColor(floatArrayOf(hue, BG_SATURATION, BG_LIGHTNESS))
        val textColor = textColorOn(backgroundColor)
        val secondaryColor =
            Color.argb(
                (Color.alpha(textColor) * SECONDARY_ALPHA_FACTOR).toInt(),
                Color.red(textColor),
                Color.green(textColor),
                Color.blue(textColor),
            )

        val bitmap = createBitmap(CARD_WIDTH, CARD_HEIGHT)
        val canvas = Canvas(bitmap).apply { drawColor(backgroundColor) }

        val textArea = (CARD_WIDTH - HORIZONTAL_PADDING * 2).toInt()

        val titleLayout =
            buildLayout(
                text = panel.title,
                color = textColor,
                textSize = TITLE_TEXT_SIZE,
                width = textArea,
                typeface = TITLE_TYPEFACE,
                maxLines = MAX_TITLE_LINES,
            )

        val bodyLayout =
            panel.body?.takeIf { it.isNotBlank() }?.let {
                buildLayout(
                    text = it,
                    color = textColor,
                    textSize = BODY_TEXT_SIZE,
                    width = textArea,
                    typeface = BODY_TYPEFACE,
                    maxLines = MAX_BODY_LINES,
                )
            }

        val periodLayout =
            panel.periodLabel?.takeIf { it.isNotBlank() }?.let {
                buildLayout(
                    text = it,
                    color = secondaryColor,
                    textSize = PERIOD_TEXT_SIZE,
                    width = textArea,
                    typeface = BODY_TYPEFACE,
                    maxLines = 1,
                )
            }

        // Vertical layout: title block centered, body just below, period label pinned to the
        // bottom margin. Falls back gracefully when body or period is missing.
        val periodHeight = periodLayout?.height ?: 0
        val periodGap = if (periodLayout != null) PERIOD_GAP else 0f
        val titleBodyGap = if (bodyLayout != null) BODY_GAP else 0f
        val totalContentHeight =
            titleLayout.height +
                titleBodyGap +
                (bodyLayout?.height ?: 0) +
                periodGap +
                periodHeight
        val verticalSlack = CARD_HEIGHT - VERTICAL_PADDING * 2 - totalContentHeight
        val topOffset = (VERTICAL_PADDING + verticalSlack / 2).coerceAtLeast(VERTICAL_PADDING)

        canvas.save()
        canvas.translate(HORIZONTAL_PADDING, topOffset)
        titleLayout.draw(canvas)
        if (bodyLayout != null) {
            canvas.translate(0f, titleLayout.height + titleBodyGap)
            bodyLayout.draw(canvas)
        }
        canvas.restore()

        if (periodLayout != null) {
            canvas.save()
            canvas.translate(HORIZONTAL_PADDING, CARD_HEIGHT - VERTICAL_PADDING - periodHeight.toFloat())
            periodLayout.draw(canvas)
            canvas.restore()
        }

        return bitmap
    }

    private fun buildLayout(
        text: String,
        color: Int,
        textSize: Float,
        width: Int,
        typeface: Typeface,
        maxLines: Int,
    ): StaticLayout {
        val paint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.textSize = textSize
                this.typeface = typeface
                this.isSubpixelText = true
            }
        return StaticLayout
            .Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
            .setIncludePad(false)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(maxLines)
            .build()
    }

    private fun accentHue(seed: Int): Float {
        val rotated = (abs(seed) + NEUTRAL_HUE_SEED) % HUE_DEGREES
        return rotated.toFloat()
    }

    private fun textColorOn(background: Int): Int {
        val luminance = ColorUtils.calculateLuminance(background)
        return if (luminance > LUMINANCE_THRESHOLD) {
            Color.argb(TEXT_ON_LIGHT_ALPHA, 24, 22, 30)
        } else {
            Color.argb(TEXT_ON_DARK_ALPHA, 252, 250, 248)
        }
    }

    private fun cacheFile(panel: RewindPanel): File {
        val key = abs(panel.accentSeed).toString(16) + "_" + panel.kind.name.lowercase()
        return context.cacheDir.resolve("$CACHE_PREFIX$key.${ShareAssetFormats.ASSET_FILE_EXT}")
    }

    private fun fileToShareUri(file: File) =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )

    private fun writeAtomically(
        dest: File,
        block: (OutputStream) -> Unit,
    ) {
        val atomic = AtomicFile(dest)
        val stream = atomic.startWrite()
        try {
            block(stream)
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }

    private companion object {
        private const val CARD_WIDTH = 1080
        private const val CARD_HEIGHT = 1920

        private const val HORIZONTAL_PADDING = 96f
        private const val VERTICAL_PADDING = 160f

        private const val TITLE_TEXT_SIZE = 88f
        private const val MAX_TITLE_LINES = 4
        private const val BODY_GAP = 36f

        private const val BODY_TEXT_SIZE = 48f
        private const val MAX_BODY_LINES = 12

        private const val PERIOD_TEXT_SIZE = 32f
        private const val PERIOD_GAP = 48f
        private const val SECONDARY_ALPHA_FACTOR = 0.7f
        private const val LINE_SPACING_MULTIPLIER = 1.25f

        private const val HUE_DEGREES = 360
        private const val BG_SATURATION = 0.30f
        private const val BG_LIGHTNESS = 0.88f
        private const val LUMINANCE_THRESHOLD = 0.5f
        private const val TEXT_ON_LIGHT_ALPHA = 222
        private const val TEXT_ON_DARK_ALPHA = 242
        private const val NEUTRAL_HUE_SEED = 210

        private const val ASSET_QUALITY = 100
        private const val CACHE_PREFIX = "rewind_panel_card_"

        private val TITLE_TYPEFACE: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private val BODY_TYPEFACE: Typeface = Typeface.create("serif", Typeface.NORMAL)
    }
}
