package app.logdate.client.data.fakes

import app.logdate.client.data.notes.drafts.LocalEntryDraftStore
import app.logdate.client.repository.journals.EntryDraft
import kotlin.uuid.Uuid

/**
 * Fake implementation of [LocalEntryDraftStore] for testing.
 */
class FakeLocalEntryDraftStore : LocalEntryDraftStore {
    private val drafts = mutableMapOf<Uuid, EntryDraft>()
    
    override suspend fun saveDraft(draft: EntryDraft) {
        drafts[draft.id] = draft
    }
    
    override suspend fun getDraft(id: Uuid): EntryDraft? {
        return drafts[id]
    }
    
    override suspend fun getAllDrafts(): List<EntryDraft> {
        return drafts.values.toList()
    }
    
    override suspend fun deleteDraft(id: Uuid): Boolean {
        return drafts.remove(id) != null
    }
    
    override suspend fun clearAllDrafts() {
        drafts.clear()
    }
    
    /**
     * Clears all drafts from the store. This method is specific to the fake implementation.
     */
    fun clear() {
        drafts.clear()
    }
}