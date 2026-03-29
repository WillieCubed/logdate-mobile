package app.logdate.feature.stickers.ui

/**
 * Extracts foreground subjects from photos for use as stickers.
 *
 * The output is a PNG byte array with transparency — the extracted subject
 * on a transparent background.
 */
interface StickerSubjectExtractor {
    /**
     * Extracts the foreground subject from the image at [imageUri].
     *
     * @param imageUri Content URI or file path of the source image.
     * @return The extracted subject as a PNG byte array with transparency,
     *         or null if extraction failed or no subject was found.
     */
    suspend fun extractSubject(imageUri: String): ByteArray?
}
