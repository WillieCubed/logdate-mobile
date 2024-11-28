package app.logdate.client.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.JournalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch


/**
 * A utility that handles sharing content to external apps.
 */
class AndroidSharingLauncher(
    private val context: Context,
    private val mediaManager: MediaManager,
    private val journalRepository: JournalRepository,
    private val shareAssetGenerator: AndroidShareAssetGenerator,
    private val coroutineScope: CoroutineScope = GlobalScope, // TODO: Use app-bound scope
) : SharingLauncher {
    /**
     * Shares a journal to Instagram as a story.
     *
     * @param journalId The ID of the journal to share. If the journal does not exist, an exception
     * will be thrown.
     */
    override fun shareJournalToInstagram(journalId: String, theme: ShareTheme) {
        coroutineScope.launch {
            val journal = journalRepository.observeJournalById(journalId).firstOrNull()
                ?: throw IllegalArgumentException("Journal with ID $journalId does not exist")
            val cover = shareAssetGenerator.generateStickerLayer(journal, theme)
            val background = shareAssetGenerator.generateBackgroundLayer(journal, theme)
            context.grantUriPermission(
                INSTAGRAM_PACKAGE_NAME, cover.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            context.startActivity(createInstagramStoryIntent(cover.toUri(), background.toUri()))
        }
    }

    /**
     * Triggers the app to share a photo to Instagram.
     *
     * This method will trigger the Instagram app to open and share the video. The given video ID
     * must exist. If the image does not exist, an exception will be thrown.
     */
    override fun sharePhotoToInstagramFeed(photoId: String) {
        coroutineScope.launch {
            if (!mediaManager.exists(photoId)) {
                throw IllegalArgumentException("Photo with ID $photoId does not exist")
            }
            try {
                context.startActivity(createInstagramImageShareIntent(getUriFromMedia(photoId)))
            } catch (e: Exception) {
                throw IllegalArgumentException("Photo with ID $photoId does not exist")
            }
        }
    }

    /**
     * Triggers the app to share a video to Instagram.
     *
     * This method will trigger the Instagram app to open and share the video. The given video ID
     * must exist. If the video does not exist, an exception will be thrown.
     *
     */
    override fun shareVideoToInstagramFeed(videoId: String) {
        coroutineScope.launch {
            if (!mediaManager.exists(videoId)) {
                throw IllegalArgumentException("Video with ID $videoId does not exist")
            }
            try {
                context.startActivity(createInstagramVideoShareIntent(getUriFromMedia(videoId)))
            } catch (e: Exception) {
                throw IllegalArgumentException("Video with ID $videoId does not exist")
            }
        }
    }

    override fun getUriFromMedia(uid: String): Uri = context.filesDir.resolve("media/$uid").toUri()
}