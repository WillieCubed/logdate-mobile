package app.logdate.feature.rewind.ui.detail

/** Pixel geometry for the complete media object, including its metadata and caption. */
internal data class RewindMediaLayout(
    val width: Float,
    val height: Float,
    val headerHeight: Float,
    val mediaHeight: Float,
    val captionHeight: Float,
)

/** Fit the complete object as in Apple; keep mobile captions readable when height is scarce. */
internal fun resolveRewindMediaLayout(
    availableWidth: Float,
    availableHeight: Float,
    preferredMediaAspectRatio: Float,
    headerHeight: Float,
    minimumReadableWidth: Float,
    hasCaption: Boolean,
    measureCaption: (Float) -> Float,
): RewindMediaLayout {
    val heightLimit = availableHeight.coerceAtLeast(1f)
    val header = headerHeight.coerceIn(0f, heightLimit)
    val aspect = preferredMediaAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1.5f
    val minimumWidth = minOf(availableWidth.coerceAtLeast(1f), minimumReadableWidth.coerceAtLeast(1f))
    var width = availableWidth.coerceAtLeast(1f)
    var caption = 0f
    var media = 0f
    repeat(200) {
        caption = if (hasCaption) minOf(heightLimit * 0.32f, heightLimit - header, maxOf(width * 0.12f, measureCaption(width))) else 0f
        media = width / aspect
        if (header + media + caption <= heightLimit || width <= minimumWidth) {
            media = minOf(media, (heightLimit - header - caption).coerceAtLeast(0f))
            return RewindMediaLayout(width, header + media + caption, header, media, caption)
        }
        width = (width * 0.96f).coerceAtLeast(minimumWidth)
    }
    return RewindMediaLayout(width, heightLimit, header, media.coerceAtMost((heightLimit - header - caption).coerceAtLeast(0f)), caption)
}
