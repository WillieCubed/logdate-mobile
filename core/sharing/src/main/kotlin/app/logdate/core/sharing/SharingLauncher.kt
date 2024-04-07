package app.logdate.core.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import app.logdate.core.data.JournalRepository
import app.logdate.core.di.ApplicationScope
import app.logdate.core.media.MediaManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject


/**
 * A utility that handles sharing content to external apps.
 */
class SharingLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaManager: MediaManager,
    private val journalRepository: JournalRepository,
    private val shareAssetGenerator: ShareAssetGenerator,
    @ApplicationScope private val coroutineScope: CoroutineScope,
) {
    /**
     * Shares a journal to Instagram as a story.
     *
     * @param journalId The ID of the journal to share. If the journal does not exist, an exception
     * will be thrown.
     */
    fun shareJournalToInstagram(journalId: String, theme: ShareTheme = ShareTheme.Light) {
        coroutineScope.launch {
            val journal = journalRepository.observeJournalById(journalId).firstOrNull()
                ?: throw IllegalArgumentException("Journal with ID $journalId does not exist")
            val cover = shareAssetGenerator.generateStickerLayer(journal, theme)
            val background = shareAssetGenerator.generateBackgroundLayer(journal, theme)
            context.grantUriPermission(
                INSTAGRAM_PACKAGE_NAME, cover, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            context.startActivity(createInstagramStoryIntent(cover, background))
        }
    }

    /**
     * Triggers the app to share a photo to Instagram.
     *
     * This method will trigger the Instagram app to open and share the video. The given video ID
     * must exist. If the image does not exist, an exception will be thrown.
     */
    fun sharePhotoToInstagramFeed(photoId: String) {
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
    fun shareVideoToInstagramFeed(videoId: String) {
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

    private fun getUriFromMedia(uid: String): Uri = context.filesDir.resolve("media/$uid").toUri()
}


