package app.logdate.client.sharing

import android.content.Intent
import android.net.Uri

internal const val INSTAGRAM_PACKAGE_NAME = "com.instagram.android"
private const val INSTAGRAM_STORY_INTENT = "com.instagram.share.ADD_TO_STORY"

/**
 * Creates an intent to share a story to Instagram with a background image and sticker overlay.
 *
 * Per https://developers.facebook.com/docs/instagram-platform/sharing-to-stories, the
 * background asset is passed via [Intent.setDataAndType] and the sticker via an extra.
 *
 * @param backgroundAsset Uri to an image (JPG, PNG, min 720x1280, 9:16 or 9:18) or video
 * (H.264/H.265/WebM, up to 1080p, max 20s). Must be a content:// URI.
 * @param stickerAsset Uri to a sticker image (JPG, PNG, recommended 640x480). Must be a
 * content:// URI.
 */
internal fun createInstagramStoryIntent(
    backgroundAsset: Uri,
    stickerAsset: Uri,
) = Intent(INSTAGRAM_STORY_INTENT).apply {
    putExtra("source_application", BuildConfig.META_APP_ID)
    putExtra("interactive_asset_uri", stickerAsset)
    setDataAndType(backgroundAsset, ShareAssetFormats.ASSET_MIME_TYPE)
    setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/**
 * Creates an intent to share a sticker-only story to Instagram with a solid background color.
 *
 * This variant omits the background image. Instagram renders the background using the given
 * hex color values (solid if equal, gradient if different). Defaults to #222222 if omitted.
 *
 * @param stickerAsset Uri to a sticker PNG with transparency (recommended 640x480).
 * Must be a content:// URI.
 * @param topBackgroundColor Hex color string (e.g. "#A8D5BA") for the top of the background.
 * @param bottomBackgroundColor Hex color string for the bottom of the background.
 */
internal fun createInstagramStoryIntent(
    stickerAsset: Uri,
    topBackgroundColor: String,
    bottomBackgroundColor: String,
) = Intent(INSTAGRAM_STORY_INTENT).apply {
    putExtra("source_application", BuildConfig.META_APP_ID)
    putExtra("interactive_asset_uri", stickerAsset)
    putExtra("top_background_color", topBackgroundColor)
    putExtra("bottom_background_color", bottomBackgroundColor)
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
internal fun createInstagramImageShareIntent(imageUri: Uri) =
    Intent(Intent.ACTION_SEND).apply {
        type = ShareAssetFormats.IMAGE_ANY
        putExtra(Intent.EXTRA_STREAM, imageUri)
        setPackage(INSTAGRAM_PACKAGE_NAME)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
internal fun createInstagramVideoShareIntent(videoUri: Uri) =
    Intent(Intent.ACTION_SEND).apply {
        type = ShareAssetFormats.VIDEO_ANY
        putExtra(Intent.EXTRA_STREAM, videoUri)
        setPackage(INSTAGRAM_PACKAGE_NAME)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
