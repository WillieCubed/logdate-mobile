package app.logdate.client.sharing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.util.AtomicFile
import app.logdate.shared.model.Journal
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.math.abs

// Cover dimensions — match the in-app AspectRatios.JOURNAL_COVER (9:16)
private const val JOURNAL_COVER_WIDTH = 480
private const val JOURNAL_COVER_HEIGHT = 853
private const val JOURNAL_COVER_CORNER_RADIUS = 44f
private const val JOURNAL_COVER_PADDING = 40f

// Cover text
private const val TITLE_TEXT_SIZE = 40f
private const val SUBTITLE_TEXT_SIZE = 26f
private const val TITLE_MAX_LINES = 4
private const val SUBTITLE_MAX_LINES = 2
private const val TITLE_SUBTITLE_GAP = 8f
private const val SUBTITLE_ALPHA_FACTOR = 0.7f
private const val DEFAULT_TAGLINE = "logdate.app"

// HSL values — match deriveCoverColor() in JournalCover.kt
private const val HUE_DEGREES = 360
private const val COVER_SATURATION = 0.50f
private const val COVER_LIGHTNESS = 0.80f
private const val LUMINANCE_THRESHOLD = 0.5f
private const val TEXT_ON_LIGHT_ALPHA = 222 // 0.87 × 255
private const val TEXT_ON_DARK_ALPHA = 242 // 0.95 × 255

// Background HSL — desaturated tint of the journal's hue
private const val BG_LIGHT_SATURATION = 0.25f
private const val BG_LIGHT_LIGHTNESS = 0.92f
private const val BG_DARK_SATURATION = 0.15f
private const val BG_DARK_LIGHTNESS = 0.12f

// Story background dimensions (used by system share sheet)
private const val STORY_BACKGROUND_WIDTH = 1080
private const val STORY_BACKGROUND_HEIGHT = 1920

private const val QR_CODE_SIZE = 1080
private const val PNG_QUALITY = 100

// Cache file name prefixes — no versioning; files are always regenerated on share
private const val CACHE_BACKGROUND = "shared_journal_bg"
private const val CACHE_COVER = "shared_journal_cover"
private const val CACHE_QR = "shared_journal_qr"

/**
 * A utility that generates assets for sharing content to external apps.
 */
class AndroidShareAssetGenerator(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ShareAssetInterface {
    override suspend fun generateBackgroundLayer(
        journal: Journal,
        shareTheme: ShareTheme,
    ): String =
        withContext(ioDispatcher) {
            val file = backgroundCacheFile(journal, shareTheme)
            val bitmap = renderBackground(journal, shareTheme)
            writeAtomically(file) { bitmap.compress(ShareAssetFormats.ASSET_COMPRESS_FORMAT, PNG_QUALITY, it) }
            fileToShareUri(file).toString()
        }

    override suspend fun generateStickerLayer(journal: Journal): String =
        withContext(ioDispatcher) {
            val file = coverCacheFile(journal)
            val bitmap = generateJournalCover(journal)
            writeAtomically(file) { bitmap.compress(ShareAssetFormats.ASSET_COMPRESS_FORMAT, PNG_QUALITY, it) }
            fileToShareUri(file).toString()
        }

    override suspend fun generateJournalQrCode(journal: Journal): String =
        withContext(ioDispatcher) {
            val file = qrCacheFile(journal)
            val bitmap = generateQrCodeBitmap("https://logdate.app/j/${journal.id}")
            writeAtomically(file) { bitmap.compress(ShareAssetFormats.ASSET_COMPRESS_FORMAT, PNG_QUALITY, it) }
            fileToShareUri(file).toString()
        }

    private fun backgroundCacheFile(
        journal: Journal,
        theme: ShareTheme,
    ) = cacheFile("${CACHE_BACKGROUND}_${journal.id}_${theme.cacheKey}")

    private fun coverCacheFile(journal: Journal) = cacheFile("${CACHE_COVER}_${journal.id}")

    private fun qrCacheFile(journal: Journal) = cacheFile("${CACHE_QR}_${journal.id}")

    private fun cacheFile(name: String) = context.cacheDir.resolve("$name.${ShareAssetFormats.ASSET_FILE_EXT}")

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

    private fun journalHue(journal: Journal): Float = abs(journal.id.hashCode() % HUE_DEGREES).toFloat()

    private fun renderBackground(
        journal: Journal,
        shareTheme: ShareTheme,
    ): Bitmap {
        val hue = journalHue(journal)
        val bgColor =
            if (shareTheme == ShareTheme.Dark) {
                ColorUtils.HSLToColor(floatArrayOf(hue, BG_DARK_SATURATION, BG_DARK_LIGHTNESS))
            } else {
                ColorUtils.HSLToColor(floatArrayOf(hue, BG_LIGHT_SATURATION, BG_LIGHT_LIGHTNESS))
            }
        val bitmap = createBitmap(STORY_BACKGROUND_WIDTH, STORY_BACKGROUND_HEIGHT)
        with(Canvas(bitmap)) {
            drawColor(bgColor)
        }
        return bitmap
    }

    /**
     * Renders a bitmap that matches the in-app JournalCover composable as closely as possible.
     *
     * - Shape: flat left edge, rounded right edge (book-spine aesthetic), matching JournalShape.
     * - Color: HSL-derived from journal ID, matching deriveCoverColor().
     * - Text: title + tagline, bottom-left aligned.
     */
    private fun generateJournalCover(journal: Journal): Bitmap {
        val coverColorInt = coverColor(journal)
        val textColorInt = textColorFor(coverColorInt)

        val bitmap = createBitmap(JOURNAL_COVER_WIDTH, JOURNAL_COVER_HEIGHT)
        val canvas = Canvas(bitmap)

        val coverPath =
            bookSpinePath(
                JOURNAL_COVER_WIDTH.toFloat(),
                JOURNAL_COVER_HEIGHT.toFloat(),
                JOURNAL_COVER_CORNER_RADIUS,
            )
        canvas.drawPath(coverPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = coverColorInt })
        canvas.clipPath(coverPath)

        val tagline = journal.description.ifBlank { DEFAULT_TAGLINE }
        drawCoverText(canvas, journal.title, tagline, textColorInt)

        return bitmap
    }

    private fun coverColor(journal: Journal): Int =
        ColorUtils.HSLToColor(floatArrayOf(journalHue(journal), COVER_SATURATION, COVER_LIGHTNESS))

    private fun textColorFor(backgroundColor: Int): Int {
        val luminance = ColorUtils.calculateLuminance(backgroundColor)
        return if (luminance > LUMINANCE_THRESHOLD) {
            Color.argb(TEXT_ON_LIGHT_ALPHA, 0, 0, 0)
        } else {
            Color.argb(TEXT_ON_DARK_ALPHA, 255, 255, 255)
        }
    }

    private fun bookSpinePath(
        width: Float,
        height: Float,
        cornerRadius: Float,
    ): Path =
        Path().apply {
            addRoundRect(
                RectF(0f, 0f, width, height),
                floatArrayOf(0f, 0f, cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f),
                Path.Direction.CW,
            )
        }

    private fun drawCoverText(
        canvas: Canvas,
        title: String,
        subtitle: String,
        textColor: Int,
    ) {
        val w = JOURNAL_COVER_WIDTH.toFloat()
        val h = JOURNAL_COVER_HEIGHT.toFloat()
        val textWidth = (w - JOURNAL_COVER_PADDING * 2).toInt()

        val subtitleAlpha = (Color.alpha(textColor) * SUBTITLE_ALPHA_FACTOR).toInt()
        val subtitleColor =
            Color.argb(
                subtitleAlpha,
                Color.red(textColor),
                Color.green(textColor),
                Color.blue(textColor),
            )

        val subtitleLayout =
            buildTextLayout(
                subtitle,
                subtitleColor,
                SUBTITLE_TEXT_SIZE,
                textWidth,
                SUBTITLE_MAX_LINES,
            )
        val titleLayout =
            buildTextLayout(
                title,
                textColor,
                TITLE_TEXT_SIZE,
                textWidth,
                TITLE_MAX_LINES,
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL),
            )

        val subtitleTop = h - JOURNAL_COVER_PADDING - subtitleLayout.height
        val titleTop = subtitleTop - TITLE_SUBTITLE_GAP - titleLayout.height

        canvas.withTranslation(JOURNAL_COVER_PADDING, titleTop) { titleLayout.draw(this) }
        canvas.withTranslation(JOURNAL_COVER_PADDING, subtitleTop) { subtitleLayout.draw(this) }
    }

    private fun buildTextLayout(
        text: String,
        color: Int,
        size: Float,
        width: Int,
        maxLines: Int,
        typeface: Typeface? = null,
    ): StaticLayout {
        val paint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface?.let { this.typeface = it }
            }
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    private inline fun Canvas.withTranslation(
        x: Float,
        y: Float,
        block: Canvas.() -> Unit,
    ) {
        save()
        translate(x, y)
        block()
        restore()
    }

    private fun generateQrCodeBitmap(content: String): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE)
        val bitmap = createBitmap(QR_CODE_SIZE, QR_CODE_SIZE)
        for (x in 0 until QR_CODE_SIZE) {
            for (y in 0 until QR_CODE_SIZE) {
                bitmap.setPixel(
                    x,
                    y,
                    if (matrix[x, y]) Color.BLACK else Color.WHITE,
                )
            }
        }
        return bitmap
    }
}
