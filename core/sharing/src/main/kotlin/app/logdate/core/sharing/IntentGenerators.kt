package app.logdate.core.sharing

import android.content.Intent
import android.net.Uri

internal const val INSTAGRAM_PACKAGE_NAME = "com.instagram.android"
private const val INSTAGRAM_STORY_INTENT = "com.instagram.share.ADD_TO_STORY"

/**
 * Creates an intent to share a story to Instagram.
 *
 * The given image must be a local file URI and must be stored in a location that Instagram
 * can access.
 *
 * @param backgroundAsset Uri to an image asset (JPG, PNG) or video asset (H.264, H.265, WebM).
 * Minimum dimensions 720x1280. Recommended image ratios 9:16 or 9:18. Videos can be 1080p and up to
 * 20 seconds in duration.
 * @param stickerAsset Uri to an image asset (JPG, PNG). Recommended dimensions: 640x480.
 */
internal fun createInstagramStoryIntent(
    backgroundAsset: Uri,
    stickerAsset: Uri,
) = Intent(INSTAGRAM_STORY_INTENT).apply {
    val sourceApplication: String = BuildConfig.META_APP_ID
    putExtra("source_application", sourceApplication)
    putExtra("interactive_asset_uri", stickerAsset)
    setDataAndType(backgroundAsset, "image/jpeg")
    setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/**
 * Creates an intent to share an image to Instagram.
 *
 * The given image must be a local file URI and must be stored in a location that Instagram
 * can access. The video must be in one of the following formats: JPEG, GIF, or PNG
 *
 * @param imageUri The URI of the video to share.
 */
internal fun createInstagramImageShareIntent(imageUri: Uri) = Intent(Intent.ACTION_SEND).apply {
    type = "image/*"
    putExtra(Intent.EXTRA_STREAM, imageUri)
    setPackage(INSTAGRAM_PACKAGE_NAME)
}

/**
 * Creates an intent to share a video to Instagram.
 *
 * The given video must be a local file URI and must be stored in a location that Instagram
 * can access. The video must be in one of the following formats: MKV, MP4. Additionally, the video
 * must meet the following requirements:
 * - Minimum duration: 3 seconds
 * - Maximum duration: 10 minutes
 * - Minimum dimension: 640x640 pixels
 *
 * @param videoUri The URI of the video to share.
 */
internal fun createInstagramVideoShareIntent(videoUri: Uri) = Intent(Intent.ACTION_SEND).apply {
    type = "video/*"
    putExtra(Intent.EXTRA_STREAM, videoUri)
    setPackage(INSTAGRAM_PACKAGE_NAME)
}