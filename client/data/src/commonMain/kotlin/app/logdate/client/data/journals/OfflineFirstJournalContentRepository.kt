package app.logdate.client.data.journals

import app.logdate.client.database.dao.JournalDao
import app.logdate.client.database.dao.JournalNotesDao
import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/**
 * Repository implementation for managing associations between content and journals.
 * Uses local database as source of truth.
 */
class OfflineFirstJournalContentRepository(
    private val journalContentDao: JournalContentDao,
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JournalContentRepository {

    override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> {
        // Use the JournalContentDao to get content IDs associated with this journal
        return journalContentDao.getContentForJournal(journalId)
            .combine(journalNotesRepository.allNotesObserved) { contentIds, allNotes ->
                // Filter notes to only those associated with this journal
                allNotes.filter { note -> 
                    contentIds.contains(note.uid) 
                }
            }
    }

    override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> {
        // Use the dedicated JournalContentDao to get journal IDs
        return journalContentDao.getJournalsForContent(contentId)
            .combine(journalRepository.allJournalsObserved) { journalIds, allJournals ->
                // Get all journals that match the journal IDs
                allJournals.filter { journal -> 
                    journalIds.contains(journal.id) 
                }
            }
    }

    override suspend fun addContentToJournal(contentId: Uuid, journalId: Uuid) = withContext(dispatcher) {
        // Create a link in the journal content table using string IDs
        val link = JournalContentEntityLink(journalId, contentId)
        journalContentDao.addContentToJournal(link)
    }

    override suspend fun removeContentFromJournal(contentId: Uuid, journalId: Uuid) = withContext(dispatcher) {
        // Remove from the journal content links table using string IDs
        journalContentDao.removeContentFromJournal(journalId, contentId)
    }

    override suspend fun addContentToJournals(contentId: Uuid, journalIds: List<Uuid>) = withContext(dispatcher) {
        journalIds.forEach { journalId ->
            addContentToJournal(contentId, journalId)
        }
    }

    override suspend fun removeContentFromAllJournals(contentId: Uuid) = withContext(dispatcher) {
        // Remove from the journal content links table using Uuid
        journalContentDao.removeContentFromAllJournals(contentId)
    }
}