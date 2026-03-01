package app.logdate.client.data.maintenance

import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.database.dao.maintenance.IntegrityDao
import app.logdate.client.database.dao.sync.SyncMetadataDao
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Audits and repairs local storage inconsistencies for sync-related data.
 */
class DataIntegrityService(
    private val integrityDao: IntegrityDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val journalContentDao: JournalContentDao
) {
    suspend fun audit(): IntegrityReport {
        val orphanedJournalLinks = integrityDao.countOrphanedJournalLinks()
        val orphanedContentLinks = integrityDao.countOrphanedContentLinks()
        val pendingMissingJournals = integrityDao.countPendingMissingJournals()
        val pendingMissingNotes = integrityDao.countPendingMissingNotes()
        val associationAudit = auditPendingAssociations()

        return IntegrityReport(
            checkedAt = Clock.System.now(),
            orphanedJournalLinks = orphanedJournalLinks,
            orphanedContentLinks = orphanedContentLinks,
            pendingMissingJournals = pendingMissingJournals,
            pendingMissingNotes = pendingMissingNotes,
            pendingAssociationMissingLinks = associationAudit.missingLinks,
            pendingAssociationMalformed = associationAudit.malformed
        )
    }

    suspend fun repair(): IntegrityRepairResult {
        val orphanedJournalLinksRemoved = integrityDao.deleteOrphanedJournalLinks()
        val orphanedContentLinksRemoved = integrityDao.deleteOrphanedContentLinks()
        val pendingMissingJournalsRemoved = integrityDao.deletePendingMissingJournals()
        val pendingMissingNotesRemoved = integrityDao.deletePendingMissingNotes()
        val pendingAssociationsRemoved = repairPendingAssociations()

        return IntegrityRepairResult(
            repairedAt = Clock.System.now(),
            orphanedJournalLinksRemoved = orphanedJournalLinksRemoved,
            orphanedContentLinksRemoved = orphanedContentLinksRemoved,
            pendingMissingJournalsRemoved = pendingMissingJournalsRemoved,
            pendingMissingNotesRemoved = pendingMissingNotesRemoved,
            pendingAssociationsRemoved = pendingAssociationsRemoved
        )
    }

    private suspend fun auditPendingAssociations(): PendingAssociationAudit {
        val pending = syncMetadataDao.getPendingByType(EntityType.ASSOCIATION.name)
        var missingLinks = 0
        var malformed = 0

        for (entry in pending) {
            val key = AssociationPendingKey.fromPendingId(entry.entityId)
            if (key == null) {
                malformed++
                continue
            }
            val exists = journalContentDao.isContentInJournal(key.journalId, key.contentId)
            if (!exists) {
                missingLinks++
            }
        }

        return PendingAssociationAudit(missingLinks, malformed)
    }

    private suspend fun repairPendingAssociations(): Int {
        val pending = syncMetadataDao.getPendingByType(EntityType.ASSOCIATION.name)
        var removed = 0

        for (entry in pending) {
            val key = AssociationPendingKey.fromPendingId(entry.entityId)
            if (key == null) {
                syncMetadataDao.deletePending(EntityType.ASSOCIATION.name, entry.entityId)
                removed++
                continue
            }

            val exists = journalContentDao.isContentInJournal(key.journalId, key.contentId)
            if (!exists) {
                syncMetadataDao.deletePending(EntityType.ASSOCIATION.name, entry.entityId)
                removed++
            }
        }

        return removed
    }
}

data class IntegrityReport(
    val checkedAt: Instant,
    val orphanedJournalLinks: Int,
    val orphanedContentLinks: Int,
    val pendingMissingJournals: Int,
    val pendingMissingNotes: Int,
    val pendingAssociationMissingLinks: Int,
    val pendingAssociationMalformed: Int
)

data class IntegrityRepairResult(
    val repairedAt: Instant,
    val orphanedJournalLinksRemoved: Int,
    val orphanedContentLinksRemoved: Int,
    val pendingMissingJournalsRemoved: Int,
    val pendingMissingNotesRemoved: Int,
    val pendingAssociationsRemoved: Int
)

private data class PendingAssociationAudit(
    val missingLinks: Int,
    val malformed: Int
)
