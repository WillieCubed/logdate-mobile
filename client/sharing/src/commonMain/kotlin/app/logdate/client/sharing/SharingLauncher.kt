package app.logdate.client.sharing

import kotlin.uuid.Uuid

interface SharingLauncher {
    /**
     * Shares a journal to Instagram as a story.
     *
     * @param journalId The ID of the journal to share. If the journal does not exist, an exception
     * will be thrown.
     * @param theme The theme to use for the shared content
     */
    fun shareJournalToInstagram(journalId: Uuid, theme: ShareTheme = ShareTheme.Light)
    
    /**
     * Shares a journal using the system share sheet.
     *
     * @param journalId The ID of the journal to share. If the journal does not exist, an exception
     * will be thrown.
     */
    fun shareJournalLink(journalId: Uuid)

    /**
     * Triggers the app to share a photo to Instagram.
     *
     * This method will trigger the Instagram app to open and share the photo. The given photo ID
     * must exist. If the image does not exist, an exception will be thrown.
     *
     * @param photoId The ID of the photo to share
     */
    fun sharePhotoToInstagramFeed(photoId: String)

    /**
     * Triggers the app to share a video to Instagram.
     *
     * This method will trigger the Instagram app to open and share the video. The given video ID
     * must exist. If the video does not exist, an exception will be thrown.
     *
     * @param videoId The ID of the video to share
     */
    fun shareVideoToInstagramFeed(videoId: String)
    
    /**
     * Gets a URI for a media file by its ID.
     *
     * @param uid The ID of the media file
     * @return A platform-specific URI representation
     */
    fun getUriFromMedia(uid: String): Any
}