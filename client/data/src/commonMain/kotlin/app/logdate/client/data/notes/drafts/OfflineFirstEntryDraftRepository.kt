package app.logdate.client.data.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

/**
 * Local-first implementation of the EntryDraftRepository.
 * 
 * This implementation stores drafts in-memory first, with persistent
 * local storage provided by the LocalEntryDraftStore.
 */
class OfflineFirstEntryDraftRepository(
    private val draftStore: LocalEntryDraftStore,
    coroutineScope: CoroutineScope
) : EntryDraftRepository {
    
    // StateFlow to store and emit drafts
    private val draftsFlow = MutableStateFlow<Map<Uuid, EntryDraft>>(emptyMap())
    
    init {
        // Load existing drafts from storage on initialization
        coroutineScope.launch {
            val storedDrafts = draftStore.getAllDrafts()
            val draftsMap = storedDrafts.associateBy { it.id }
            draftsFlow.value = draftsMap
        }
    }
    
    override fun getDrafts(): Flow<List<EntryDraft>> {
        return draftsFlow.map { it.values.toList() }
    }
    
    override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> {
        return draftsFlow.map { drafts ->
            drafts[uid]?.let { Result.success(it) } 
                ?: Result.failure(NoSuchElementException("Draft with ID $uid not found"))
        }
    }
    
    override suspend fun createDraft(notes: List<JournalNote>): Uuid {
        val now = Clock.System.now()
        val id = Uuid.random()
        val draft = EntryDraft(
            id = id,
            notes = notes,
            createdAt = now,
            updatedAt = now
        )
        
        // Update StateFlow with new draft
        val currentDrafts = draftsFlow.value.toMutableMap()
        currentDrafts[id] = draft
        draftsFlow.value = currentDrafts
        
        // Persist to storage
        draftStore.saveDraft(draft)
        
        return id
    }
    
    override suspend fun updateDraft(uid: Uuid, notes: List<JournalNote>): Uuid {
        val currentDrafts = draftsFlow.value
        val existingDraft = currentDrafts[uid] ?: throw IllegalArgumentException("Draft with ID $uid not found")
        
        val updatedDraft = existingDraft.copy(
            notes = notes,
            updatedAt = Clock.System.now()
        )
        
        // Update StateFlow with modified draft
        val updatedDrafts = currentDrafts.toMutableMap()
        updatedDrafts[uid] = updatedDraft
        draftsFlow.value = updatedDrafts
        
        // Persist to storage
        draftStore.saveDraft(updatedDraft)
        
        return uid
    }
    
    override suspend fun deleteDraft(uid: Uuid) {
        // Update StateFlow by removing the draft
        val updatedDrafts = draftsFlow.value.toMutableMap()
        updatedDrafts.remove(uid)
        draftsFlow.value = updatedDrafts
        
        // Remove from storage
        draftStore.deleteDraft(uid)
    }
}
