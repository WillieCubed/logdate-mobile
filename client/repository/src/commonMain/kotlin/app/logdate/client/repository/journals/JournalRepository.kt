package app.logdate.client.repository.journals

import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface JournalRepository {
    val allJournalsObserved: Flow<List<Journal>>

    fun observeJournalById(id: Uuid): Flow<Journal>

    /**
     * Gets a journal by its ID.
     * 
     * @param id The ID of the journal to get
     * @return The journal with the given ID, or null if not found
     */
    suspend fun getJournalById(id: Uuid): Journal?

    /**
     * Creates a new journal.
     *
     * @return The ID of the created journal.
     */
    suspend fun create(journal: Journal): Uuid
    
    /**
     * Updates an existing journal.
     * 
     * @param journal The updated journal data
     */
    suspend fun update(journal: Journal)

    /**
     * Deletes a journal by ID.
     * 
     * @param journalId The ID of the journal to delete
     */
    suspend fun delete(journalId: Uuid)
    
    /**
     * Saves a draft entry
     */
    suspend fun saveDraft(draft: EditorDraft)
    
    /**
     * Gets the most recent draft
     */
    suspend fun getLatestDraft(): EditorDraft?
    
    /**
     * Gets all drafts
     */
    suspend fun getAllDrafts(): List<EditorDraft>
    
    /**
     * Gets a draft by ID
     */
    suspend fun getDraft(id: Uuid): EditorDraft?
    
    /**
     * Deletes a draft
     */
    suspend fun deleteDraft(id: Uuid)
}
