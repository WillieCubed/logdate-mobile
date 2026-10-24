package app.logdate.client.media

import app.logdate.client.media.storage.AndroidMediaPublicationTickets

/** Bridges staged imports to the durable ticket ledger used by the Android collector. */
class AndroidManagedMediaPublicationCustody(
    private val tickets: AndroidMediaPublicationTickets,
) : ManagedMediaPublicationCustody {
    override suspend fun reserve(
        destination: String,
        stagedMedia: StagedMediaFile,
    ): String =
        tickets
            .reserve(destination, stagedMedia.sourceFilePath)
            .id

    override suspend fun publish(
        ticketId: String,
        reference: String,
    ) {
        tickets.publish(ticketId, reference)
    }

    override suspend fun acknowledge(
        ticketId: String,
        reference: String,
    ) {
        tickets.acknowledge(ticketId, reference)
    }
}
