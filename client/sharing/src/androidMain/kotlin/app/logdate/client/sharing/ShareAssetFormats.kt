package app.logdate.client.sharing

import android.graphics.Bitmap

/**
 * Canonical format constants for all assets generated and shared by the sharing pipeline.
 *
 * Every place that generates a share asset (file extension, compress format) and every place that
 * declares the MIME type of that asset to a receiving app must draw from these constants. Changing
 * the output format requires updating exactly one place, and the compiler enforces consistency.
 */
internal object ShareAssetFormats {
    /** File extension for generated share assets (background, sticker, QR code). */
    const val ASSET_FILE_EXT = "png"

    /** Compress format that matches [ASSET_FILE_EXT]. */
    val ASSET_COMPRESS_FORMAT: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG

    /** MIME type declared to receiving apps (e.g. Instagram) for generated share assets. */
    const val ASSET_MIME_TYPE = "image/png"

    /** Wildcard MIME type used when sharing arbitrary images to other apps. */
    const val IMAGE_ANY = "image/*"

    /** Wildcard MIME type used when sharing arbitrary videos to other apps. */
    const val VIDEO_ANY = "video/*"
}
