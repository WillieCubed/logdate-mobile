package app.logdate.client.repository.journals

import app.logdate.shared.model.EditorDraft
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Repository for managing entry drafts.
 * 
 * Drafts are temporary saves of journal entries that haven't been
 * finalized and added to journals yet.
 */
interface DraftRepository {
    /**
     * Save a draft entry
     */
    suspend fun saveDraft(draft: EditorDraft)
    
    /**
     * Get the most recent draft
     */
    suspend fun getLatestDraft(): EditorDraft?
    
    /**
     * Get all drafts
     */
    suspend fun getAllDrafts(): List<EditorDraft>
    
    /**
     * Observe all drafts
     */
    val allDrafts: Flow<List<EditorDraft>>
    
    /**
     * Get a draft by ID
     */
    suspend fun getDraft(id: Uuid): EditorDraft?
    
    /**
     * Delete a draft
     */
    suspend fun deleteDraft(id: Uuid)
    
    /**
     * Delete all drafts
     */
    suspend fun clearAllDrafts()
}