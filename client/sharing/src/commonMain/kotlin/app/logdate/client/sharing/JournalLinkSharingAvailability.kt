package app.logdate.client.sharing

import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerProtocolFeature

/**
 * Whether a journal can be shared as a link that someone else can open.
 *
 * A journal link only reaches another person when the connected server hosts shared journals.
 * Until then, link and QR code sharing must not be offered.
 */
fun interface JournalLinkSharingAvailability {
    fun isAvailable(): Boolean
}

/**
 * Reads link-sharing support from the connected server's advertised protocol features.
 */
class ServerJournalLinkSharingAvailability(
    private val configRepository: LogDateConfigRepository,
) : JournalLinkSharingAvailability {
    override fun isAvailable(): Boolean =
        configRepository
            .getCurrentServerDescriptor()
            ?.hasProtocolFeature(ServerProtocolFeature.JOURNAL_SHARE_LINKS_V1) == true
}
