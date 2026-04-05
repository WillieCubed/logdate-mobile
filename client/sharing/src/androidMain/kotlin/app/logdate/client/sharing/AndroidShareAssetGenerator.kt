package app.logdate.client.sharing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.util.AtomicFile
import app.logdate.shared.model.Journal
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

// All in pixels
private const val JOURNAL_CORNER_RADIUS = 16f
private const val JOURNAL_COVER_HEIGHT = 320f
private const val JOURNAL_COVER_WIDTH = 180f
private const val QR_CODE_SIZE = 1080

/**
 * A utility that generates assets for sharing content to external apps.
 */
class AndroidShareAssetGenerator(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ShareAssetInterface {
    /**
     * Creates and returns a background layer for the given [journal].
     *
     * This layer is used to overlay the journal cover on top of a background image. It is stored
     * in the cache directory and can be shared with other apps.
     */
    override suspend fun generateBackgroundLayer(
        journal: Journal,
        shareTheme: ShareTheme,
    ): String =
        withContext(ioDispatcher) {
            val file =
                context.cacheDir.resolve(
                    "shared_journal_background_${journal.id}_${shareTheme.cacheKey}.${ShareAssetFormats.ASSET_FILE_EXT}",
                )
            if (file.exists()) {
                return@withContext fileToShareUri(file).toString()
            }
            val bitmap = generateBackgroundLayer(shareTheme)
            writeAtomically(file) { bitmap.compress(ShareAssetFormats.ASSET_COMPRESS_FORMAT, 100, it) }
            fileToShareUri(file).toString()
        }

    /**
     * Creates and returns a sticker layer for the given [journal].
     *
     * This layer is used to overlay the journal cover on top of a background image. It is stored
     * in the cache directory and can be shared with other apps. Assume that this sticker layer
     * may have a transparent background.
     */
    override suspend fun generateStickerLayer(
        journal: Journal,
        theme: ShareTheme,
    ): String =
        withContext(ioDispatcher) {
            val file = context.cacheDir.resolve("shared_journal_cover_${journal.id}_${theme.cacheKey}.${ShareAssetFormats.ASSET_FILE_EXT}")
            if (file.exists()) {
                return@withContext fileToShareUri(file).toString()
            }
            val bitmap = generateJournalCover(journal.title, theme)
            writeAtomically(file) { bitmap.compress(ShareAssetFormats.ASSET_COMPRESS_FORMAT, 100, it) }
            fileToShareUri(file).toString()
        }

    override suspend fun generateJournalQrCode(journal: Journal): String =
        withContext(ioDispatcher) {
            val file = context.cacheDir.resolve("shared_journal_qr_${journal.id}.${ShareAssetFormats.ASSET_FILE_EXT}")
            if (file.exists()) {
                return@withContext fileToShareUri(file).toString()
            }
            val bitmap = generateQrCodeBitmap("https://logdate.app/j/${journal.id}")
            writeAtomically(file) { bitmap.compress(ShareAssetFormats.ASSET_COMPRESS_FORMAT, 100, it) }
            fileToShareUri(file).toString()
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

    private fun generateJournalCover(
        journalName: String,
        theme: ShareTheme,
    ): Bitmap {
        val bitmap = createBitmap(180, JOURNAL_COVER_HEIGHT.toInt())
        with(Canvas(bitmap)) {
            val paint =
                Paint().apply {
                    color =
                        if (theme == ShareTheme.Light) {
                            "#bcebef".toColorInt()
                        } else {
                            "#1E4D51".toColorInt()
                        }

                    textSize = 20f
                }
            val rightSideOffset = JOURNAL_COVER_WIDTH - JOURNAL_CORNER_RADIUS
            drawRect(0f, 0f, rightSideOffset, JOURNAL_COVER_HEIGHT, paint)
            drawRect(
                rightSideOffset,
                JOURNAL_CORNER_RADIUS,
                JOURNAL_CORNER_RADIUS,
                JOURNAL_COVER_HEIGHT - JOURNAL_CORNER_RADIUS * 2,
                paint,
            )
            // Top right corner
            drawCircle(
                rightSideOffset,
                JOURNAL_CORNER_RADIUS,
                JOURNAL_CORNER_RADIUS,
                paint,
            )
            // Bottom right corner
            drawCircle(
                rightSideOffset,
                JOURNAL_COVER_HEIGHT - JOURNAL_CORNER_RADIUS,
                JOURNAL_CORNER_RADIUS,
                paint,
            )
            val titlePaint =
                Paint().apply {
                    color = "#002022".toColorInt()
                    textSize = 20f
                }
            // TODO: Handle truncating text if it doesn't fit
            drawText(journalName, 10f, 20f, titlePaint)
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
                    if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
                )
            }
        }
        return bitmap
    }
}
