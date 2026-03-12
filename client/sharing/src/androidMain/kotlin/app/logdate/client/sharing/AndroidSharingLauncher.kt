package app.logdate.client.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.JournalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/**
 * A utility that handles sharing content to external apps.
 */
class AndroidSharingLauncher(
    private val context: Context,
    private val mediaManager: MediaManager,
    private val journalRepository: JournalRepository,
    private val shareAssetGenerator: ShareAssetInterface,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : SharingLauncher {
    override fun shareMemoryDay(
        date: LocalDate,
        summary: String,
    ) {
        val shareText =
            if (summary.isNotBlank()) {
                "My memory from $date: $summary"
            } else {
                "Check out my memory from $date on LogDate!"
            }
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
        val chooserIntent =
            Intent.createChooser(shareIntent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(chooserIntent)
    }

    /**
     * Shares a journal to Instagram as a story.
     *
     * @param journalId The ID of the journal to share. If the journal does not exist, an exception
     * will be thrown.
     * @param theme The theme to use for the shared content
     */
    override fun shareJournalToInstagram(
        journalId: Uuid,
        theme: ShareTheme,
    ) {
        coroutineScope.launch {
            val journal =
                journalRepository.observeJournalById(journalId).firstOrNull()
                    ?: throw IllegalArgumentException("Journal with ID $journalId does not exist")
            val cover = shareAssetGenerator.generateStickerLayer(journal, theme)
            val background = shareAssetGenerator.generateBackgroundLayer(journal, theme)
            context.grantUriPermission(
                INSTAGRAM_PACKAGE_NAME,
                cover.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            context.startActivity(createInstagramStoryIntent(cover.toUri(), background.toUri()))
        }
    }

    /**
     * Shares a journal using the system share sheet.
     *
     * @param journalId The ID of the journal to share. If the journal does not exist, an exception
     * will be thrown.
     */
    override fun shareJournalLink(journalId: Uuid) {
        coroutineScope.launch {
            val journal =
                journalRepository.observeJournalById(journalId).firstOrNull()
                    ?: throw IllegalArgumentException("Journal with ID $journalId does not exist")

            // Use the shareJournalLink extension function from ShareSheet.kt
            context.shareJournalLink(journal)
        }
    }

    /**
     * Triggers the app to share a photo to Instagram.
     *
     * This method will trigger the Instagram app to open and share the photo. The given photo ID
     * must exist. If the image does not exist, an exception will be thrown.
     *
     * @param photoId The ID of the photo to share
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
     * @param videoId The ID of the video to share
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
