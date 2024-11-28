package app.logdate.client.repository.journals

import kotlinx.datetime.Instant

/**
 * A repository that can export its content to a file.
 */
interface ExportableJournalContentRepository {
    /**
     * Exports the content of this repository to a file.
     *
     * @param destination The URI to export the content to. If the URI is not accessible, an exception will
     * be thrown.
     * @param overwrite Whether to overwrite the file if it already exists. If this is false and the
     * file already exists, an exception will be thrown.
     */
    suspend fun exportContentToFile(
        destination: String,
        overwrite: Boolean = false,
        startTimestamp: Instant = Instant.DISTANT_PAST,
        endTimestamp: Instant = Instant.DISTANT_FUTURE,
    )
}