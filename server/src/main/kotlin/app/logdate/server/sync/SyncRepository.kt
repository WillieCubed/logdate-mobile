package app.logdate.server.sync

import app.logdate.shared.model.sync.DeviceId

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
    val createdAt: Long,
    val serverVersion: Long,
    val deviceId: DeviceId
)

data class ChangeSet<T, D>(
    val changes: List<T>,
    val deletions: List<D>,
    val lastTimestamp: Long
)

interface SyncRepository {
    fun status(): SyncStatus

    // Content
    fun upsertContent(record: ContentRecord): ContentRecord
    fun getContent(id: String): ContentRecord?
    fun deleteContent(id: String, deletedAt: Long)
    fun contentChanges(since: Long): ChangeSet<ContentRecord, ContentDeletionMarker>

    // Journals
    fun upsertJournal(record: JournalRecord): JournalRecord
    fun getJournal(id: String): JournalRecord?
    fun deleteJournal(id: String, deletedAt: Long)
    fun journalChanges(since: Long): ChangeSet<JournalRecord, JournalDeletionMarker>

    // Associations
    fun upsertAssociations(records: List<AssociationRecord>)
    fun deleteAssociations(keys: List<AssociationKey>, deletedAt: Long)
    fun associationChanges(since: Long): ChangeSet<AssociationRecord, AssociationDeletionMarker>

    // Media
    fun upsertMedia(record: MediaRecord): MediaRecord
    fun getMedia(mediaId: String): MediaRecord?
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
