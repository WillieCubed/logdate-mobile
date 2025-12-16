package app.logdate.client.repository.journals

import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Repository interface for managing associations between content and journals.
 * This allows notes to be associated with multiple journals.
 */
interface JournalContentRepository {
    /**
     * Observes all content associated with a journal.
     */
    fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>>
    
    /**
     * Observes all journals associated with a piece of content.
     */
    fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>>
    
    /**
     * Associates content with a journal.
     */
    suspend fun addContentToJournal(contentId: Uuid, journalId: Uuid)
    
    /**
     * Removes content from a journal.
     */
    suspend fun removeContentFromJournal(contentId: Uuid, journalId: Uuid)
    
    /**
     * Associates content with multiple journals.
     */
    suspend fun addContentToJournals(contentId: Uuid, journalIds: List<Uuid>)
    
    /**
     * Removes content from all journals.
     */
    suspend fun removeContentFromAllJournals(contentId: Uuid)
}