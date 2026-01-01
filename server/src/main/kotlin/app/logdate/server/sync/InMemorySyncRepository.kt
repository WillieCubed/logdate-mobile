package app.logdate.server.sync

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * In-memory implementation of SyncRepository with user isolation.
 * Non-persistent, intended for development and tests until DB-backed storage is wired.
 */
class InMemorySyncRepository : SyncRepository {

    // User-scoped storage: userId -> (entityId -> record)
    private val content = ConcurrentHashMap<UUID, ConcurrentHashMap<String, ContentRecord>>()
    private val contentDeletions = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()
    private val journals = ConcurrentHashMap<UUID, ConcurrentHashMap<String, JournalRecord>>()
    private val journalDeletions = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()
    private val associations = ConcurrentHashMap<UUID, ConcurrentHashMap<AssociationKey, AssociationRecord>>()
    private val associationDeletions = ConcurrentHashMap<UUID, ConcurrentHashMap<AssociationKey, Long>>()
    private val media = ConcurrentHashMap<UUID, ConcurrentHashMap<String, MediaRecord>>()
    private val lastTimestamp = AtomicLong(System.currentTimeMillis())

    private fun now(): Long {
        val ts = System.currentTimeMillis()
        lastTimestamp.set(ts)
        return ts
    }

    private fun nextVersion(): Long = lastTimestamp.incrementAndGet()

    private fun <K, V> ConcurrentHashMap<UUID, ConcurrentHashMap<K, V>>.forUser(userId: UUID): ConcurrentHashMap<K, V> =
        getOrPut(userId) { ConcurrentHashMap() }

    override fun status(userId: UUID): SyncStatus = SyncStatus(
        contentCount = content.forUser(userId).size,
        journalCount = journals.forUser(userId).size,
        associationCount = associations.forUser(userId).size,
        lastTimestamp = lastTimestamp.get()
    )

    // Content
    override fun upsertContent(userId: UUID, record: ContentRecord): ContentRecord {
        val versioned = record.copy(serverVersion = nextVersion(), lastUpdated = record.lastUpdated)
        content.forUser(userId)[record.id] = versioned
        return versioned.copy(serverVersion = versioned.serverVersion, lastUpdated = now())
    }

    override fun getContent(userId: UUID, id: String): ContentRecord? = content.forUser(userId)[id]

    override fun deleteContent(userId: UUID, id: String, deletedAt: Long) {
        content.forUser(userId).remove(id)
        contentDeletions.forUser(userId)[id] = deletedAt
        lastTimestamp.set(deletedAt)
    }

    override fun contentChanges(userId: UUID, since: Long): ChangeSet<ContentRecord, ContentDeletionMarker> {
        val changes = content.forUser(userId).values
            .filter { it.lastUpdated > since || it.serverVersion > since }
            .map { it.copy() }
        val deletions = contentDeletions.forUser(userId)
            .filterValues { it > since }
            .map { ContentDeletionMarker(it.key, it.value) }
        return ChangeSet(changes, deletions, lastTimestamp.get())
    }

    // Journals
    override fun upsertJournal(userId: UUID, record: JournalRecord): JournalRecord {
        val versioned = record.copy(serverVersion = nextVersion(), lastUpdated = record.lastUpdated)
        journals.forUser(userId)[record.id] = versioned
        return versioned.copy(serverVersion = versioned.serverVersion, lastUpdated = now())
    }

    override fun getJournal(userId: UUID, id: String): JournalRecord? = journals.forUser(userId)[id]

    override fun deleteJournal(userId: UUID, id: String, deletedAt: Long) {
        journals.forUser(userId).remove(id)
        journalDeletions.forUser(userId)[id] = deletedAt
        lastTimestamp.set(deletedAt)
    }

    override fun journalChanges(userId: UUID, since: Long): ChangeSet<JournalRecord, JournalDeletionMarker> {
        val changes = journals.forUser(userId).values
            .filter { it.lastUpdated > since || it.serverVersion > since }
            .map { it.copy() }
        val deletions = journalDeletions.forUser(userId)
            .filterValues { it > since }
            .map { JournalDeletionMarker(it.key, it.value) }
        return ChangeSet(changes, deletions, lastTimestamp.get())
    }

    // Associations
    override fun upsertAssociations(userId: UUID, records: List<AssociationRecord>) {
        records.forEach { association ->
            associations.forUser(userId)[AssociationKey(association.journalId, association.contentId)] =
                association.copy(serverVersion = nextVersion())
        }
    }

    override fun deleteAssociations(userId: UUID, keys: List<AssociationKey>, deletedAt: Long) {
        keys.forEach { key ->
            associations.forUser(userId).remove(key)
            associationDeletions.forUser(userId)[key] = deletedAt
        }
        lastTimestamp.set(deletedAt)
    }

    override fun associationChanges(userId: UUID, since: Long): ChangeSet<AssociationRecord, AssociationDeletionMarker> {
        val changes = associations.forUser(userId).values
            .filter { it.serverVersion > since || it.createdAt > since }
            .map { it.copy() }
        val deletions = associationDeletions.forUser(userId)
            .filterValues { it > since }
            .map { (key, deletedAt) -> AssociationDeletionMarker(key, deletedAt) }
        return ChangeSet(changes, deletions, lastTimestamp.get())
    }

    // Media
    override fun upsertMedia(userId: UUID, record: MediaRecord): MediaRecord {
        val versioned = record.copy(
            mediaId = if (record.mediaId.isBlank()) "media-${Random.nextLong().absoluteValue}" else record.mediaId,
            serverVersion = nextVersion()
        )
        media.forUser(userId)[versioned.mediaId] = versioned
        return versioned.copy(serverVersion = versioned.serverVersion, createdAt = now())
    }

    override fun getMedia(userId: UUID, mediaId: String): MediaRecord? = media.forUser(userId)[mediaId]
}
