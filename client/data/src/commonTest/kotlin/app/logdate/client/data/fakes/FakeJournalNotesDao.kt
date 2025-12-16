package app.logdate.client.data.fakes

import app.logdate.client.database.dao.JournalNotesDao
import app.logdate.client.database.entities.JournalEntity
import app.logdate.client.database.entities.JournalNoteCrossRef
import app.logdate.client.database.entities.JournalWithNotes
import app.logdate.client.database.entities.NoteJournals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * Fake implementation of [JournalNotesDao] for testing.
 */
class FakeJournalNotesDao : JournalNotesDao {
    private val journalNotes = mutableListOf<JournalNoteCrossRef>()
    private val journals = mutableMapOf<Uuid, JournalEntity>()
    
    private val journalsFlow = MutableStateFlow<List<JournalEntity>>(emptyList())
    private val journalNotesFlow = MutableStateFlow<List<JournalNoteCrossRef>>(emptyList())
    
    override suspend fun getAll(): List<JournalWithNotes> {
        // Since this is just a fake for testing, we'll return an empty list
        // Implementing a proper JournalWithNotes with text and image notes would be complex
        return emptyList()
    }
    
    override fun observeAll(): Flow<List<JournalWithNotes>> {
        // Since this is just a fake for testing, we'll return an empty flow
        return journalsFlow.map { emptyList() }
    }
    
    override fun getNotesForJournal(journalId: Uuid): Flow<List<NoteJournals>> {
        return journalNotesFlow.map { refs ->
            refs.filter { it.journalId == journalId }
                .map { ref ->
                    val journal = journals[ref.journalId] ?: return@map null
                    NoteJournals(
                        noteId = ref.noteId,
                        journal = journal
                    )
                }
                .filterNotNull()
        }
    }
    
    override suspend fun journalsForNoteSync(noteId: Uuid): List<JournalEntity> {
        val journalIds = journalNotes
            .filter { it.noteId == noteId }
            .map { it.journalId }
        
        return journals.values.filter { it.id in journalIds }
    }
    
    override fun observeJournalsForNote(noteId: Uuid): Flow<List<JournalEntity>> {
        return journalNotesFlow.map { refs ->
            val journalIds = refs
                .filter { it.noteId == noteId }
                .map { it.journalId }
            
            journals.values.filter { it.id in journalIds }
        }
    }
    
    override suspend fun addNoteToJournal(journalId: Uuid, noteId: Uuid) {
        val crossRef = JournalNoteCrossRef(journalId, noteId)
        journalNotes.add(crossRef)
        updateJournalNotesFlow()
    }
    
    override suspend fun removeNoteFromJournal(journalId: Uuid, noteId: Uuid) {
        journalNotes.removeIf { it.journalId == journalId && it.noteId == noteId }
        updateJournalNotesFlow()
    }
    
    override suspend fun deleteNoteFromAllJournals(noteId: Uuid) {
        journalNotes.removeIf { it.noteId == noteId }
        updateJournalNotesFlow()
    }
    
    /**
     * Adds a journal to the fake database for testing purposes.
     */
    fun addJournal(journal: JournalEntity) {
        journals[journal.id] = journal
        updateJournalsFlow()
    }
    
    /**
     * Clears all journal-note relationships and journals from the fake database.
     */
    fun clear() {
        journalNotes.clear()
        journals.clear()
        updateJournalNotesFlow()
        updateJournalsFlow()
    }
    
    private fun updateJournalNotesFlow() {
        journalNotesFlow.value = journalNotes.toList()
    }
    
    private fun updateJournalsFlow() {
        journalsFlow.value = journals.values.toList()
    }
}