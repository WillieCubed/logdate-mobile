package app.logdate.client.sync.cloud

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Data source for syncing journal-content associations with LogDate Cloud.
 *
 * Handles uploading, downloading, and deleting associations between journals
 * and their content (notes). This enables content to be associated with
 * multiple journals.
 */
interface CloudAssociationDataSource {
    /**
     * Uploads new journal-content associations to the cloud.
     */
    suspend fun uploadAssociations(
        accessToken: String,
        associations: List<JournalContentAssociation>,
    ): Result<Instant>

    /**
     * Deletes specific journal-content associations from the cloud.
     */
    suspend fun deleteAssociations(
        accessToken: String,
        associations: List<JournalContentAssociation>,
    ): Result<Unit>

    /**
     * Downloads all association changes since the specified timestamp.
     */
    suspend fun getAssociationChanges(
        accessToken: String,
        since: Instant,
        limit: Int? = null,
    ): Result<AssociationSyncResult>
}

/**
 * Represents a journal-content association for sync operations.
 */
data class JournalContentAssociation(
    val journalId: Uuid,
    val contentId: Uuid,
    val createdAt: Instant = Clock.System.now(),
    val syncVersion: Long = 0,
)

/**
 * Result of an association sync operation containing changes and deletions.
 */
data class AssociationSyncResult(
    val additions: List<JournalContentAssociation>,
    val deletions: List<JournalContentAssociation>,
    val lastSyncTimestamp: Instant,
    val hasMore: Boolean = false,
    val failures: List<RemoteRecordFailure> = emptyList(),
)

/**
 * Default implementation of CloudAssociationDataSource using the CloudApiClient.
 */
class DefaultCloudAssociationDataSource(
    private val cloudApiClient: CloudApiClient,
) : CloudAssociationDataSource {
    override suspend fun uploadAssociations(
        accessToken: String,
        associations: List<JournalContentAssociation>,
    ): Result<Instant> {
        val request =
            AssociationUploadRequest(
                associations = associations.map { it.toAssociation() },
            )
        return cloudApiClient.uploadAssociations(accessToken, request).map {
            Instant.fromEpochMilliseconds(it.uploadedAt)
        }
    }

    override suspend fun deleteAssociations(
        accessToken: String,
        associations: List<JournalContentAssociation>,
    ): Result<Unit> {
        val request =
            AssociationDeleteRequest(
                associations =
                    associations.map {
                        AssociationDeleteItem(
                            journalId = it.journalId.toString(),
                            contentId = it.contentId.toString(),
                        )
                    },
            )
        return cloudApiClient.deleteAssociations(accessToken, request)
    }

    override suspend fun getAssociationChanges(
        accessToken: String,
        since: Instant,
        limit: Int?,
    ): Result<AssociationSyncResult> =
        cloudApiClient.getAssociationChanges(accessToken, since.toEpochMilliseconds(), limit).mapRecordPage { response ->
            val additions =
                response.changes.filterNot { it.isDeleted }.readEach(
                    idOf = { it.journalId + "::" + it.contentId },
                    versionOf = { it.serverVersion },
                ) { it.toAssociation() }
            val removed =
                response.deletions.readEach(
                    idOf = { it.journalId + "::" + it.contentId },
                    versionOf = { it.serverVersion },
                ) {
                    JournalContentAssociation(
                        journalId = Uuid.parse(it.journalId),
                        contentId = Uuid.parse(it.contentId),
                        createdAt = Instant.fromEpochMilliseconds(it.deletedAt),
                        syncVersion = it.serverVersion,
                    )
                }
            val tombstones =
                response.changes.filter { it.isDeleted }.readEach(
                    idOf = { it.journalId + "::" + it.contentId },
                    versionOf = { it.serverVersion },
                ) { it.toAssociation() }
            AssociationSyncResult(
                additions = additions.readable,
                deletions = removed.readable + tombstones.readable,
                lastSyncTimestamp = Instant.fromEpochMilliseconds(response.lastTimestamp),
                hasMore = response.hasMore,
                failures = additions.failures + removed.failures + tombstones.failures,
            )
        }

    private fun JournalContentAssociation.toAssociation(): Association =
        Association(
            journalId = journalId.toString(),
            contentId = contentId.toString(),
            createdAt = createdAt.toEpochMilliseconds(),
            syncVersion = syncVersion,
        )

    private fun AssociationChange.toAssociation(): JournalContentAssociation =
        JournalContentAssociation(
            journalId = Uuid.parse(journalId),
            contentId = Uuid.parse(contentId),
            createdAt = Instant.fromEpochMilliseconds(createdAt),
            syncVersion = serverVersion,
        )
}
