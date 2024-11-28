package app.logdate.client.sharing

interface SharingLauncher {
    /**
     * Shares a journal to Instagram as a story.
     *
     * @param journalId The ID of the journal to share. If the journal does not exist, an exception
     * will be thrown.
     */
    fun shareJournalToInstagram(journalId: String, theme: ShareTheme = ShareTheme.Light)

    /**
     * Triggers the app to share a photo to Instagram.
     *
     * This method will trigger the Instagram app to open and share the video. The given video ID
     * must exist. If the image does not exist, an exception will be thrown.
     */
    fun sharePhotoToInstagramFeed(photoId: String)

    /**
     * Triggers the app to share a video to Instagram.
     *
     * This method will trigger the Instagram app to open and share the video. The given video ID
     * must exist. If the video does not exist, an exception will be thrown.
     *
     */
    fun shareVideoToInstagramFeed(videoId: String)
    fun getUriFromMedia(uid: String): Any
}