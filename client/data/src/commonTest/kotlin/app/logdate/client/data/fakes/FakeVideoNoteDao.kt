package app.logdate.client.data.fakes

import app.logdate.client.database.dao.VideoNoteDao
import app.logdate.client.database.entities.VideoNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * Fake implementation of [VideoNoteDao] for testing.
 */
class FakeVideoNoteDao : VideoNoteDao {
    private val notes = mutableMapOf<Uuid, VideoNoteEntity>()
    private val notesFlow = MutableStateFlow<List<VideoNoteEntity>>(emptyList())

    override fun getNote(uid: Uuid): Flow<VideoNoteEntity> {
        return notesFlow.map { notes ->
            notes.find { it.uid == uid } ?: throw NoSuchElementException("Video note with ID $uid not found")
        }
    }

    override suspend fun getNoteOneOff(uid: Uuid): VideoNoteEntity {
        return notes[uid] ?: throw NoSuchElementException("Video note with ID $uid not found")
    }

    override fun getAllNotes(): Flow<List<VideoNoteEntity>> {
        return notesFlow
    }

    override suspend fun getAll(): List<VideoNoteEntity> {
        return notes.values.toList()
    }

    override fun getRecentNotes(limit: Int): Flow<List<VideoNoteEntity>> {
        return notesFlow.map { notes ->
            notes.sortedByDescending { it.created }.take(limit)
        }
    }

    override fun getNotesInRange(startTimestamp: Long, endTimestamp: Long): Flow<List<VideoNoteEntity>> {
        return notesFlow.map { notes ->
            notes.filter {
                val createdMillis = it.created.toEpochMilliseconds()
                createdMillis in startTimestamp..endTimestamp
            }.sortedByDescending { it.created }
        }
    }

    override suspend fun addNote(note: VideoNoteEntity) {
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

    override suspend fun updateSyncMetadata(noteId: Uuid, syncVersion: Long, lastSynced: kotlinx.datetime.Instant) {
        val existing = notes[noteId] ?: return
        notes[noteId] = existing.copy(syncVersion = syncVersion, lastSynced = lastSynced)
        updateFlow()
    }

    fun clear() {
        notes.clear()
        updateFlow()
    }

    private fun updateFlow() {
        notesFlow.value = notes.values.toList()
    }
}
