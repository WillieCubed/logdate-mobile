package app.logdate.server.sync

import app.logdate.shared.model.sync.DeviceId
import java.util.UUID

/**
 * Transport-level storage records for sync. These are not core domain entities;
 * they carry sync-only metadata like serverVersion and deviceId.
 */
data class ContentRecord(
    val id: String,
    val type: String,
    val content: String?,
    val mediaUri: String?,
    val createdAt: Long,
    val lastUpdated: Long,
    val serverVersion: Long,
    val deviceId: DeviceId
)

data class JournalRecord(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val lastUpdated: Long,
    val serverVersion: Long,
    val deviceId: DeviceId
)

data class AssociationRecord(
    val journalId: String,
    val contentId: String,
    val createdAt: Long,
    val serverVersion: Long,
    val deviceId: DeviceId
)

data class MediaRecord(
    val mediaId: String,
    val contentId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val data: ByteArray,
    val storagePath: String? = null,
    val createdAt: Long,
    val serverVersion: Long,
    val deviceId: DeviceId
)

data class ChangeSet<T, D>(
    val changes: List<T>,
    val deletions: List<D>,
    val lastTimestamp: Long
)

/**
 * Repository interface for sync data operations.
 * All methods require a userId for multi-tenancy isolation.
 */
interface SyncRepository {
    fun status(userId: UUID): SyncStatus

    // Content
    fun upsertContent(userId: UUID, record: ContentRecord): ContentRecord
    fun getContent(userId: UUID, id: String): ContentRecord?
    fun deleteContent(userId: UUID, id: String, deletedAt: Long)
    fun contentChanges(userId: UUID, since: Long): ChangeSet<ContentRecord, ContentDeletionMarker>

    // Journals
    fun upsertJournal(userId: UUID, record: JournalRecord): JournalRecord
    fun getJournal(userId: UUID, id: String): JournalRecord?
    fun deleteJournal(userId: UUID, id: String, deletedAt: Long)
    fun journalChanges(userId: UUID, since: Long): ChangeSet<JournalRecord, JournalDeletionMarker>

    // Associations
    fun upsertAssociations(userId: UUID, records: List<AssociationRecord>)
    fun deleteAssociations(userId: UUID, keys: List<AssociationKey>, deletedAt: Long)
    fun associationChanges(userId: UUID, since: Long): ChangeSet<AssociationRecord, AssociationDeletionMarker>

    // Media
    fun upsertMedia(userId: UUID, record: MediaRecord): MediaRecord
    fun getMedia(userId: UUID, mediaId: String): MediaRecord?
}

data class SyncStatus(
    val contentCount: Int,
    val journalCount: Int,
    val associationCount: Int,
    val lastTimestamp: Long
)

data class ContentDeletionMarker(val id: String, val deletedAt: Long)
data class JournalDeletionMarker(val id: String, val deletedAt: Long)
data class AssociationKey(val journalId: String, val contentId: String)
data class AssociationDeletionMarker(val key: AssociationKey, val deletedAt: Long)
