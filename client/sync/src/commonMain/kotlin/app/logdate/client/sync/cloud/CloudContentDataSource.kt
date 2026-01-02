package app.logdate.client.sync.cloud

import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.sync.VersionConstraint
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Data source for syncing journal content (notes) with LogDate Cloud.
 * 
 * Handles uploading, downloading, updating, and deleting journal notes
 * across all types: Text, Image, Video, and Audio.
 */
interface CloudContentDataSource {
    /**
     * Uploads a new note to the cloud.
     */
    suspend fun uploadNote(accessToken: String, note: JournalNote): Result<SyncUploadResult>
    
    /**
     * Updates an existing note in the cloud.
     */
    suspend fun updateNote(accessToken: String, note: JournalNote): Result<SyncUploadResult>
    
    /**
     * Deletes a note from the cloud.
     */
    suspend fun deleteNote(accessToken: String, noteId: Uuid): Result<Unit>
    
    /**
     * Downloads all note changes since the specified timestamp.
     */
    suspend fun getContentChanges(accessToken: String, since: Instant): Result<ContentSyncResult>
}

/**
 * Result of a content sync operation containing changes and deletions.
 */
data class ContentSyncResult(
    val changes: List<JournalNote>,
    val deletions: List<Uuid>,
    val lastSyncTimestamp: Instant
)

/**
 * Default implementation of CloudContentDataSource using the CloudApiClient.
 */
class DefaultCloudContentDataSource(
    private val cloudApiClient: CloudApiClient
) : CloudContentDataSource {
    
    override suspend fun uploadNote(accessToken: String, note: JournalNote): Result<SyncUploadResult> {
        val request = note.toUploadRequest()
        return cloudApiClient.uploadContent(accessToken, request).map {
            SyncUploadResult(
                serverVersion = it.serverVersion,
                syncedAt = Instant.fromEpochMilliseconds(it.uploadedAt)
            )
        }
    }
    
    override suspend fun updateNote(accessToken: String, note: JournalNote): Result<SyncUploadResult> {
        val request = note.toUpdateRequest()
        return cloudApiClient.updateContent(accessToken, note.uid.toString(), request).map {
            SyncUploadResult(
                serverVersion = it.serverVersion,
                syncedAt = Instant.fromEpochMilliseconds(it.updatedAt)
            )
        }
    }
    
    override suspend fun deleteNote(accessToken: String, noteId: Uuid): Result<Unit> {
        return cloudApiClient.deleteContent(accessToken, noteId.toString())
    }
    
    override suspend fun getContentChanges(accessToken: String, since: Instant): Result<ContentSyncResult> {
        return cloudApiClient.getContentChanges(accessToken, since.toEpochMilliseconds()).map { response ->
            ContentSyncResult(
                changes = response.changes.map { it.toJournalNote() },
                deletions = response.deletions.map { Uuid.parse(it.id) },
                lastSyncTimestamp = Instant.fromEpochMilliseconds(response.lastTimestamp)
            )
        }
    }
    
    private fun JournalNote.toUploadRequest(): ContentUploadRequest {
        return ContentUploadRequest(
            id = uid.toString(),
            type = when (this) {
                is JournalNote.Text -> "TEXT"
                is JournalNote.Image -> "IMAGE" 
                is JournalNote.Video -> "VIDEO"
                is JournalNote.Audio -> "AUDIO"
            },
            content = when (this) {
                is JournalNote.Text -> content
                else -> null
            },
            mediaUri = when (this) {
                is JournalNote.Image -> mediaRef
                is JournalNote.Video -> mediaRef
                is JournalNote.Audio -> mediaRef
                else -> null
            },
            createdAt = creationTimestamp.toEpochMilliseconds(),
            lastUpdated = lastUpdated.toEpochMilliseconds(),
            syncVersion = syncVersion
        )
    }
    
    private fun JournalNote.toUpdateRequest(): ContentUpdateRequest {
        return ContentUpdateRequest(
            content = when (this) {
                is JournalNote.Text -> content
                else -> null
            },
            mediaUri = when (this) {
                is JournalNote.Image -> mediaRef
                is JournalNote.Video -> mediaRef
                is JournalNote.Audio -> mediaRef
                else -> null
            },
            lastUpdated = lastUpdated.toEpochMilliseconds(),
            syncVersion = syncVersion,
            versionConstraint = if (syncVersion > 0) {
                VersionConstraint.Known(syncVersion)
            } else {
                VersionConstraint.None
            }
        )
    }
    
    private fun ContentChange.toJournalNote(): JournalNote {
        val uid = Uuid.parse(id)
        val creationTimestamp = Instant.fromEpochMilliseconds(createdAt)
        val lastUpdated = Instant.fromEpochMilliseconds(lastUpdated)
        
        return when (type) {
            "TEXT" -> JournalNote.Text(
                uid = uid,
                creationTimestamp = creationTimestamp,
                lastUpdated = lastUpdated,
                content = content ?: "",
                syncVersion = serverVersion
            )
            "IMAGE" -> JournalNote.Image(
                uid = uid,
                creationTimestamp = creationTimestamp,
                lastUpdated = lastUpdated,
                mediaRef = mediaUri ?: "",
                syncVersion = serverVersion
            )
            "VIDEO" -> JournalNote.Video(
                uid = uid,
                creationTimestamp = creationTimestamp,
                lastUpdated = lastUpdated,
                mediaRef = mediaUri ?: "",
                syncVersion = serverVersion
            )
            "AUDIO" -> JournalNote.Audio(
                uid = uid,
                creationTimestamp = creationTimestamp,
                lastUpdated = lastUpdated,
                mediaRef = mediaUri ?: "",
                syncVersion = serverVersion
            )
            else -> throw IllegalArgumentException("Unknown content type: $type")
        }
    }
}
