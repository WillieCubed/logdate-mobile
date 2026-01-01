package app.logdate.server.sync

import app.logdate.shared.model.sync.DeviceId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * In-memory implementation of SyncRepository.
 * Non-persistent, intended for development and tests until DB-backed storage is wired.
 */
class InMemorySyncRepository : SyncRepository {

    private val content = ConcurrentHashMap<String, ContentRecord>()
    private val contentDeletions = ConcurrentHashMap<String, Long>()
    private val journals = ConcurrentHashMap<String, JournalRecord>()
    private val journalDeletions = ConcurrentHashMap<String, Long>()
    private val associations = ConcurrentHashMap<AssociationKey, AssociationRecord>()
    private val associationDeletions = ConcurrentHashMap<AssociationKey, Long>()
    private val media = ConcurrentHashMap<String, MediaRecord>()
    private val lastTimestamp = AtomicLong(System.currentTimeMillis())

    private fun now(): Long {
        val ts = System.currentTimeMillis()
        lastTimestamp.set(ts)
        return ts
    }

    private fun nextVersion(): Long = lastTimestamp.incrementAndGet()

    override fun status(): SyncStatus = SyncStatus(
        contentCount = content.size,
        journalCount = journals.size,
        associationCount = associations.size,
        lastTimestamp = lastTimestamp.get()
    )

    // Content
    override fun upsertContent(record: ContentRecord): ContentRecord {
        val versioned = record.copy(serverVersion = nextVersion(), lastUpdated = record.lastUpdated)
        content[record.id] = versioned
        return versioned.copy(serverVersion = versioned.serverVersion, lastUpdated = now())
    }

    override fun getContent(id: String): ContentRecord? = content[id]

    override fun deleteContent(id: String, deletedAt: Long) {
        content.remove(id)
        contentDeletions[id] = deletedAt
        lastTimestamp.set(deletedAt)
    }

    override fun contentChanges(since: Long): ChangeSet<ContentRecord, ContentDeletionMarker> {
        val changes = content.values
            .filter { it.lastUpdated > since || it.serverVersion > since }
            .map { it.copy() }
        val deletions = contentDeletions
            .filterValues { it > since }
            .map { ContentDeletionMarker(it.key, it.value) }
        return ChangeSet(changes, deletions, lastTimestamp.get())
    }

    // Journals
    override fun upsertJournal(record: JournalRecord): JournalRecord {
        val versioned = record.copy(serverVersion = nextVersion(), lastUpdated = record.lastUpdated)
        journals[record.id] = versioned
        return versioned.copy(serverVersion = versioned.serverVersion, lastUpdated = now())
    }

    override fun getJournal(id: String): JournalRecord? = journals[id]

    override fun deleteJournal(id: String, deletedAt: Long) {
        journals.remove(id)
        journalDeletions[id] = deletedAt
        lastTimestamp.set(deletedAt)
    }

    override fun journalChanges(since: Long): ChangeSet<JournalRecord, JournalDeletionMarker> {
        val changes = journals.values
            .filter { it.lastUpdated > since || it.serverVersion > since }
            .map { it.copy() }
        val deletions = journalDeletions
            .filterValues { it > since }
            .map { JournalDeletionMarker(it.key, it.value) }
        return ChangeSet(changes, deletions, lastTimestamp.get())
    }

    // Associations
    override fun upsertAssociations(records: List<AssociationRecord>) {
        records.forEach { association ->
            associations[AssociationKey(association.journalId, association.contentId)] =
                association.copy(serverVersion = nextVersion())
        }
    }

    override fun deleteAssociations(keys: List<AssociationKey>, deletedAt: Long) {
        keys.forEach { key ->
            associations.remove(key)
            associationDeletions[key] = deletedAt
        }
        lastTimestamp.set(deletedAt)
    }

    override fun associationChanges(since: Long): ChangeSet<AssociationRecord, AssociationDeletionMarker> {
        val changes = associations.values
            .filter { it.serverVersion > since || it.createdAt > since }
            .map { it.copy() }
        val deletions = associationDeletions
            .filterValues { it > since }
            .map { (key, deletedAt) -> AssociationDeletionMarker(key, deletedAt) }
        return ChangeSet(changes, deletions, lastTimestamp.get())
    }

    // Media
    override fun upsertMedia(record: MediaRecord): MediaRecord {
        val versioned = record.copy(
            mediaId = if (record.mediaId.isBlank()) "media-${Random.nextLong().absoluteValue}" else record.mediaId,
            serverVersion = nextVersion()
        )
        media[versioned.mediaId] = versioned
        return versioned.copy(serverVersion = versioned.serverVersion, createdAt = now())
    }

    override fun getMedia(mediaId: String): MediaRecord? = media[mediaId]
}
