package app.logdate.client.media.audio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.io.ByteArrayOutputStream

/**
 * Generates small artwork bitmaps for audio playback notifications.
 *
 * Each artwork is a 256x256 PNG with a vertical gradient derived from
 * the daylight palette colors, giving the notification a visual identity
 * tied to when the audio was recorded.
 */
class AudioNotificationArtworkGenerator {
    private val cache = mutableMapOf<Long, ByteArray>()

    /**
     * Generates artwork bytes for a notification, using palette colors.
     *
     * Results are cached by background color to avoid regenerating for the same palette.
     *
     * @param immersiveBackground Bottom/overall background color (packed ARGB).
     * @param gradientStart Top gradient color (packed ARGB).
     * @param gradientEnd Bottom gradient color (packed ARGB).
     * @return PNG-encoded byte array suitable for [androidx.media3.common.MediaMetadata.artworkData].
     */
    fun generate(
        immersiveBackground: Long,
        gradientStart: Long,
        gradientEnd: Long,
    ): ByteArray {
        val cacheKey = immersiveBackground xor (gradientStart shl 16) xor (gradientEnd shl 32)
        cache[cacheKey]?.let { return it }

        val size = ARTWORK_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fill background
        val bgPaint = Paint()
        bgPaint.color = immersiveBackground.toInt()
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Draw vertical gradient stripe in center third
        val stripeLeft = size / 4f
        val stripeRight = size * 3f / 4f
        val gradientPaint = Paint()
        gradientPaint.shader =
            LinearGradient(
                0f,
                0f,
                0f,
                size.toFloat(),
                gradientStart.toInt(),
                gradientEnd.toInt(),
                Shader.TileMode.CLAMP,
            )
        gradientPaint.alpha = 180
        canvas.drawRect(stripeLeft, 0f, stripeRight, size.toFloat(), gradientPaint)

        // Encode to PNG bytes
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        bitmap.recycle()

        val bytes = stream.toByteArray()
        cache[cacheKey] = bytes
        return bytes
    }

    companion object {
        private const val ARTWORK_SIZE = 256
    }
}
