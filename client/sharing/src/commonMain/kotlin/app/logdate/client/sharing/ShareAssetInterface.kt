package app.logdate.client.sharing

import app.logdate.shared.model.Journal

interface ShareAssetInterface {
    /**
     * Creates and returns a background layer for the given [journal].
     *
     * This layer is used to overlay the journal cover on top of a background image. It is stored
     * in the cache directory and can be shared with other apps.
     *
     * @return The URI of the generated background layer image.
     */
    suspend fun generateBackgroundLayer(
        journal: Journal,
        shareTheme: ShareTheme,
    ): String

    /**
     * Creates and returns a sticker layer for the given [journal].
     *
     * This layer is used to overlay the journal cover on top of a background image. It is stored
     * in the cache directory and can be shared with other apps. Assume that this sticker layer
     * may have a transparent background.
     *
     * @return The URI of the generated sticker layer image.
     */
    suspend fun generateStickerLayer(journal: Journal): String

    /**
     * Creates and returns a QR code image for the given [journal].
     *
     * The QR code points to the journal's public web URL and is stored in the cache directory so
     * it can be shared with other apps.
     *
     * @return The URI of the generated QR code image.
     */
    suspend fun generateJournalQrCode(journal: Journal): String
}
