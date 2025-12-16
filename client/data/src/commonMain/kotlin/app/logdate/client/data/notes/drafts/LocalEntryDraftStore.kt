package app.logdate.client.data.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import kotlin.uuid.Uuid

/**
 * Platform-agnostic interface for storing entry drafts locally on the device.
 * Each platform must provide its own implementation of this interface.
 */
interface LocalEntryDraftStore {
    /**
     * Saves a draft to local storage.
     */
    suspend fun saveDraft(draft: EntryDraft)
    
    /**
     * Gets a draft from local storage by its ID.
     * @return The draft if found, null otherwise.
     */
    suspend fun getDraft(id: Uuid): EntryDraft?
    
    /**
     * Gets all drafts from local storage.
     */
    suspend fun getAllDrafts(): List<EntryDraft>
    
    /**
     * Deletes a draft by its ID.
     * @return True if the draft was deleted, false if not found.
     */
    suspend fun deleteDraft(id: Uuid): Boolean
    
    /**
     * Deletes all drafts from local storage.
     */
    suspend fun clearAllDrafts()
}
