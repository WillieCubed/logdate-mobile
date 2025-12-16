package app.logdate.client.data.fakes

import app.logdate.client.repository.journals.DraftRepository
import app.logdate.shared.model.EditorDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

/**
 * Fake implementation of [DraftRepository] for testing.
 */
class FakeDraftRepository : DraftRepository {
    private val drafts = mutableMapOf<Uuid, EditorDraft>()
    private val draftsFlow = MutableStateFlow<List<EditorDraft>>(emptyList())
    
    override suspend fun saveDraft(draft: EditorDraft) {
        drafts[draft.id] = draft
        updateFlow()
    }
    
    override suspend fun getLatestDraft(): EditorDraft? {
        return drafts.values.maxByOrNull { it.lastModifiedAt }
    }
    
    override suspend fun getAllDrafts(): List<EditorDraft> {
        return drafts.values.toList()
    }
    
    override val allDrafts: Flow<List<EditorDraft>>
        get() = draftsFlow
    
    override suspend fun getDraft(id: Uuid): EditorDraft? {
        return drafts[id]
    }
    
    override suspend fun deleteDraft(id: Uuid) {
        drafts.remove(id)
        updateFlow()
    }
    
    override suspend fun clearAllDrafts() {
        drafts.clear()
        updateFlow()
    }
    
    /**
     * Clears all drafts in the repository.
     * This method is specific to the fake implementation for testing.
     */
    fun clear() {
        drafts.clear()
        updateFlow()
    }
    
    private fun updateFlow() {
        draftsFlow.value = drafts.values.toList()
    }
}