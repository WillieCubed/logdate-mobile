package app.logdate.client.sharing

import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/**
 * A no-op implementation of [SharingLauncher] for use in previews and non-sharing contexts.
 */
object NoOpSharingLauncher : SharingLauncher {
    override fun shareMemoryDay(
        date: LocalDate,
        summary: String,
    ) {}

    override fun shareJournalToInstagram(
        journalId: Uuid,
        theme: ShareTheme,
    ) {}

    override fun shareJournalLink(journalId: Uuid) {}

    override fun sharePhotoToInstagramFeed(photoId: String) {}

    override fun shareVideoToInstagramFeed(videoId: String) {}

    override fun getUriFromMedia(uid: String): Any = ""
}
