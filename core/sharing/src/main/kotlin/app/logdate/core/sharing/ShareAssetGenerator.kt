package app.logdate.core.sharing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.net.toUri
import app.logdate.model.Journal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * A utility that generates assets for sharing content to external apps.
 */
class ShareAssetGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // TODO: Implement more robust check to ensure we're not generating the same asset multiple times
    private var cachedJournalId: String? = null

    /**
     * Creates and returns a background layer for the given [journal].
     *
     * This layer is used to overlay the journal cover on top of a background image. It is stored
     * in the cache directory and can be shared with other apps.
     */
    fun generateBackgroundLayer(journal: Journal, shareTheme: ShareTheme): Uri {
        if (cachedJournalId == journal.id) {
            return context.cacheDir.resolve("shared_journal_background_${journal.id}.png").toUri()
        }
        val bitmap = generateBackgroundLayer(shareTheme)
        val file = context.cacheDir.resolve("shared_journal_background_${journal.id}.png")
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
        cachedJournalId = journal.id
        return file.toUri()
    }

    /**
     * Creates and returns a sticker layer for the given [journal].
     *
     * This layer is used to overlay the journal cover on top of a background image. It is stored
     * in the cache directory and can be shared with other apps. Assume that this sticker layer
     * may have a transparent background.
     */
    fun generateStickerLayer(journal: Journal, theme: ShareTheme): Uri {
        if (cachedJournalId == journal.id) {
            return context.cacheDir.resolve("shared_journal_cover_${journal.id}.png").toUri()
        }
        val bitmap = generateJournalCover(journal.title, theme)
        val file = context.cacheDir.resolve("shared_journal_cover_${journal.id}.png")
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
        cachedJournalId = journal.id
        return file.toUri()
    }

    private fun generateBackgroundLayer(shareTheme: ShareTheme = ShareTheme.Light): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        with(Canvas(bitmap)) {
            val paint = Paint().apply {
                color = if (shareTheme == ShareTheme.Dark) {
                    Color.parseColor("#11140f") // Surface dark
                } else {
                    Color.parseColor("#f7fbf1") // Surface light
                }
            }
            drawRect(0f, 0f, 1080f, 1920f, paint)
        }
        return bitmap
    }

    private fun generateJournalCover(
        journalName: String,
        theme: ShareTheme = ShareTheme.Light,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(180, JOURNAL_COVER_HEIGHT.toInt(), Bitmap.Config.ARGB_8888)
        with(Canvas(bitmap)) {
            val paint = Paint().apply {
                color = if (theme == ShareTheme.Light) {
                    Color.parseColor("#bcebef")
                } else {
                    Color.parseColor("#1E4D51")
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
                paint
            )
            // Top right corner
            drawCircle(
                rightSideOffset, JOURNAL_CORNER_RADIUS, JOURNAL_CORNER_RADIUS, paint
            )
            // Bottom right corner
            drawCircle(
                rightSideOffset,
                JOURNAL_COVER_HEIGHT - JOURNAL_CORNER_RADIUS,
                JOURNAL_CORNER_RADIUS,
                paint
            )
            val titlePaint = Paint().apply {
                color = Color.parseColor("#002022")
                textSize = 20f
            }
            // TODO: Handle truncating text if it doesn't fit
            drawText(journalName, 10f, 20f, titlePaint)
        }
        return bitmap
    }
}

// All in pixels
private const val JOURNAL_CORNER_RADIUS = 16f
private const val JOURNAL_COVER_HEIGHT = 320f
private const val JOURNAL_COVER_WIDTH = 180f

