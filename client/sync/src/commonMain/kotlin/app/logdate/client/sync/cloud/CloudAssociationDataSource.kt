package app.logdate.client.sync.cloud

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
    suspend fun uploadAssociations(accessToken: String, associations: List<JournalContentAssociation>): Result<Instant>
    
    /**
     * Deletes specific journal-content associations from the cloud.
     */
    suspend fun deleteAssociations(accessToken: String, associations: List<JournalContentAssociation>): Result<Unit>
    
    /**
     * Downloads all association changes since the specified timestamp.
     */
    suspend fun getAssociationChanges(accessToken: String, since: Instant): Result<AssociationSyncResult>
}

/**
 * Represents a journal-content association for sync operations.
 */
data class JournalContentAssociation(
    val journalId: Uuid,
    val contentId: Uuid,
    val createdAt: Instant = Clock.System.now(),
    val syncVersion: Long = 0
)

/**
 * Result of an association sync operation containing changes and deletions.
 */
data class AssociationSyncResult(
    val additions: List<JournalContentAssociation>,
    val deletions: List<JournalContentAssociation>,
    val lastSyncTimestamp: Instant
)

/**
 * Default implementation of CloudAssociationDataSource using the CloudApiClient.
 */
class DefaultCloudAssociationDataSource(
    private val cloudApiClient: CloudApiClient
) : CloudAssociationDataSource {
    
    override suspend fun uploadAssociations(
        accessToken: String, 
        associations: List<JournalContentAssociation>
    ): Result<Instant> {
        val request = AssociationUploadRequest(
            associations = associations.map { it.toAssociation() }
        )
        return cloudApiClient.uploadAssociations(accessToken, request).map {
            Instant.fromEpochMilliseconds(it.uploadedAt)
        }
    }
    
    override suspend fun deleteAssociations(
        accessToken: String, 
        associations: List<JournalContentAssociation>
    ): Result<Unit> {
        val request = AssociationDeleteRequest(
            associations = associations.map { 
                AssociationDeleteItem(
                    journalId = it.journalId.toString(), 
                    contentId = it.contentId.toString()
                )
            }
        )
        return cloudApiClient.deleteAssociations(accessToken, request)
    }
    
    override suspend fun getAssociationChanges(accessToken: String, since: Instant): Result<AssociationSyncResult> {
        return cloudApiClient.getAssociationChanges(accessToken, since.toEpochMilliseconds()).map { response ->
            AssociationSyncResult(
                additions = response.changes.filter { !it.isDeleted }.map { it.toAssociation() },
                deletions = response.deletions.map { 
                    JournalContentAssociation(
                        journalId = Uuid.parse(it.journalId),
                        contentId = Uuid.parse(it.contentId),
                        createdAt = Instant.fromEpochMilliseconds(it.deletedAt)
                    )
                },
                lastSyncTimestamp = Instant.fromEpochMilliseconds(response.lastTimestamp)
            )
        }
    }
    
    private fun JournalContentAssociation.toAssociation(): Association {
        return Association(
            journalId = journalId.toString(),
            contentId = contentId.toString(),
            createdAt = createdAt.toEpochMilliseconds(),
            syncVersion = syncVersion
        )
    }
    
    private fun AssociationChange.toAssociation(): JournalContentAssociation {
        return JournalContentAssociation(
            journalId = Uuid.parse(journalId),
            contentId = Uuid.parse(contentId),
            createdAt = Instant.fromEpochMilliseconds(createdAt),
            syncVersion = serverVersion
        )
    }
}
