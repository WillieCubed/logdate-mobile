package app.logdate.client.data.fakes

import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.entities.VoiceNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * Fake implementation of [AudioNoteDao] for testing.
 */
class FakeAudioNoteDao : AudioNoteDao {
    private val notes = mutableMapOf<Uuid, VoiceNoteEntity>()
    private val notesFlow = MutableStateFlow<List<VoiceNoteEntity>>(emptyList())
    
    override fun getNote(uid: Uuid): Flow<VoiceNoteEntity> {
        return notesFlow.map { notes ->
            notes.find { it.uid == uid } ?: throw NoSuchElementException("Audio note with ID $uid not found")
        }
    }
    
    override suspend fun getNoteOneOff(uid: Uuid): VoiceNoteEntity {
        return notes[uid] ?: throw NoSuchElementException("Audio note with ID $uid not found")
    }
    
    override fun getAllNotes(): Flow<List<VoiceNoteEntity>> {
        return notesFlow
    }
    
    override suspend fun getAll(): List<VoiceNoteEntity> {
        return notes.values.toList()
    }
    
    override fun getRecentNotes(limit: Int): Flow<List<VoiceNoteEntity>> {
        return notesFlow.map { notes ->
            notes.sortedByDescending { it.created }.take(limit)
        }
    }
    
    override fun getNotesPage(limit: Int, offset: Int): Flow<List<VoiceNoteEntity>> {
        return notesFlow.map { notes ->
            notes.sortedByDescending { it.created }.drop(offset).take(limit)
        }
    }
    
    override fun getNotesInRange(startTimestamp: Long, endTimestamp: Long): Flow<List<VoiceNoteEntity>> {
        return notesFlow.map { notes ->
            notes.filter {
                val createdMillis = it.created.toEpochMilliseconds()
                createdMillis in startTimestamp..endTimestamp
            }.sortedByDescending { it.created }
        }
    }
    
    override suspend fun addNote(note: VoiceNoteEntity) {
        notes[note.uid] = note
        updateFlow()
    }
    
    override suspend fun removeNote(noteId: Uuid) {
        notes.remove(noteId)
        updateFlow()
    }
    
    override suspend fun removeNote(noteIds: List<Uuid>) {
        noteIds.forEach { notes.remove(it) }
        updateFlow()
    }
    
    /**
     * Clears all notes in the fake database.
     * This method is specific to the fake implementation for testing.
     */
    fun clear() {
        notes.clear()
        updateFlow()
    }
    
    private fun updateFlow() {
        notesFlow.value = notes.values.toList()
    }
}