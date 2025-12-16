package app.logdate.client.data.fakes

import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * Fake implementation of [JournalRepository] for testing.
 */
class FakeJournalRepository : JournalRepository {
    private val journals = mutableMapOf<Uuid, Journal>()
    private val drafts = mutableMapOf<Uuid, EditorDraft>()
    private val journalsFlow = MutableStateFlow<List<Journal>>(emptyList())
    
    override val allJournalsObserved: Flow<List<Journal>>
        get() = journalsFlow
    
    override fun observeJournalById(id: Uuid): Flow<Journal> {
        return journalsFlow.map { journals ->
            journals.find { it.id == id } ?: throw NoSuchElementException("Journal with ID $id not found")
        }
    }
    
    override suspend fun getJournalById(id: Uuid): Journal? {
        return journals[id]
    }
    
    override suspend fun create(journal: Journal): Uuid {
        journals[journal.id] = journal
        updateJournalsFlow()
        return journal.id
    }
    
    override suspend fun update(journal: Journal) {
        journals[journal.id] = journal
        updateJournalsFlow()
    }
    
    override suspend fun delete(journalId: Uuid) {
        journals.remove(journalId)
        updateJournalsFlow()
    }
    
    override suspend fun saveDraft(draft: EditorDraft) {
        drafts[draft.id] = draft
    }
    
    override suspend fun getLatestDraft(): EditorDraft? {
        return drafts.values.maxByOrNull { it.lastModifiedAt }
    }
    
    override suspend fun getAllDrafts(): List<EditorDraft> {
        return drafts.values.toList()
    }
    
    override suspend fun getDraft(id: Uuid): EditorDraft? {
        return drafts[id]
    }
    
    override suspend fun deleteDraft(id: Uuid) {
        drafts.remove(id)
    }
    
    /**
     * Adds a journal directly to the repository for testing purposes.
     */
    fun addJournal(journal: Journal) {
        journals[journal.id] = journal
        updateJournalsFlow()
    }
    
    /**
     * Adds a draft directly to the repository for testing purposes.
     */
    fun addDraft(draft: EditorDraft) {
        drafts[draft.id] = draft
    }
    
    /**
     * Clears all journals and drafts from the repository.
     */
    fun clear() {
        journals.clear()
        drafts.clear()
        updateJournalsFlow()
    }
    
    private fun updateJournalsFlow() {
        journalsFlow.value = journals.values.toList()
    }
}