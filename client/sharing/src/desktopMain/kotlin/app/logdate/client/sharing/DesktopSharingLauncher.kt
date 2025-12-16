package app.logdate.client.sharing

import app.logdate.client.repository.journals.JournalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI
import kotlin.uuid.Uuid

/**
 * Desktop implementation of the SharingLauncher interface.
 * 
 * Provides sharing functionality for desktop platforms.
 *
 * @param journalRepository Repository for accessing journal data
 * @param coroutineScope Scope for launching coroutines
 */
class DesktopSharingLauncher(
    private val journalRepository: JournalRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : SharingLauncher {
    
    /**
     * Shares a journal to Instagram as a story.
     *
     * On desktop, this attempts to open the browser to Instagram, though
     * it may not support Instagram story sharing directly.
     *
     * @param journalId The ID of the journal to share.
     * @param theme The theme to use for the shared content
     */
    override fun shareJournalToInstagram(journalId: Uuid, theme: ShareTheme) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI("https://instagram.com"))
                }
            } catch (e: Exception) {
                // Handle the exception
            }
        }
    }
    
    /**
     * Shares a journal using the system share functionality.
     *
     * On desktop, this opens the default browser with the journal link.
     *
     * @param journalId The ID of the journal to share
     */
    override fun shareJournalLink(journalId: Uuid) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val journal = journalRepository.observeJournalById(journalId).firstOrNull()
                    ?: throw IllegalArgumentException("Journal with ID $journalId does not exist")
                
                val url = "https://logdate.app/j/${journal.id}"
                
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI(url))
                }
            } catch (e: Exception) {
                // Handle the exception
            }
        }
    }

    /**
     * Not implemented for desktop.
     * 
     * @throws UnsupportedOperationException This operation is not supported on desktop
     */
    override fun sharePhotoToInstagramFeed(photoId: String) {
        throw UnsupportedOperationException("Sharing photos to Instagram is not supported on desktop")
    }

    /**
     * Not implemented for desktop.
     * 
     * @throws UnsupportedOperationException This operation is not supported on desktop
     */
    override fun shareVideoToInstagramFeed(videoId: String) {
        throw UnsupportedOperationException("Sharing videos to Instagram is not supported on desktop")
    }

    /**
     * Gets a URI for a media file by its ID.
     *
     * @param uid The ID of the media file
     * @return A string path to the media file
     */
    override fun getUriFromMedia(uid: String): String {
        return "file:///media/$uid"
    }
}