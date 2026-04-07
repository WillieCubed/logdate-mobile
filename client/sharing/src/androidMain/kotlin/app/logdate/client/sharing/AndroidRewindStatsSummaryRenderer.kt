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
 * Renders a [RewindStatsSummary] onto a 1080×1920 portrait card suitable for Stories-style
 * sharing. The card layout is title → subtitle → counters → highlights, with each section
 * sized so a typical weekly rewind fits the available height without truncation.
 *
 * Like the quote card renderer this draws no LogDate branding and no template prose: every
 * piece of text on the card came from the user's own data and was already localized by the
 * time it reached this class.
 */
class AndroidRewindStatsSummaryRenderer(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RewindStatsSummaryRenderer {
    override suspend fun render(summary: RewindStatsSummary): String? {
        if (summary.title.isBlank()) return null
        return withContext(ioDispatcher) {
            runCatching {
                val bitmap = drawCard(summary)
                val file = cacheFile(summary)
                writeAtomically(file) { stream ->
                    bitmap.compress(ShareAssetFormats.ASSET_COMPRESS_FORMAT, ASSET_QUALITY, stream)
                }
                fileToShareUri(file).toString()
            }.onFailure { error ->
                Napier.w("Failed to render rewind stats summary card", error)
            }.getOrNull()
        }
    }

    private fun drawCard(summary: RewindStatsSummary): Bitmap {
        val hue = accentHue(summary.accentSeed)
        val backgroundColor = ColorUtils.HSLToColor(floatArrayOf(hue, BG_SATURATION, BG_LIGHTNESS))
        val textColor = textColorOn(backgroundColor)
        val secondaryColor = withAlphaFactor(textColor, SECONDARY_ALPHA_FACTOR)

        val bitmap = createBitmap(CARD_WIDTH, CARD_HEIGHT)
        val canvas = Canvas(bitmap).apply { drawColor(backgroundColor) }

        val textArea = (CARD_WIDTH - HORIZONTAL_PADDING * 2).toInt()

        val titleLayout =
            buildLayout(
                text = summary.title,
                color = textColor,
                textSize = TITLE_TEXT_SIZE,
                width = textArea,
                typeface = TITLE_TYPEFACE,
                maxLines = TITLE_MAX_LINES,
            )
        val subtitleLayout =
            buildLayout(
                text = summary.subtitle,
                color = secondaryColor,
                textSize = SUBTITLE_TEXT_SIZE,
                width = textArea,
                typeface = SECONDARY_TYPEFACE,
                maxLines = 1,
            )

        var cursor = VERTICAL_PADDING
        canvas.drawAt(HORIZONTAL_PADDING, cursor) { titleLayout.draw(this) }
        cursor += titleLayout.height + TITLE_SUBTITLE_GAP
        canvas.drawAt(HORIZONTAL_PADDING, cursor) { subtitleLayout.draw(this) }
        cursor += subtitleLayout.height + SECTION_GAP

        if (summary.counters.isNotEmpty()) {
            cursor = drawCounters(canvas, summary.counters, textColor, secondaryColor, cursor)
            cursor += SECTION_GAP
        }

        if (summary.highlights.isNotEmpty()) {
            drawHighlights(canvas, summary.highlights, textColor, secondaryColor, cursor, textArea)
        }

        return bitmap
    }

    private fun drawCounters(
        canvas: Canvas,
        counters: List<RewindStatsSummary.Counter>,
        primary: Int,
        secondary: Int,
        top: Float,
    ): Float {
        // Lay counters out in a row; each counter is a big number above a small label.
        val columnCount = counters.size.coerceAtMost(MAX_COUNTER_COLUMNS)
        val available = CARD_WIDTH - HORIZONTAL_PADDING * 2
        val columnWidth = available / columnCount
        val numberPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primary
                textSize = COUNTER_NUMBER_SIZE
                typeface = TITLE_TYPEFACE
                textAlign = Paint.Align.CENTER
            }
        val labelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondary
                textSize = COUNTER_LABEL_SIZE
                typeface = SECONDARY_TYPEFACE
                textAlign = Paint.Align.CENTER
            }

        counters.take(columnCount).forEachIndexed { index, counter ->
            val centerX = HORIZONTAL_PADDING + columnWidth * (index + 0.5f)
            val numberY = top + COUNTER_NUMBER_SIZE
            canvas.drawText(counter.count.toString(), centerX, numberY, numberPaint)

            val labelY = numberY + COUNTER_NUMBER_LABEL_GAP + COUNTER_LABEL_SIZE
            canvas.drawText(counter.label, centerX, labelY, labelPaint)
        }

        return top + COUNTER_NUMBER_SIZE + COUNTER_NUMBER_LABEL_GAP + COUNTER_LABEL_SIZE
    }

    private fun drawHighlights(
        canvas: Canvas,
        highlights: List<RewindStatsSummary.Highlight>,
        primary: Int,
        secondary: Int,
        top: Float,
        textArea: Int,
    ) {
        var cursor = top
        highlights.take(MAX_HIGHLIGHT_LINES).forEach { highlight ->
            val headingLayout =
                buildLayout(
                    text = highlight.heading,
                    color = secondary,
                    textSize = HIGHLIGHT_HEADING_SIZE,
                    width = textArea,
                    typeface = SECONDARY_TYPEFACE,
                    maxLines = 1,
                )
            val valueLayout =
                buildLayout(
                    text = highlight.value,
                    color = primary,
                    textSize = HIGHLIGHT_VALUE_SIZE,
                    width = textArea,
                    typeface = TITLE_TYPEFACE,
                    maxLines = 2,
                )
            canvas.drawAt(HORIZONTAL_PADDING, cursor) { headingLayout.draw(this) }
            cursor += headingLayout.height + HIGHLIGHT_HEADING_VALUE_GAP
            canvas.drawAt(HORIZONTAL_PADDING, cursor) { valueLayout.draw(this) }
            cursor += valueLayout.height + HIGHLIGHT_GAP
        }
    }

    private inline fun Canvas.drawAt(
        x: Float,
        y: Float,
        block: Canvas.() -> Unit,
    ) {
        save()
        translate(x, y)
        block()
        restore()
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
            }
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
            .setIncludePad(false)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(maxLines)
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

    private fun withAlphaFactor(
        color: Int,
        factor: Float,
    ): Int =
        Color.argb(
            (Color.alpha(color) * factor).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )

    private fun cacheFile(summary: RewindStatsSummary): File {
        // Cache key derived from content so re-shares of the same summary hit the same file.
        val countersKey = summary.counters.joinToString("/") { "${it.label}=${it.count}" }
        val highlightsKey = summary.highlights.joinToString("/") { "${it.heading}=${it.value}" }
        val key =
            (
                summary.title.hashCode() xor
                    summary.subtitle.hashCode() xor
                    countersKey.hashCode() xor
                    highlightsKey.hashCode() xor
                    summary.accentSeed
            ).toString().replace('-', '_')
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
        private const val VERTICAL_PADDING = 200f

        private const val TITLE_TEXT_SIZE = 78f
        private const val TITLE_MAX_LINES = 3
        private const val SUBTITLE_TEXT_SIZE = 36f
        private const val TITLE_SUBTITLE_GAP = 16f
        private const val SECTION_GAP = 96f

        private const val MAX_COUNTER_COLUMNS = 4
        private const val COUNTER_NUMBER_SIZE = 140f
        private const val COUNTER_LABEL_SIZE = 32f
        private const val COUNTER_NUMBER_LABEL_GAP = 16f

        private const val MAX_HIGHLIGHT_LINES = 4
        private const val HIGHLIGHT_HEADING_SIZE = 28f
        private const val HIGHLIGHT_VALUE_SIZE = 56f
        private const val HIGHLIGHT_HEADING_VALUE_GAP = 8f
        private const val HIGHLIGHT_GAP = 56f

        private const val LINE_SPACING_MULTIPLIER = 1.2f
        private const val SECONDARY_ALPHA_FACTOR = 0.7f

        private const val HUE_DEGREES = 360
        private const val BG_SATURATION = 0.32f
        private const val BG_LIGHTNESS = 0.86f
        private const val LUMINANCE_THRESHOLD = 0.5f
        private const val TEXT_ON_LIGHT_ALPHA = 222
        private const val TEXT_ON_DARK_ALPHA = 242

        private const val NEUTRAL_HUE_SEED = 165

        private const val ASSET_QUALITY = 100
        private const val CACHE_PREFIX = "rewind_stats_summary_"

        private val TITLE_TYPEFACE: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private val SECONDARY_TYPEFACE: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
}
