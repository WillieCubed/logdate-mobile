package app.logdate.client.sync.cloud

import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.shared.model.Journal
import app.logdate.shared.model.sync.VersionConstraint
import kotlin.time.Instant
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
    suspend fun uploadJournal(
        accessToken: String,
        journal: Journal,
    ): Result<SyncUploadResult>

    /**
     * Updates existing journal metadata in the cloud.
     */
    suspend fun updateJournal(
        accessToken: String,
        journal: Journal,
    ): Result<SyncUploadResult>

    /**
     * Deletes journal metadata from the cloud.
     */
    suspend fun deleteJournal(
        accessToken: String,
        journalId: Uuid,
    ): Result<Unit>

    /**
     * Downloads all journal metadata changes since the specified timestamp.
     */
    suspend fun getJournalChanges(
        accessToken: String,
        since: Instant,
        limit: Int? = null,
    ): Result<JournalSyncResult>
}

/**
 * Result of a journal sync operation containing changes and deletions.
 */
data class JournalSyncResult(
    val changes: List<Journal>,
    val deletions: List<Uuid>,
    val lastSyncTimestamp: Instant,
    val hasMore: Boolean = false,
)

/**
 * Default implementation of CloudJournalDataSource using the CloudApiClient.
 */
class DefaultCloudJournalDataSource(
    private val cloudApiClient: CloudApiClient,
    private val syncPayloadCipher: SyncPayloadCipher? = null,
) : CloudJournalDataSource {
    override suspend fun uploadJournal(
        accessToken: String,
        journal: Journal,
    ): Result<SyncUploadResult> {
        val request =
            try {
                journal.toUploadRequest()
            } catch (error: Exception) {
                return Result.failure(error)
            }
        return cloudApiClient.uploadJournal(accessToken, request).map {
            SyncUploadResult(
                serverVersion = it.serverVersion,
                syncedAt = Instant.fromEpochMilliseconds(it.uploadedAt),
            )
        }
    }

    override suspend fun updateJournal(
        accessToken: String,
        journal: Journal,
    ): Result<SyncUploadResult> {
        val request =
            try {
                journal.toUpdateRequest()
            } catch (error: Exception) {
                return Result.failure(error)
            }
        return cloudApiClient.updateJournal(accessToken, journal.id.toString(), request).map {
            SyncUploadResult(
                serverVersion = it.serverVersion,
                syncedAt = Instant.fromEpochMilliseconds(it.updatedAt),
            )
        }
    }

    override suspend fun deleteJournal(
        accessToken: String,
        journalId: Uuid,
    ): Result<Unit> = cloudApiClient.deleteJournal(accessToken, journalId.toString())

    override suspend fun getJournalChanges(
        accessToken: String,
        since: Instant,
        limit: Int?,
    ): Result<JournalSyncResult> =
        cloudApiClient.getJournalChanges(accessToken, since.toEpochMilliseconds(), limit).mapCatching { response ->
            response.toJournalSyncResult()
        }

    private suspend fun Journal.toUploadRequest(): JournalUploadRequest =
        JournalUploadRequest(
            id = id.toString(),
            title = encryptJournalField(id, "title", title),
            description = encryptJournalField(id, "description", description),
            createdAt = created.toEpochMilliseconds(),
            lastUpdated = lastUpdated.toEpochMilliseconds(),
            syncVersion = syncVersion,
        )

    private suspend fun Journal.toUpdateRequest(): JournalUpdateRequest =
        JournalUpdateRequest(
            title = encryptJournalField(id, "title", title),
            description = encryptJournalField(id, "description", description),
            lastUpdated = lastUpdated.toEpochMilliseconds(),
            syncVersion = syncVersion,
            versionConstraint =
                if (syncVersion > 0) {
                    VersionConstraint.Known(syncVersion)
                } else {
                    VersionConstraint.None
                },
        )

    private suspend fun JournalChangesResponse.toJournalSyncResult(): JournalSyncResult =
        JournalSyncResult(
            changes = changes.map { it.toJournal() },
            deletions = deletions.map { Uuid.parse(it.id) },
            lastSyncTimestamp = Instant.fromEpochMilliseconds(lastTimestamp),
            hasMore = hasMore,
        )

    private suspend fun JournalChange.toJournal(): Journal =
        Journal(
            id = Uuid.parse(id),
            title = decryptJournalField(Uuid.parse(id), "title", title),
            description = decryptJournalField(Uuid.parse(id), "description", description),
            created = Instant.fromEpochMilliseconds(createdAt),
            lastUpdated = Instant.fromEpochMilliseconds(lastUpdated),
            syncVersion = serverVersion,
        )

    private suspend fun encryptJournalField(
        journalId: Uuid,
        fieldName: String,
        value: String,
    ): String = syncPayloadCipher?.encryptString(journalFieldId(journalId, fieldName), value) ?: value

    private suspend fun decryptJournalField(
        journalId: Uuid,
        fieldName: String,
        value: String,
    ): String = syncPayloadCipher?.decryptString(journalFieldId(journalId, fieldName), value) ?: value

    private fun journalFieldId(
        journalId: Uuid,
        fieldName: String,
    ): String = "sync:journal:$journalId:$fieldName"
}
