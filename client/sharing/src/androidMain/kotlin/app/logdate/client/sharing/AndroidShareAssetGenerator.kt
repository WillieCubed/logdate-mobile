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
import androidx.core.graphics.toColorInt
import androidx.core.util.AtomicFile
import app.logdate.shared.model.Journal
import app.logdate.util.toReadableDateShort
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.math.abs

// All in pixels — cover dimensions match the in-app AspectRatios.JOURNAL_COVER (9:16)
private const val JOURNAL_COVER_WIDTH = 480
private const val JOURNAL_COVER_HEIGHT = 853
private const val JOURNAL_COVER_CORNER_RADIUS = 44f // proportional to 16.dp at max cover width
private const val JOURNAL_COVER_PADDING = 40f
private const val QR_CODE_SIZE = 1080

// Cache file name prefixes — bump version suffix when cover rendering changes significantly
private const val CACHE_BACKGROUND = "shared_journal_background"
private const val CACHE_COVER = "shared_journal_cover_v3"
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
            if (file.exists()) {
                return@withContext fileToShareUri(file).toString()
            }
            val bitmap = generateBackgroundLayer(shareTheme)
            writeAtomically(file) { bitmap.compress(ShareAssetFormats.ASSET_COMPRESS_FORMAT, 100, it) }
            fileToShareUri(file).toString()
        }

    override suspend fun generateStickerLayer(journal: Journal): String =
        withContext(ioDispatcher) {
            val file = coverCacheFile(journal)
            if (file.exists()) {
                return@withContext fileToShareUri(file).toString()
            }
            val bitmap = generateJournalCover(journal)
            writeAtomically(file) { bitmap.compress(ShareAssetFormats.ASSET_COMPRESS_FORMAT, 100, it) }
            fileToShareUri(file).toString()
        }

    override suspend fun generateJournalQrCode(journal: Journal): String =
        withContext(ioDispatcher) {
            val file = qrCacheFile(journal)
            if (file.exists()) {
                return@withContext fileToShareUri(file).toString()
            }
            val bitmap = generateQrCodeBitmap("https://logdate.app/j/${journal.id}")
            writeAtomically(file) { bitmap.compress(ShareAssetFormats.ASSET_COMPRESS_FORMAT, 100, it) }
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

    private fun generateBackgroundLayer(shareTheme: ShareTheme): Bitmap {
        val bitmap = createBitmap(1080, 1920)
        with(Canvas(bitmap)) {
            val paint =
                Paint().apply {
                    color =
                        if (shareTheme == ShareTheme.Dark) {
                            "#11140f".toColorInt() // Surface dark
                        } else {
                            "#f7fbf1".toColorInt() // Surface light
                        }
                }
            drawRect(0f, 0f, 1080f, 1920f, paint)
        }
        return bitmap
    }

    /**
     * Renders a bitmap that matches the in-app JournalCover composable as closely as possible.
     *
     * - Shape: flat left edge, rounded right edge (book-spine aesthetic), matching JournalShape.
     * - Color: HSL-derived from journal ID, matching deriveCoverColor().
     * - Text: title (titleMedium weight) + "Last updated …" subtitle, bottom-left aligned.
     */
    private fun generateJournalCover(journal: Journal): Bitmap {
        val hue = abs(journal.id.hashCode() % 360).toFloat()
        val coverColorInt = ColorUtils.HSLToColor(floatArrayOf(hue, 0.50f, 0.80f))

        // Replicate the UI luminance-based text color logic from JournalCover.kt
        val luminance = ColorUtils.calculateLuminance(coverColorInt)
        val textColorInt =
            if (luminance > 0.5f) {
                Color.argb(222, 0, 0, 0) // Black @ 0.87 alpha
            } else {
                Color.argb(242, 255, 255, 255) // White @ 0.95 alpha
            }

        val bitmap = createBitmap(JOURNAL_COVER_WIDTH, JOURNAL_COVER_HEIGHT)
        val w = JOURNAL_COVER_WIDTH.toFloat()
        val h = JOURNAL_COVER_HEIGHT.toFloat()
        val r = JOURNAL_COVER_CORNER_RADIUS

        with(Canvas(bitmap)) {
            // Clip to book-spine shape before any drawing — eliminates corner artifacts
            // caused by anti-aliased pixels blending against transparent-black.
            val coverPath =
                Path().apply {
                    addRoundRect(
                        RectF(0f, 0f, w, h),
                        floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f),
                        Path.Direction.CW,
                    )
                }
            clipPath(coverPath)
            drawColor(coverColorInt)

            val padding = JOURNAL_COVER_PADDING
            val textWidth = (w - padding * 2).toInt()

            // Subtitle text color: 70% alpha of the title color, matching JournalCoverContent
            val subtitleAlpha = (Color.alpha(textColorInt) * 0.7f).toInt()
            val subtitleColorInt =
                Color.argb(
                    subtitleAlpha,
                    Color.red(textColorInt),
                    Color.green(textColorInt),
                    Color.blue(textColorInt),
                )

            val subtitlePaint =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = subtitleColorInt
                    textSize = 26f
                }
            val subtitleText = "Last updated ${journal.lastUpdated.toReadableDateShort()}"
            val subtitleLayout =
                StaticLayout.Builder
                    .obtain(subtitleText, 0, subtitleText.length, subtitlePaint, textWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(1)
                    .build()

            // titleMedium: 16sp, FontWeight(500) — "sans-serif-medium" maps to weight 500 on Android
            val titlePaint =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColorInt
                    textSize = 40f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
            val titleLayout =
                StaticLayout.Builder
                    .obtain(journal.title, 0, journal.title.length, titlePaint, textWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(4)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()

            // Stack from the bottom: subtitle, then title above it, both left-aligned
            val subtitleTop = h - padding - subtitleLayout.height
            val titleTop = subtitleTop - 8f - titleLayout.height

            save()
            translate(padding, titleTop)
            titleLayout.draw(this)
            restore()

            save()
            translate(padding, subtitleTop)
            subtitleLayout.draw(this)
            restore()
        }
        return bitmap
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
