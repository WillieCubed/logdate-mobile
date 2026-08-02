package app.logdate.client.data.maintenance

import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.database.dao.maintenance.IntegrityDao
import app.logdate.client.database.dao.sync.SyncMetadataDao
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.shared.config.LogDateConfigRepository
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Audits and repairs local storage inconsistencies for sync-related data.
 */
class DataIntegrityService(
    private val integrityDao: IntegrityDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val journalContentDao: JournalContentDao,
    private val configRepository: LogDateConfigRepository,
    private val canonicalOwnerProvider: CanonicalOwnerProvider,
) {
    suspend fun audit(): IntegrityReport {
        val ownerId = canonicalOwnerProvider.getCanonicalOwnerId()
        val serverOrigin = currentOrigin()
        val orphanedJournalLinks = integrityDao.countOrphanedJournalLinks()
        val orphanedContentLinks = integrityDao.countOrphanedContentLinks()
        val pendingMissingJournals = integrityDao.countPendingMissingJournals(ownerId, serverOrigin)
        val pendingMissingNotes = integrityDao.countPendingMissingNotes(ownerId, serverOrigin)
        val associationAudit = auditPendingAssociations()

        return IntegrityReport(
            checkedAt = Clock.System.now(),
            orphanedJournalLinks = orphanedJournalLinks,
            orphanedContentLinks = orphanedContentLinks,
            pendingMissingJournals = pendingMissingJournals,
            pendingMissingNotes = pendingMissingNotes,
            pendingAssociationMissingLinks = associationAudit.missingLinks,
            pendingAssociationMalformed = associationAudit.malformed,
        )
    }

    suspend fun repair(): IntegrityRepairResult {
        val ownerId = canonicalOwnerProvider.getCanonicalOwnerId()
        val serverOrigin = currentOrigin()
        val orphanedJournalLinksRemoved = integrityDao.deleteOrphanedJournalLinks()
        val orphanedContentLinksRemoved = integrityDao.deleteOrphanedContentLinks()
        val pendingMissingJournalsRemoved = integrityDao.deletePendingMissingJournals(ownerId, serverOrigin)
        val pendingMissingNotesRemoved = integrityDao.deletePendingMissingNotes(ownerId, serverOrigin)
        val pendingAssociationsRemoved = repairPendingAssociations()

        return IntegrityRepairResult(
            repairedAt = Clock.System.now(),
            orphanedJournalLinksRemoved = orphanedJournalLinksRemoved,
            orphanedContentLinksRemoved = orphanedContentLinksRemoved,
            pendingMissingJournalsRemoved = pendingMissingJournalsRemoved,
            pendingMissingNotesRemoved = pendingMissingNotesRemoved,
            pendingAssociationsRemoved = pendingAssociationsRemoved,
        )
    }

    private suspend fun auditPendingAssociations(): PendingAssociationAudit {
        val pending =
            syncMetadataDao.getPendingByType(
                canonicalOwnerProvider.getCanonicalOwnerId(),
                currentOrigin(),
                EntityType.ASSOCIATION.name,
            )
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
        val ownerId = canonicalOwnerProvider.getCanonicalOwnerId()
        val pending = syncMetadataDao.getPendingByType(ownerId, currentOrigin(), EntityType.ASSOCIATION.name)
        var removed = 0

        for (entry in pending) {
            val key = AssociationPendingKey.fromPendingId(entry.entityId)
            if (key == null) {
                syncMetadataDao.deletePending(ownerId, currentOrigin(), EntityType.ASSOCIATION.name, entry.entityId)
                removed++
                continue
            }

            val exists = journalContentDao.isContentInJournal(key.journalId, key.contentId)
            if (!exists) {
                syncMetadataDao.deletePending(ownerId, currentOrigin(), EntityType.ASSOCIATION.name, entry.entityId)
                removed++
            }
        }

        return removed
    }

    private fun currentOrigin(): String = configRepository.getCurrentBackendUrl().trimEnd('/')
}

data class IntegrityReport(
    val checkedAt: Instant,
    val orphanedJournalLinks: Int,
    val orphanedContentLinks: Int,
    val pendingMissingJournals: Int,
    val pendingMissingNotes: Int,
    val pendingAssociationMissingLinks: Int,
    val pendingAssociationMalformed: Int,
)

data class IntegrityRepairResult(
    val repairedAt: Instant,
    val orphanedJournalLinksRemoved: Int,
    val orphanedContentLinksRemoved: Int,
    val pendingMissingJournalsRemoved: Int,
    val pendingMissingNotesRemoved: Int,
    val pendingAssociationsRemoved: Int,
)

private data class PendingAssociationAudit(
    val missingLinks: Int,
    val malformed: Int,
)
