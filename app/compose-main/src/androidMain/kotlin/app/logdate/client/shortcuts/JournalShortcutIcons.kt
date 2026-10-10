package app.logdate.client.shortcuts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.IconCompat
import app.logdate.feature.journals.ui.deriveCoverColor
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * The icon a journal wears outside the app: its cover photo when it has one, otherwise the same
 * colored monogram the in-app journal cover uses. Share targets and journals pinned to the home
 * screen both use it, so a journal looks the same everywhere.
 */
class JournalShortcutIcons(
    private val context: Context,
    private val imageLoader: ImageLoader,
) {
    suspend fun icon(
        journalId: Uuid,
        title: String,
        coverImageUri: String?,
    ): IconCompat {
        val cover = coverImageUri?.let { loadCover(it) }
        return IconCompat.createWithAdaptiveBitmap(cover ?: monogram(journalId, title))
    }

    private suspend fun loadCover(uri: String): Bitmap? =
        runCatching {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(uri)
                    .size(ICON_PX)
                    .allowHardware(false)
                    .build()
            val result = imageLoader.execute(request)
            (result as? SuccessResult)?.image?.let { it as? BitmapImage }?.bitmap
        }.onFailure { error ->
            Napier.w("Failed to load journal cover for a shortcut icon: $uri", error)
        }.getOrNull()

    private fun monogram(
        journalId: Uuid,
        title: String,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = deriveCoverColor(journalId).toArgb() }
        canvas.drawRect(0f, 0f, ICON_PX.toFloat(), ICON_PX.toFloat(), background)

        val letter =
            title
                .trim()
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                .orEmpty()
        if (letter.isNotEmpty()) {
            val text =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt()
                    textAlign = Paint.Align.CENTER
                    textSize = MONOGRAM_TEXT_PX
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
            val centerY = (ICON_PX / 2f) - ((text.descent() + text.ascent()) / 2f)
            canvas.drawText(letter, ICON_PX / 2f, centerY, text)
        }
        return bitmap
    }

    private companion object {
        /** 108dp at xxxhdpi, the recommended adaptive-icon canvas size. */
        const val ICON_PX = 324
        const val MONOGRAM_TEXT_PX = 140f
    }
}
