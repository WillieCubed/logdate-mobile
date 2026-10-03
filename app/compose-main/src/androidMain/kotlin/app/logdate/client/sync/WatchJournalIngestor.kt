package app.logdate.client.sync

import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import kotlin.uuid.Uuid

/**
 * Applies journals and journal links that arrive from the watch through the same repository calls
 * the phone's own edits use, so each one is queued for cloud backup.
 *
 * Every method is safe to repeat: the Data Layer can deliver an item more than once, and a repeat
 * finds the change already made and queues nothing. A journal copy no newer than the phone's own is
 * ignored, so a late delivery cannot undo an edit made on the phone. A newer copy is saved with the
 * phone's own `syncVersion`, because the upload sends that version as an exact check and the
 * server refuses any other.
 */
class WatchJournalIngestor(
    private val journalRepository: JournalRepository,
    private val contentRepository: JournalContentRepository,
    private val journalContentDao: JournalContentDao,
) {
    suspend fun onJournal(journal: Journal) {
        val existing = journalRepository.getJournalById(journal.id)
        if (existing == null) {
            journalRepository.create(journal)
            return
        }
        if (journal.lastUpdated <= existing.lastUpdated) return
        journalRepository.update(journal.copy(syncVersion = existing.syncVersion))
    }

    suspend fun onJournalDeleted(journalId: Uuid) {
        if (journalRepository.getJournalById(journalId) == null) return
        journalRepository.delete(journalId)
    }

    suspend fun onAssociation(
        journalId: Uuid,
        contentId: Uuid,
    ) {
        if (journalContentDao.isContentInJournal(journalId, contentId)) return
        contentRepository.addContentToJournal(contentId, journalId)
    }

    suspend fun onAssociationDeleted(
        journalId: Uuid,
        contentId: Uuid,
    ) {
        if (!journalContentDao.isContentInJournal(journalId, contentId)) return
        contentRepository.removeContentFromJournal(contentId, journalId)
    }
}
