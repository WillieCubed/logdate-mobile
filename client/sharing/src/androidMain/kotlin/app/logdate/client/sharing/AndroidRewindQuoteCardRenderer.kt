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
 * Renders a rewind text panel onto a 1080×1920 portrait card suitable for Stories-style sharing.
 *
 * The card draws the user's own words centered in the frame against a hue-derived background that
 * varies from card to card so consecutive moments look distinct. There is no LogDate branding,
 * no template prose, and no decorative chrome — the user's text is the entire content.
 */
class AndroidRewindQuoteCardRenderer(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RewindQuoteCardRenderer {
    override suspend fun render(quote: RewindQuote): String? {
        if (quote.text.isBlank()) return null
        val trimmed = quote.copy(text = quote.text.trim())
        return withContext(ioDispatcher) {
            runCatching {
                val bitmap = drawCard(trimmed)
                val file = cacheFile(trimmed)
                writeAtomically(file) { stream ->
                    bitmap.compress(ShareAssetFormats.ASSET_COMPRESS_FORMAT, ASSET_QUALITY, stream)
                }
                fileToShareUri(file).toString()
            }.onFailure { error ->
                Napier.w("Failed to render rewind quote card", error)
            }.getOrNull()
        }
    }

    private fun drawCard(quote: RewindQuote): Bitmap {
        val hue = accentHue(quote.accentSeed)
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

        // Quote text shrinks until it fits inside the available height. Long entries get smaller
        // type so the whole moment lands on a single card without ellipsis.
        val bodyLayout = fitTextLayout(quote.text, textColor, textArea)

        val dateLayout =
            quote.dateLabel?.takeIf { it.isNotBlank() }?.let {
                buildLayout(
                    text = it,
                    color = secondaryColor,
                    textSize = DATE_TEXT_SIZE,
                    width = textArea,
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL),
                )
            }

        val gap = if (dateLayout != null) DATE_GAP else 0f
        val totalHeight = bodyLayout.height + gap + (dateLayout?.height ?: 0)
        val bodyTop = ((CARD_HEIGHT - totalHeight) / 2f).coerceAtLeast(VERTICAL_PADDING)

        canvas.save()
        canvas.translate(HORIZONTAL_PADDING, bodyTop)
        bodyLayout.draw(canvas)
        canvas.restore()

        if (dateLayout != null) {
            canvas.save()
            canvas.translate(HORIZONTAL_PADDING, bodyTop + bodyLayout.height + gap)
            dateLayout.draw(canvas)
            canvas.restore()
        }

        return bitmap
    }

    private fun fitTextLayout(
        text: String,
        color: Int,
        width: Int,
    ): StaticLayout {
        val maxBodyHeight = CARD_HEIGHT - VERTICAL_PADDING * 2
        var size = BODY_TEXT_SIZE_MAX
        var layout = buildLayout(text, color, size, width, BODY_TYPEFACE)
        while (layout.height > maxBodyHeight && size > BODY_TEXT_SIZE_MIN) {
            size = (size - BODY_TEXT_SIZE_STEP).coerceAtLeast(BODY_TEXT_SIZE_MIN)
            layout = buildLayout(text, color, size, width, BODY_TYPEFACE)
        }
        return layout
    }

    private fun buildLayout(
        text: String,
        color: Int,
        textSize: Float,
        width: Int,
        typeface: Typeface,
    ): StaticLayout {
        val paint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.textSize = textSize
                this.typeface = typeface
            }
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
            .setIncludePad(false)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(MAX_BODY_LINES)
            .build()
    }

    private fun accentHue(accentSeed: Int): Float {
        val seed = if (accentSeed == 0) NEUTRAL_HUE_SEED else accentSeed
        return abs(seed % HUE_DEGREES).toFloat()
    }

    private fun textColorOn(background: Int): Int =
        if (ColorUtils.calculateLuminance(background) > LUMINANCE_THRESHOLD) {
            Color.argb(TEXT_ON_LIGHT_ALPHA, 0, 0, 0)
        } else {
            Color.argb(TEXT_ON_DARK_ALPHA, 255, 255, 255)
        }

    private fun cacheFile(quote: RewindQuote): File {
        // Cache key derived from content so re-shares of the same moment hit the same file.
        val key =
            (quote.text.hashCode() xor (quote.dateLabel?.hashCode() ?: 0) xor quote.accentSeed)
                .toString()
                .replace('-', '_')
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
        // Card dimensions match the Stories aspect ratio used elsewhere in the share pipeline.
        private const val CARD_WIDTH = 1080
        private const val CARD_HEIGHT = 1920

        private const val HORIZONTAL_PADDING = 96f
        private const val VERTICAL_PADDING = 160f

        private const val BODY_TEXT_SIZE_MAX = 80f
        private const val BODY_TEXT_SIZE_MIN = 40f
        private const val BODY_TEXT_SIZE_STEP = 4f
        private const val MAX_BODY_LINES = 16
        private const val LINE_SPACING_MULTIPLIER = 1.25f

        private const val DATE_TEXT_SIZE = 32f
        private const val DATE_GAP = 48f
        private const val SECONDARY_ALPHA_FACTOR = 0.7f

        // HSL palette tuned to feel quiet — strong saturation makes long quotes look noisy.
        private const val HUE_DEGREES = 360
        private const val BG_SATURATION = 0.30f
        private const val BG_LIGHTNESS = 0.88f
        private const val LUMINANCE_THRESHOLD = 0.5f
        private const val TEXT_ON_LIGHT_ALPHA = 222
        private const val TEXT_ON_DARK_ALPHA = 242

        private const val NEUTRAL_HUE_SEED = 210

        private const val ASSET_QUALITY = 100
        private const val CACHE_PREFIX = "rewind_quote_card_"

        private val BODY_TYPEFACE: Typeface = Typeface.create("serif", Typeface.NORMAL)
    }
}
