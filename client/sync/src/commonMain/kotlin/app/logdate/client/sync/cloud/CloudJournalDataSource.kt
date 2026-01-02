package app.logdate.client.sync.cloud

import app.logdate.shared.model.Journal
import app.logdate.shared.model.sync.VersionConstraint
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Data source for syncing journal metadata with LogDate Cloud.
 * 
 * Handles uploading, downloading, updating, and deleting journal metadata.
 * Note that this only syncs journal metadata (title, description, timestamps)
 * and not the actual content, which is handled by CloudContentDataSource.
 */
interface CloudJournalDataSource {
    /**
     * Uploads new journal metadata to the cloud.
     */
    suspend fun uploadJournal(accessToken: String, journal: Journal): Result<SyncUploadResult>
    
    /**
     * Updates existing journal metadata in the cloud.
     */
    suspend fun updateJournal(accessToken: String, journal: Journal): Result<SyncUploadResult>
    
    /**
     * Deletes journal metadata from the cloud.
     */
    suspend fun deleteJournal(accessToken: String, journalId: Uuid): Result<Unit>
    
    /**
     * Downloads all journal metadata changes since the specified timestamp.
     */
    suspend fun getJournalChanges(accessToken: String, since: Instant): Result<JournalSyncResult>
}

/**
 * Result of a journal sync operation containing changes and deletions.
 */
data class JournalSyncResult(
    val changes: List<Journal>,
    val deletions: List<Uuid>,
    val lastSyncTimestamp: Instant
)

/**
 * Default implementation of CloudJournalDataSource using the CloudApiClient.
 */
class DefaultCloudJournalDataSource(
    private val cloudApiClient: CloudApiClient
) : CloudJournalDataSource {
    
    override suspend fun uploadJournal(accessToken: String, journal: Journal): Result<SyncUploadResult> {
        val request = journal.toUploadRequest()
        return cloudApiClient.uploadJournal(accessToken, request).map {
            SyncUploadResult(
                serverVersion = it.serverVersion,
                syncedAt = Instant.fromEpochMilliseconds(it.uploadedAt)
            )
        }
    }
    
    override suspend fun updateJournal(accessToken: String, journal: Journal): Result<SyncUploadResult> {
        val request = journal.toUpdateRequest()
        return cloudApiClient.updateJournal(accessToken, journal.id.toString(), request).map {
            SyncUploadResult(
                serverVersion = it.serverVersion,
                syncedAt = Instant.fromEpochMilliseconds(it.updatedAt)
            )
        }
    }
    
    override suspend fun deleteJournal(accessToken: String, journalId: Uuid): Result<Unit> {
        return cloudApiClient.deleteJournal(accessToken, journalId.toString())
    }
    
    override suspend fun getJournalChanges(accessToken: String, since: Instant): Result<JournalSyncResult> {
        return cloudApiClient.getJournalChanges(accessToken, since.toEpochMilliseconds()).map { response ->
            JournalSyncResult(
                changes = response.changes.map { it.toJournal() },
                deletions = response.deletions.map { Uuid.parse(it.id) },
                lastSyncTimestamp = Instant.fromEpochMilliseconds(response.lastTimestamp)
            )
        }
    }
    
    private fun Journal.toUploadRequest(): JournalUploadRequest {
        return JournalUploadRequest(
            id = id.toString(),
            title = title,
            description = description,
            createdAt = created.toEpochMilliseconds(),
            lastUpdated = lastUpdated.toEpochMilliseconds(),
            syncVersion = syncVersion
        )
    }
    
    private fun Journal.toUpdateRequest(): JournalUpdateRequest {
        return JournalUpdateRequest(
            title = title,
            description = description,
            lastUpdated = lastUpdated.toEpochMilliseconds(),
            syncVersion = syncVersion,
            versionConstraint = if (syncVersion > 0) {
                VersionConstraint.Known(syncVersion)
            } else {
                VersionConstraint.None
            }
        )
    }
    
    private fun JournalChange.toJournal(): Journal {
        return Journal(
            id = Uuid.parse(id),
            title = title,
            description = description,
            created = Instant.fromEpochMilliseconds(createdAt),
            lastUpdated = Instant.fromEpochMilliseconds(lastUpdated),
            syncVersion = serverVersion
        )
    }
}
